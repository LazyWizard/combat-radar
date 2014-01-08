package org.lazywizard.radar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: Switch to pre-calculated rotations for ships
// TODO: Use a narrower triangle for ships to better show facing
// TODO: Use better names for config options in the settings file
// TODO: Change toggle to switch between 3 zoom levels + off
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    private static final String SETTINGS_FILE = "data/config/combat_radar.json";
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float RADAR_SIGHT_RANGE;
    private static float RADAR_SCALING;
    // Location and size of progress bar on screen
    private static final Vector2f PROGRESS_BAR_LOCATION;
    private static final float PROGRESS_BAR_WIDTH;
    private static final float PROGRESS_BAR_HEIGHT;
    // Radar OpenGL buffers/display lists
    private static int RADAR_BOX_DISPLAY_LIST_ID = -123;
    // Radar display settings
    private static boolean SHOW_SHIPS = true;
    private static boolean SHOW_ASTEROIDS = true;
    private static boolean SHOW_MISSILES = true;
    private static boolean SHOW_OBJECTIVES = true;
    private static boolean SHOW_SHIELDS = true;
    private static boolean SHOW_BATTLE_PROGRESS = true;
    // Radar color settings
    private static float RADAR_OPACITY, RADAR_ALPHA, CONTACT_ALPHA;
    private static float RADAR_FADE, RADAR_MIDFADE;
    private static Color RADAR_BG_COLOR;
    private static Color RADAR_FG_COLOR;
    private static Color RADAR_FG_DEAD_COLOR;
    private static Color FRIENDLY_COLOR;
    private static Color ENEMY_COLOR;
    private static Color HULK_COLOR;
    private static Color ASTEROID_COLOR;
    // Radar toggle button constant
    private static int RADAR_TOGGLE_KEY;
    // Whether the radar is active
    private static boolean radarEnabled = true;
    private boolean needsRecalc = true;
    private ShipAPI player;
    private boolean isHulk = false;
    private CombatEngineAPI engine;

    static
    {
        RADAR_RADIUS = Display.getHeight() / 10f;
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.2f),
                RADAR_RADIUS * 1.2f);

        PROGRESS_BAR_LOCATION = new Vector2f(
                RADAR_CENTER.x + (RADAR_RADIUS * 1.1f),
                RADAR_CENTER.y - (RADAR_RADIUS * 1.1f));
        PROGRESS_BAR_WIDTH = RADAR_RADIUS * .09f;
        PROGRESS_BAR_HEIGHT = RADAR_RADIUS * 2f;
    }

    static void reloadSettings() throws IOException, JSONException
    {
        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        // Toggle key
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Radar options
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        RADAR_ALPHA = (float) settings.getDouble("radarForegroundAlpha");
        RADAR_FADE = (float) settings.getDouble("radarEdgeFadeAmount");
        RADAR_MIDFADE = (RADAR_ALPHA + RADAR_FADE) / 2f;
        SHOW_SHIPS = settings.getBoolean("showShips");
        SHOW_ASTEROIDS = settings.getBoolean("showAsteroids");
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_OBJECTIVES = settings.getBoolean("showObjectives");
        SHOW_SHIELDS = settings.getBoolean("showShields");
        SHOW_BATTLE_PROGRESS = settings.getBoolean("showBattleProgress");

        // Radar range
        RADAR_SIGHT_RANGE = (float) settings.getDouble("radarRange");
        RADAR_SCALING = RADAR_RADIUS / RADAR_SIGHT_RANGE;
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar range set to " + RADAR_SIGHT_RANGE + " su");

        // Base radar color
        RADAR_BG_COLOR = toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_FG_COLOR = Global.getSettings().getColor("textFriendColor");
        RADAR_FG_DEAD_COLOR = Global.getSettings().getColor("textNeutralColor");

        // Radar contact colors
        final boolean vanillaColors = settings.getBoolean("useVanillaColors");
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = vanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = vanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : toColor(settings.getJSONArray("enemyColor"));
        HULK_COLOR = vanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : toColor(settings.getJSONArray("hulkColor"));
        ASTEROID_COLOR = vanillaColors ? Color.LIGHT_GRAY
                : toColor(settings.getJSONArray("asteroidColor"));
    }

    private static Color toColor(JSONArray array) throws JSONException
    {
        return new Color(array.getInt(0), array.getInt(1), array.getInt(2),
                (array.length() == 4 ? array.getInt(3) : 255));
    }

    private static void glColor(Color color, float alphaMult)
    {
        glColor4ub((byte) color.getRed(), (byte) color.getGreen(),
                (byte) color.getBlue(), (byte) (color.getAlpha() * alphaMult));
    }

    private List<CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts)
    {
        List<CombatEntityAPI> visible = new ArrayList<CombatEntityAPI>();
        for (CombatEntityAPI contact : contacts)
        {
            if (MathUtils.getDistanceSquared(contact.getLocation(), player.getLocation())
                    > (RADAR_SIGHT_RANGE * RADAR_SIGHT_RANGE))
            {
                continue;
            }

            visible.add(contact);
        }

        return visible;
    }

    private static final Vector2f TMP_VECTOR = new Vector2f();

    private Vector2f getPointOnRadar(Vector2f worldLoc)
    {
        // Get position relative to {0,0}
        Vector2f.sub(worldLoc, player.getLocation(), TMP_VECTOR);
        // Scale point to fit within the radar properly
        TMP_VECTOR.scale(RADAR_SCALING);
        // Translate point to inside the radar box
        Vector2f.add(TMP_VECTOR, RADAR_CENTER, TMP_VECTOR);
        return TMP_VECTOR;
    }

    private void renderBox()
    {
        // Cache OpenGL commands for faster execution
        if (!needsRecalc)
        {
            glCallList(RADAR_BOX_DISPLAY_LIST_ID);
        }
        else
        {
            // Delete old display list, if existant
            if (RADAR_BOX_DISPLAY_LIST_ID >= 0)
            {
                Global.getLogger(CombatRadarPlugin.class).log(Level.DEBUG,
                        "Deleting old list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
                glDeleteLists(RADAR_BOX_DISPLAY_LIST_ID, 1);
            }

            // Generate new display list
            RADAR_BOX_DISPLAY_LIST_ID = glGenLists(1);
            Global.getLogger(CombatRadarPlugin.class).log(Level.DEBUG,
                    "Creating new list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
            glNewList(RADAR_BOX_DISPLAY_LIST_ID, GL_COMPILE);
            glLineWidth(1f);

            // Slight darkening of radar background
            glColor(RADAR_BG_COLOR, RADAR_OPACITY);
            DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, true);

            Color color = (isHulk ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR);

            // Outer circle
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, false);

            // Middle circle
            glColor(color, RADAR_ALPHA * RADAR_MIDFADE);
            DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .66f, 54, false);

            // Inner circle
            glColor(color, RADAR_ALPHA);
            DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .33f, 36, false);

            glBegin(GL_LINES);
            // Left line
            glColor(color, RADAR_ALPHA);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x - RADAR_RADIUS, RADAR_CENTER.y);

            // Right line
            glColor(color, RADAR_ALPHA);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x + RADAR_RADIUS, RADAR_CENTER.y);

            // Upper line
            glColor(color, RADAR_ALPHA);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y + RADAR_RADIUS);

            // Lower line
            glColor(color, RADAR_ALPHA);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y - RADAR_RADIUS);
            glEnd();

            // Border lines
            glLineWidth(1.5f);
            glBegin(GL_LINE_STRIP);
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x + (RADAR_RADIUS * 1.1f),
                    RADAR_CENTER.y + (RADAR_RADIUS * 1.1f));
            glColor(color, RADAR_ALPHA);
            glVertex2f(RADAR_CENTER.x - (RADAR_RADIUS * 1.1f),
                    RADAR_CENTER.y + (RADAR_RADIUS * 1.1f));
            glColor(color, RADAR_ALPHA * RADAR_FADE);
            glVertex2f(RADAR_CENTER.x - (RADAR_RADIUS * 1.1f),
                    RADAR_CENTER.y - (RADAR_RADIUS * 1.1f));
            glEnd();
            glEndList();
            needsRecalc = false;
        }
    }

    private void drawContact(Vector2f center, float size, float angle)
    {
        // Convert angles into radians for our calculations
        angle = (float) Math.toRadians(angle);

        // Precalculate the sine and cosine
        // Instead of recalculating sin/cos for each line segment,
        // this algorithm rotates the line around the center point
        float theta = 2f * 3.1415926f / 3f;
        float cos = (float) FastTrig.cos(theta);
        float sin = (float) FastTrig.sin(theta);
        float t;

        // Start at angle startAngle
        float x = (float) (size * FastTrig.cos(angle));
        float y = (float) (size * FastTrig.sin(angle));

        for (int i = 0; i < 2; i++)
        {
            // Output vertex
            glVertex2f(x + center.x, y + center.y);

            // Apply the rotation matrix
            t = x;
            x = (cos * x) - (sin * y);
            y = (sin * t) + (cos * y);
        }
        glVertex2f(x + center.x, y + center.y);
    }

    private void renderContacts()
    {
        if (SHOW_SHIPS)
        {
            List<CombatEntityAPI> contacts = filterVisible(engine.getShips());
            if (!contacts.isEmpty())
            {
                // Draw contacts
                ShipAPI contact, target = null;
                Vector2f radarLoc;
                float alphaMod;
                glLineWidth(1f);
                glBegin(GL_TRIANGLES);
                for (CombatEntityAPI entity : contacts)
                {
                    contact = (ShipAPI) entity;
                    alphaMod = (contact.getPhaseCloak() != null
                            && contact.getPhaseCloak().isOn()) ? .5f : 1f;

                    // Check for current ship target
                    if (player.getShipTarget() == contact)
                    {
                        target = contact;
                    }

                    // Allies
                    if (contact.getOwner() == player.getOwner())
                    {
                        glColor(FRIENDLY_COLOR, CONTACT_ALPHA * alphaMod);
                    }
                    // Enemies
                    else if (contact.getOwner() + player.getOwner() == 1)
                    {
                        glColor(ENEMY_COLOR, CONTACT_ALPHA * alphaMod);
                    }
                    // Hulks
                    else
                    {
                        glColor(HULK_COLOR, CONTACT_ALPHA);
                    }

                    radarLoc = getPointOnRadar(contact.getLocation());
                    drawContact(radarLoc,
                            1.5f * (contact.getHullSize().ordinal() + 1),
                            contact.getFacing());
                }
                glEnd();

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    ShieldAPI shield;
                    for (CombatEntityAPI entity : contacts)
                    {
                        contact = (ShipAPI) entity;
                        shield = contact.getShield();
                        if (shield != null && shield.isOn())
                        {
                            glColor4f(0f, 1f, 1f, CONTACT_ALPHA);
                            radarLoc = getPointOnRadar(contact.getLocation());
                            DrawUtils.drawArc(radarLoc.x, radarLoc.y,
                                    1.75f * (contact.getHullSize().ordinal() + 1),
                                    shield.getFacing() - (shield.getActiveArc() / 2f),
                                    shield.getActiveArc(),
                                    (int) (shield.getActiveArc() / 18f) + 1);
                        }
                    }
                }

                // Draw marker around current ship target
                if (target != null)
                {
                    // TODO: Add a color setting for this
                    float size = 1.8f * (target.getHullSize().ordinal() + 1);
                    radarLoc = getPointOnRadar(target.getLocation());
                    float margin = size * .5f;
                    glColor4f(1f, 1f, 1f, CONTACT_ALPHA);
                    glBegin(GL_LINES);
                    // Upper left corner
                    glVertex2f(radarLoc.x - size, radarLoc.y + size);
                    glVertex2f(radarLoc.x - margin, radarLoc.y + size);
                    glVertex2f(radarLoc.x - size, radarLoc.y + size);
                    glVertex2f(radarLoc.x - size, radarLoc.y + margin);

                    // Upper right corner
                    glVertex2f(radarLoc.x + size, radarLoc.y + size);
                    glVertex2f(radarLoc.x + margin, radarLoc.y + size);
                    glVertex2f(radarLoc.x + size, radarLoc.y + size);
                    glVertex2f(radarLoc.x + size, radarLoc.y + margin);

                    // Lower left corner
                    glVertex2f(radarLoc.x - size, radarLoc.y - size);
                    glVertex2f(radarLoc.x - margin, radarLoc.y - size);
                    glVertex2f(radarLoc.x - size, radarLoc.y - size);
                    glVertex2f(radarLoc.x - size, radarLoc.y - margin);

                    // Lower right corner
                    glVertex2f(radarLoc.x + size, radarLoc.y - size);
                    glVertex2f(radarLoc.x + margin, radarLoc.y - size);
                    glVertex2f(radarLoc.x + size, radarLoc.y - size);
                    glVertex2f(radarLoc.x + size, radarLoc.y - margin);
                    glEnd();
                }
            }
        }
    }

    private void renderAsteroids()
    {
        if (SHOW_ASTEROIDS)
        {
            List<CombatEntityAPI> asteroids = filterVisible(engine.getAsteroids());
            if (!asteroids.isEmpty())
            {
                Vector2f radarLoc;
                glColor(ASTEROID_COLOR, CONTACT_ALPHA);
                glPointSize(2f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI asteroid : asteroids)
                {
                    radarLoc = getPointOnRadar(asteroid.getLocation());
                    glVertex2f(radarLoc.x, radarLoc.y);
                }
                glEnd();
            }
        }
    }

    private void renderMissiles()
    {
        if (SHOW_MISSILES)
        {
            List<CombatEntityAPI> missiles = filterVisible(engine.getMissiles());
            if (!missiles.isEmpty())
            {
                Vector2f radarLoc;
                MissileAPI missile;
                float alphaMod;
                glPointSize(2f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI entity : missiles)
                {
                    missile = (MissileAPI) entity;
                    // TODO: Add a setting for missile damage alpha mod
                    alphaMod = Math.min(1f, Math.max(0.3f,
                            missile.getDamageAmount() / 750f));
                    alphaMod *= (missile.isFading() ? .5f : 1f);

                    // Enemy or burnt-out missiles
                    if (missile.isFizzling() || (missile.getOwner() + player.getOwner() == 1))
                    {
                        glColor(ENEMY_COLOR, CONTACT_ALPHA * alphaMod);
                    }
                    // Allied missiles
                    else
                    {
                        glColor(FRIENDLY_COLOR, CONTACT_ALPHA * alphaMod);
                    }

                    radarLoc = getPointOnRadar(missile.getLocation());
                    glVertex2f(radarLoc.x, radarLoc.y);
                }
                glEnd();
            }
        }
    }

    private void renderObjectives()
    {
        if (SHOW_OBJECTIVES)
        {
            List<CombatEntityAPI> objectives = filterVisible(engine.getObjectives());
            if (!objectives.isEmpty())
            {
                // TODO: Add customizable colors to settings
                Vector2f radarLoc;
                float size = 250f * RADAR_SCALING;
                glLineWidth(size / 5f);
                for (CombatEntityAPI objective : objectives)
                {
                    // Owned by player
                    if (objective.getOwner() == player.getOwner())
                    {
                        glColor(FRIENDLY_COLOR, CONTACT_ALPHA);
                    }
                    // Owned by opposition
                    else if (objective.getOwner() + player.getOwner() == 1)
                    {
                        glColor(ENEMY_COLOR, CONTACT_ALPHA);
                    }
                    // Not owned yet
                    else
                    {
                        glColor(HULK_COLOR, CONTACT_ALPHA);
                    }

                    radarLoc = getPointOnRadar(objective.getLocation());

                    glBegin(GL_LINE_LOOP);
                    glVertex2f(radarLoc.x, radarLoc.y + size);
                    glVertex2f(radarLoc.x + size, radarLoc.y);
                    glVertex2f(radarLoc.x, radarLoc.y - size);
                    glVertex2f(radarLoc.x - size, radarLoc.y);
                    glEnd();
                }
            }
        }
    }

    private void renderBattleProgress()
    {
        if (SHOW_BATTLE_PROGRESS)
        {
            int fpPlayer = 0, fpEnemy = 0;

            // Total up player fleet point value
            CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
            List<FleetMemberAPI> ships = fm.getDeployedCopy();
            ships.addAll(fm.getReservesCopy());
            for (FleetMemberAPI ship : ships)
            {
                fpPlayer += ship.getFleetPointCost();
            }

            // Total up enemy fleet point value
            fm = engine.getFleetManager(FleetSide.ENEMY);
            ships = fm.getDeployedCopy();
            ships.addAll(fm.getReservesCopy());
            for (FleetMemberAPI ship : ships)
            {
                fpEnemy += ship.getFleetPointCost();
            }

            if (fpPlayer + fpEnemy <= 0)
            {
                return;
            }

            float relativeStrength = fpPlayer / (float) (fpPlayer + fpEnemy);

            glBegin(GL_QUADS);
            // Player strength
            glColor(FRIENDLY_COLOR, RADAR_ALPHA);
            glVertex2f(PROGRESS_BAR_LOCATION.x, PROGRESS_BAR_LOCATION.y);
            glVertex2f(PROGRESS_BAR_LOCATION.x + PROGRESS_BAR_WIDTH,
                    PROGRESS_BAR_LOCATION.y);
            glVertex2f(PROGRESS_BAR_LOCATION.x + PROGRESS_BAR_WIDTH,
                    PROGRESS_BAR_LOCATION.y + (PROGRESS_BAR_HEIGHT * relativeStrength));
            glVertex2f(PROGRESS_BAR_LOCATION.x, PROGRESS_BAR_LOCATION.y
                    + (PROGRESS_BAR_HEIGHT * relativeStrength));

            // Enemy strength
            glColor(ENEMY_COLOR, RADAR_ALPHA);
            glVertex2f(PROGRESS_BAR_LOCATION.x, PROGRESS_BAR_LOCATION.y
                    + (PROGRESS_BAR_HEIGHT * relativeStrength));
            glVertex2f(PROGRESS_BAR_LOCATION.x + PROGRESS_BAR_WIDTH,
                    PROGRESS_BAR_LOCATION.y + (PROGRESS_BAR_HEIGHT * relativeStrength));
            glVertex2f(PROGRESS_BAR_LOCATION.x + PROGRESS_BAR_WIDTH,
                    PROGRESS_BAR_LOCATION.y + PROGRESS_BAR_HEIGHT);
            glVertex2f(PROGRESS_BAR_LOCATION.x, PROGRESS_BAR_LOCATION.y
                    + PROGRESS_BAR_HEIGHT);
            glEnd();
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
        // This also acts as a main menu check
        player = engine.getPlayerShip();
        if (player == null || !engine.isEntityInPlay(player))
        {
            return;
        }

        // Radar toggle
        for (InputEventAPI event : events)
        {
            if (event.isConsumed())
            {
                continue;
            }

            if (event.isKeyDownEvent() && event.getEventValue() == RADAR_TOGGLE_KEY)
            {
                radarEnabled = !radarEnabled;
                event.consume();
                break;
            }
        }

        if (!radarEnabled)
        {
            return;
        }

        // Set OpenGL flags
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glOrtho(0, Display.getWidth(), 0, Display.getHeight(), 1, -1);

        // Draw the radar
        renderBox();
        renderBattleProgress();

        // Check if player alive/dead status has changed
        if (player.isHulk() ^ isHulk)
        {
            needsRecalc = true;
            isHulk = player.isHulk();
        }

        if (!isHulk)
        {
            renderContacts();
            renderAsteroids();
            renderMissiles();
            renderObjectives();
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}

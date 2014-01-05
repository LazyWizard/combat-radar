package org.lazywizard.radar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
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

// TODO: Draw ships with different number of sides for each hull class
// TODO: Add marker around current target
// TODO: Switch to pre-calculated rotations for ships
// TODO: Display objectives
// TODO: Use better names for config options in the settings file
// TODO: Change toggle to switch between 3 zoom levels + off
// TODO: Move each render type to its own file, use extensible plugin system
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    private static final String SETTINGS_FILE = "data/config/combat_radar.json";
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float RADAR_SIGHT_RANGE;
    private static float RADAR_SCALING;
    // Radar display settings
    private static boolean SHOW_SHIPS = true;
    private static boolean SHOW_ASTEROIDS = true;
    private static boolean SHOW_MISSILES = true;
    private static boolean SHOW_OBJECTIVES = true;
    private static boolean SHOW_SHIELDS = true;
    // Radar color settings
    private static float RADAR_OPACITY, RADAR_ALPHA, CONTACT_ALPHA;
    private static float RADAR_FADE, RADAR_MIDFADE;
    private static GLColor RADAR_BG_COLOR;
    private static GLColor RADAR_FG_COLOR;
    private static GLColor RADAR_FG_DEAD_COLOR;
    private static GLColor FRIENDLY_COLOR;
    private static GLColor ENEMY_COLOR;
    private static GLColor HULK_COLOR;
    private static GLColor ASTEROID_COLOR;
    // Radar toggle button constant
    private static int RADAR_TOGGLE_KEY;
    // Whether the radar is active
    private static boolean enabled = true;
    private final Vector2f tmp = new Vector2f();
    private ShipAPI player;
    private CombatEngineAPI engine;

    static
    {
        RADAR_RADIUS = Display.getHeight() / 10f;
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.2f),
                RADAR_RADIUS * 1.2f);
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

        // Radar range
        RADAR_SIGHT_RANGE = (float) settings.getDouble("radarRange");
        RADAR_SCALING = RADAR_RADIUS / RADAR_SIGHT_RANGE;
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar range set to " + RADAR_SIGHT_RANGE + " su");

        // Base radar color
        Color tmp = toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_BG_COLOR = new GLColor(tmp);
        tmp = Global.getSettings().getColor("textFriendColor");
        RADAR_FG_COLOR = new GLColor(tmp);
        tmp = Global.getSettings().getColor("textNeutralColor");
        RADAR_FG_DEAD_COLOR = new GLColor(tmp);

        // Radar contact colors
        final boolean vanillaColors = settings.getBoolean("useVanillaColors");
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = new GLColor(vanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : toColor(settings.getJSONArray("friendlyColor")));
        ENEMY_COLOR = new GLColor(vanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : toColor(settings.getJSONArray("enemyColor")));
        HULK_COLOR = new GLColor(vanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : toColor(settings.getJSONArray("hulkColor")));
        ASTEROID_COLOR = new GLColor(vanillaColors ? Color.LIGHT_GRAY
                : toColor(settings.getJSONArray("asteroidColor")));
    }

    private static Color toColor(JSONArray array) throws JSONException
    {
        return new Color(array.getInt(0), array.getInt(1), array.getInt(2),
                (array.length() == 4 ? array.getInt(3) : 255));
    }

    private static void glColor(GLColor color, float alphaMult)
    {
        glColor4f(color.red, color.green, color.blue, color.alpha * alphaMult);
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

    private Vector2f getPointOnRadar(Vector2f worldLoc)
    {
        Vector2f.sub(worldLoc, player.getLocation(), tmp);
        tmp.scale(RADAR_SCALING);
        Vector2f.add(tmp, RADAR_CENTER, tmp);
        return tmp;
    }

    private void renderBox()
    {
        glLineWidth(1f);

        // Slight darkening of radar background
        glColor(RADAR_BG_COLOR, RADAR_OPACITY);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, true);

        // Outer circle
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, false);

        // Middle circle
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_MIDFADE);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .66f, 54, false);

        // Inner circle
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .33f, 36, false);

        glBegin(GL_LINES);
        // Left line
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x - RADAR_RADIUS, RADAR_CENTER.y);

        // Right line
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x + RADAR_RADIUS, RADAR_CENTER.y);

        // Upper line
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y + RADAR_RADIUS);

        // Lower line
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y);
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y - RADAR_RADIUS);
        glEnd();

        // Border lines
        glLineWidth(1.5f);
        glBegin(GL_LINE_STRIP);
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x + (RADAR_RADIUS * 1.1f),
                RADAR_CENTER.y + (RADAR_RADIUS * 1.1f));
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA);
        glVertex2f(RADAR_CENTER.x - (RADAR_RADIUS * 1.1f),
                RADAR_CENTER.y + (RADAR_RADIUS * 1.1f));
        glColor(player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR, RADAR_ALPHA * RADAR_FADE);
        glVertex2f(RADAR_CENTER.x - (RADAR_RADIUS * 1.1f),
                RADAR_CENTER.y - (RADAR_RADIUS * 1.1f));
        glEnd();
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
                ShipAPI contact;
                ShieldAPI shield;
                Vector2f radarLoc;
                boolean isPhased;
                glLineWidth(1f);
                glBegin(GL_TRIANGLES);
                for (CombatEntityAPI entity : filterVisible(engine.getShips()))
                {
                    contact = (ShipAPI) entity;
                    shield = contact.getShield();
                    isPhased = (contact.getPhaseCloak() != null
                            && contact.getPhaseCloak().isOn());

                    // Allies
                    if (contact.getOwner() == player.getOwner())
                    {
                        glColor(FRIENDLY_COLOR, CONTACT_ALPHA * (isPhased ? .5f : 1f));
                    }
                    // Enemies
                    else if (contact.getOwner() + player.getOwner() == 1)
                    {
                        glColor(ENEMY_COLOR, CONTACT_ALPHA * (isPhased ? .5f : 1f));
                    }
                    // Hulks
                    else
                    {
                        glColor(HULK_COLOR, CONTACT_ALPHA);
                    }

                    radarLoc = getPointOnRadar(contact.getLocation());
                    drawContact(radarLoc,
                            1.5f * (((ShipAPI) contact).getHullSize().ordinal() + 1),
                            contact.getFacing());

                    if (SHOW_SHIELDS && shield != null && shield.isOn())
                    {
                        glEnd();
                        glColor4f(0f, 1f, 1f, CONTACT_ALPHA);
                        DrawUtils.drawArc(radarLoc.x, radarLoc.y,
                                1.75f * (((ShipAPI) contact).getHullSize().ordinal() + 1),
                                shield.getFacing() - (shield.getActiveArc() / 2f),
                                shield.getActiveArc(),
                                (int) (shield.getActiveArc() / 18f) + 1);
                        glBegin(GL_TRIANGLES);
                    }
                }
                glEnd();
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
                glColor(ASTEROID_COLOR, CONTACT_ALPHA);
                glPointSize(2f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI asteroid : asteroids)
                {
                    getPointOnRadar(asteroid.getLocation());
                    glVertex2f(tmp.x, tmp.y);
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
                MissileAPI missile;
                float alphaMod;
                glPointSize(1.5f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI entity : missiles)
                {
                    missile = (MissileAPI) entity;
                    alphaMod = (missile.isFading() ? .5f : 1f);

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

                    getPointOnRadar(missile.getLocation());
                    glVertex2f(tmp.x, tmp.y);
                }
                glEnd();
            }
        }
    }

    private void renderObjectives()
    {
        if (SHOW_OBJECTIVES)
        {
            /*List<BattleObjectiveAPI> objectives = engine.getObjectives();
             if (!objectives.isEmpty())
             {
             }*/
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
                enabled = !enabled;
                event.consume();
                break;
            }
        }

        if (!enabled)
        {
            return;
        }

        // Set OpenGL flags
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glOrtho(0, Display.getWidth(), 0, Display.getHeight(), 1, -1);

        // Draw the radar
        renderBox();

        if (!player.isHulk())
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

    private static class GLColor
    {
        private final float red, green, blue, alpha;

        private GLColor(Color color)
        {
            red = color.getRed() / 255f;
            green = color.getGreen() / 255f;
            blue = color.getBlue() / 255f;
            alpha = color.getAlpha() / 255f;
        }

        private GLColor(Color color, float alphaOverride)
        {
            red = color.getRed() / 255f;
            green = color.getGreen() / 255f;
            blue = color.getBlue() / 255f;
            alpha = alphaOverride;
        }
    }
}

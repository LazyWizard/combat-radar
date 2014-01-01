package org.lazywizard.radar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: Add box and darkened edges
// TODO: Draw ships with different number of sides for each hull class
// TODO: Add marker around current target
// TODO: Lower alpha of phased ships
// TODO: Switch to pre-calculated rotations for ships
// TODO: Display asteroids
// TODO: Display missiles
// TODO: Display objectives
// TODO: Display shield arcs (?)
// TODO: Add settings file
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    private static final String SETTINGS_FILE = "data/config/combat_radar.json";
    private static final Comparator SORT_BY_SIZE = new SortEntitiesBySize();
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float RADAR_SIGHT_RANGE;
    private static float RADAR_SCALING;
    // Radar display settings
    private static boolean SHOW_SHIPS;
    private static boolean SHOW_ASTEROIDS;
    private static boolean SHOW_MISSILES;
    private static boolean SHOW_SHIELDS;
    private static boolean SHOW_OBJECTIVES;
    // Radar box color settings
    private static final float RADAR_BOX_ALPHA = .25f;
    private static float RADAR_R, RADAR_G, RADAR_B;
    // Radar contact color settings
    private static final float RADAR_CONTACT_ALPHA = .85f;
    private static float FRIENDLY_R, FRIENDLY_G, FRIENDLY_B;
    private static float ENEMY_R, ENEMY_G, ENEMY_B;
    private static float NEUTRAL_R, NEUTRAL_G, NEUTRAL_B;
    private static float ASTEROID_R, ASTEROID_G, ASTEROID_B;
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
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.5f),
                RADAR_RADIUS * 1.5f);
    }

    static void reloadSettings() throws IOException, JSONException
    {
        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        // Toggle key
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Radar range
        RADAR_SIGHT_RANGE = (float) settings.getDouble("radarRange");
        RADAR_SCALING = RADAR_RADIUS / RADAR_SIGHT_RANGE;
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar range set to " + RADAR_SIGHT_RANGE + " su");

        // Radar color
        Color color = Global.getSettings().getColor("textFriendColor");
        RADAR_R = color.getRed() / 255f;
        RADAR_G = color.getGreen() / 255f;
        RADAR_B = color.getBlue() / 255f;

        // Friendly color
        color = Global.getSettings().getColor("iconFriendColor");
        FRIENDLY_R = color.getRed() / 255f;
        FRIENDLY_G = color.getGreen() / 255f;
        FRIENDLY_B = color.getBlue() / 255f;

        // Enemy color
        color = Global.getSettings().getColor("iconEnemyColor");
        ENEMY_R = color.getRed() / 255f;
        ENEMY_G = color.getGreen() / 255f;
        ENEMY_B = color.getBlue() / 255f;

        // Neutral color
        color = Color.WHITE; //Global.getSettings().getColor("iconNeutralShipColor");
        NEUTRAL_R = color.getRed() / 255f;
        NEUTRAL_G = color.getGreen() / 255f;
        NEUTRAL_B = color.getBlue() / 255f;

        // Asteroid color
        color = Color.WHITE;
        ASTEROID_R = color.getRed() / 255f;
        ASTEROID_G = color.getGreen() / 255f;
        ASTEROID_B = color.getBlue() / 255f;
    }

    private List<CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts)
    {
        List<CombatEntityAPI> visible = new ArrayList<CombatEntityAPI>();
        for (CombatEntityAPI contact : contacts)
        {
            if (MathUtils.getDistance(contact, player.getLocation())
                    > RADAR_SIGHT_RANGE)
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
        // Draw the radar 'box'
        glColor4f(0f, 0f, 0f, RADAR_BOX_ALPHA);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, true);
        glColor4f(RADAR_R, RADAR_G, RADAR_B, RADAR_BOX_ALPHA);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS, 72, false);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .66f, 54, false);
        DrawUtils.drawCircle(RADAR_CENTER.x, RADAR_CENTER.y, RADAR_RADIUS * .33f, 36, false);
        glBegin(GL_LINES);
        glVertex2f(RADAR_CENTER.x - RADAR_RADIUS, RADAR_CENTER.y);
        glVertex2f(RADAR_CENTER.x + RADAR_RADIUS, RADAR_CENTER.y);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y + RADAR_RADIUS);
        glVertex2f(RADAR_CENTER.x, RADAR_CENTER.y - RADAR_RADIUS);
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
                glBegin(GL_TRIANGLES);
                for (CombatEntityAPI contact : filterVisible(engine.getShips()))
                {
                    // Allies
                    if (contact.getOwner() == player.getOwner())
                    {
                        glColor4f(FRIENDLY_R, FRIENDLY_G, FRIENDLY_B, RADAR_CONTACT_ALPHA);
                    }
                    // Enemies
                    else if (contact.getOwner() + player.getOwner() == 1)
                    {
                        glColor4f(ENEMY_R, ENEMY_G, ENEMY_B, RADAR_CONTACT_ALPHA);
                    }
                    // Neutrals
                    else
                    {
                        glColor4f(NEUTRAL_R, NEUTRAL_G, NEUTRAL_B, RADAR_CONTACT_ALPHA);
                    }

                    drawContact(getPointOnRadar(contact.getLocation()),
                            1.5f * (((ShipAPI) contact).getHullSize().ordinal() + 1),
                            contact.getFacing());
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
                glColor4f(ASTEROID_R, ASTEROID_G, ASTEROID_B, RADAR_CONTACT_ALPHA);
                glPointSize(2.5f);
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
        }
    }

    private void renderObjectives()
    {
        if (SHOW_OBJECTIVES)
        {
        }
    }

    private void renderShields()
    {
        if (SHOW_SHIELDS)
        {
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
        // This also acts as a main menu check
        player = engine.getPlayerShip();
        if (player == null || player.isHulk() || !engine.isEntityInPlay(player))
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
        glLineWidth(1f);

        // Draw the radar
        renderBox();
        renderContacts();
        renderAsteroids();
        //renderMissiles();
        //renderObjectives();
        //renderShields();
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}

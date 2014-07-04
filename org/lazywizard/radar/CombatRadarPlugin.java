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
import java.util.List;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import static org.lazywizard.lazylib.JSONUtils.toColor;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.radar.renderers.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: Use better names for config options in the settings file
// TODO: Change toggle to switch between 3 zoom levels + off
// TODO: This file needs loads of cleanup after the switch to a plugin system
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    private static final String SETTINGS_FILE = "data/config/combat_radar.json";
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float RADAR_SIGHT_RANGE;
    private static float RADAR_SCALING;
    private static final List<BaseRenderer> RENDERERS;
    // Performance settings
    private static boolean RESPECT_FOG_OF_WAR = true;
    // Radar color settings
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static float RADAR_FADE;
    private static Color FRIENDLY_COLOR;
    private static Color ENEMY_COLOR;
    private static Color NEUTRAL_COLOR;
    // Radar toggle button constant
    private static int RADAR_TOGGLE_KEY;
    // Whether the radar is active
    private static boolean radarEnabled = true;
    private ShipAPI player;
    private boolean hasInitiated = false;
    private CombatEngineAPI engine;

    static
    {
        // If resizing during game becomes possible, this will
        // have to be refactored into its own method
        RADAR_RADIUS = Display.getHeight() / 10f;
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.2f),
                RADAR_RADIUS * 1.2f);

        // TODO: load renderers from a CSV, allow third-party renderers
        RENDERERS = new ArrayList<>();
        RENDERERS.add(new BoxRenderer());
        RENDERERS.add(new BattleProgressRenderer());
        RENDERERS.add(new ShipRenderer());
        RENDERERS.add(new AsteroidRenderer());
        RENDERERS.add(new MissileRenderer());
        RENDERERS.add(new ObjectiveRenderer());
    }

    public static void reloadSettings() throws IOException, JSONException
    {
        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);
        final boolean useVanillaColors = settings.getBoolean("useVanillaColors");
        for (BaseRenderer renderer : RENDERERS)
        {
            renderer.reloadSettings(settings, useVanillaColors);
        }

        // Toggle key
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Performance tweak settings
        RESPECT_FOG_OF_WAR = settings.getBoolean("onlyShowVisibleContacts");

        // Radar options
        RADAR_ALPHA = (float) settings.getDouble("radarForegroundAlpha");
        RADAR_FADE = (float) settings.getDouble("radarEdgeFadeAmount");

        // Radar range
        RADAR_SIGHT_RANGE = (float) settings.getDouble("radarRange");
        RADAR_SCALING = RADAR_RADIUS / RADAR_SIGHT_RANGE;
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar range set to " + RADAR_SIGHT_RANGE + " su");

        // Radar contact colors
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : toColor(settings.getJSONArray("enemyColor"));
        NEUTRAL_COLOR = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : toColor(settings.getJSONArray("neutralColor"));
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
        // Temp fix for .6.2a bug
        if (engine != Global.getCombatEngine())
        {
            return;
        }

        // This also acts as a main menu check
        player = engine.getPlayerShip();
        if (player == null || !engine.isEntityInPlay(player))
        {
            return;
        }

        if (!hasInitiated)
        {
            hasInitiated = true;

            RadarInfo info = new CombatRadarInfo();
            for (BaseRenderer renderer : RENDERERS)
            {
                renderer.init(info);
            }
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

        // Retina display fix
        int width = (int) (Display.getWidth() * Display.getPixelScaleFactor()),
                height = (int) (Display.getHeight() * Display.getPixelScaleFactor());

        // Set OpenGL flags
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glViewport(0, 0, width, height);
        glOrtho(0, width, 0, height, 1, -1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Draw the radar elements individually
        for (BaseRenderer renderer : RENDERERS)
        {
            renderer.render(player, amount);
        }

        // Finalize drawing
        glDisable(GL_BLEND);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        hasInitiated = false;
    }

    private class CombatRadarInfo implements RadarInfo
    {
        @Override
        public Vector2f getRenderCenter()
        {
            return RADAR_CENTER;
        }

        @Override
        public float getRenderRadius()
        {
            return RADAR_RADIUS;
        }

        @Override
        public float getScale()
        {
            return RADAR_SCALING;
        }

        @Override
        public float getCenterAlpha()
        {
            return RADAR_ALPHA;
        }

        @Override
        public float getEdgeAlpha()
        {
            return RADAR_FADE;
        }

        @Override
        public float getContactAlpha()
        {
            return CONTACT_ALPHA;
        }

        @Override
        public Color getFriendlyContactColor()
        {
            return FRIENDLY_COLOR;
        }

        @Override
        public Color getEnemyContactColor()
        {
            return ENEMY_COLOR;
        }

        @Override
        public Color getNeutralContactColor()
        {
            return NEUTRAL_COLOR;
        }

        @Override
        public ShipAPI getPlayer()
        {
            return player;
        }

        @Override
        public Vector2f getPointOnRadar(Vector2f worldLoc)
        {
            Vector2f loc = new Vector2f();
            // Get position relative to {0,0}
            Vector2f.sub(worldLoc, player.getLocation(), loc);
            // Scale point to fit within the radar properly
            loc.scale(RADAR_SCALING);
            // Translate point to inside the radar box
            Vector2f.add(loc, RADAR_CENTER, loc);
            return loc;
        }

        @Override
        public List<? extends CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts)
        {
            List<CombatEntityAPI> visible = new ArrayList<>();
            for (CombatEntityAPI contact : contacts)
            {
                if (MathUtils.isWithinRange(contact, player, RADAR_SIGHT_RANGE))
                {
                    if (RESPECT_FOG_OF_WAR
                            && !CombatUtils.isVisibleToSide(contact, player.getOwner()))
                    {
                        continue;
                    }

                    visible.add(contact);
                }
            }

            return visible;
        }
    }
}

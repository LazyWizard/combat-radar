package org.lazywizard.radar;

import org.lazywizard.radar.combat.CombatRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: This file needs loads of cleanup after the switch to a plugin system
// TODO: Use better names for config options in the settings file
// TODO: Revamp default settings file to have each renderer in own section
// TODO: Move away from static variables (except for settings loaded from JSON)
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    // Path to master settings files, link to individual renderers + their settings
    private static final String SETTINGS_FILE = "data/config/radar/combat_radar.json";
    private static final String CSV_PATH = "data/config/radar/combat_radar_plugins.csv";
    // List of loaded rendering plugins
    private static final List<Class<? extends CombatRenderer>> RENDERER_CLASSES = new ArrayList<>();
    // Performance settings
    private static boolean RESPECT_FOG_OF_WAR = true;
    // Radar range settings
    private static float MAX_SIGHT_RANGE;
    private static int NUM_ZOOM_LEVELS;
    // Radar color settings
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static Color FRIENDLY_COLOR, ENEMY_COLOR, NEUTRAL_COLOR;
    // Radar toggle button constant
    private static int RADAR_TOGGLE_KEY;
    // Location and size of radar on screen
    private final List<CombatRenderer> renderers = new ArrayList<>();
    private Vector2f renderCenter;
    private float renderRadius, sightRadius, radarScaling;
    private int currentZoom;
    private ShipAPI player;
    private boolean initialized = false;
    private CombatEngineAPI engine;

    static void reloadSettings() throws IOException, JSONException
    {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);
        final boolean useVanillaColors = settings.getBoolean("useVanillaColors");

        // Toggle key
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        Global.getLogger(CombatRadarPlugin.class).log(Level.INFO,
                "Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Performance tweak settings
        RESPECT_FOG_OF_WAR = settings.getBoolean("onlyShowVisibleContacts");

        // Radar options
        RADAR_ALPHA = (float) settings.getDouble("radarForegroundAlpha");

        // Radar range
        MAX_SIGHT_RANGE = (float) settings.getDouble("maxRadarRange");
        NUM_ZOOM_LEVELS = settings.getInt("zoomLevels");

        // Radar contact colors
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : JSONUtils.toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : JSONUtils.toColor(settings.getJSONArray("enemyColor"));
        NEUTRAL_COLOR = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : JSONUtils.toColor(settings.getJSONArray("neutralColor"));

        reloadRenderers(useVanillaColors);
    }

    private static void reloadRenderers(boolean useVanillaColors) throws IOException, JSONException
    {
        // Load renderers from CSV
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "renderer id", CSV_PATH, "lw_radar");
        final ClassLoader loader = Global.getSettings().getScriptClassLoader();
        RENDERER_CLASSES.clear();
        Map<String, JSONObject> loadedFiles = new HashMap<>();
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            String className = row.getString("script");
            String settingsPath = row.optString("settings file (optional)", null);
            int renderOrder = row.getInt("render order");
            Class renderClass;

            try
            {
                renderClass = loader.loadClass(className);
            }
            catch (ClassNotFoundException ex)
            {
                throw new RuntimeException(ex);
            }

            // Ensure this is actually a valid renderer
            if (!CombatRenderer.class.isAssignableFrom(renderClass))
            {
                throw new RuntimeException(renderClass.getCanonicalName()
                        + " does not implement interface "
                        + CombatRenderer.class.getCanonicalName());
            }

            // Register the renderer with the radar
            // TODO: Sort this list later using the "render order" column
            RENDERER_CLASSES.add(renderClass);

            // If a settings file was pointed to, tell the renderer to load it
            if (settingsPath != null && !settingsPath.isEmpty())
            {
                // Keep track of already loaded files since some renderers use
                // the same settings file; this helps lower the file I/O impact
                JSONObject renderSettings;
                if (loadedFiles.containsKey(settingsPath))
                {
                    renderSettings = loadedFiles.get(settingsPath);
                }
                else
                {
                    renderSettings = Global.getSettings().loadJSON(settingsPath);
                    loadedFiles.put(settingsPath, renderSettings);
                }

                // Load settings for each individual renderer
                try
                {
                    CombatRenderer tmp = ((CombatRenderer) renderClass.newInstance());
                    tmp.reloadSettings(renderSettings, useVanillaColors);
                }
                catch (InstantiationException | IllegalAccessException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private void setZoomLevel(int zoom)
    {
        float zoomLevel = (zoom / (float) NUM_ZOOM_LEVELS);
        sightRadius = MAX_SIGHT_RANGE * zoomLevel;
        radarScaling = renderRadius / sightRadius;
        currentZoom = zoom;

        Global.getLogger(CombatRadarPlugin.class).log(Level.DEBUG,
                "Zoom level set to " + zoom + "(" + zoomLevel + ")");
    }

    private void checkInit()
    {
        if (!initialized)
        {
            initialized = true;

            renderers.clear();
            CombatRadar info = new CombatRadarInfo();
            for (Class<? extends CombatRenderer> rendererClass : RENDERER_CLASSES)
            {
                try
                {
                    CombatRenderer renderer = rendererClass.newInstance();
                    renderers.add(renderer);
                    renderer.init(info);
                }
                catch (InstantiationException | IllegalAccessException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private void checkInput(List<InputEventAPI> events)
    {
        // Radar toggle
        for (InputEventAPI event : events)
        {
            if (event.isConsumed())
            {
                continue;
            }

            if (event.isKeyDownEvent() && event.getEventValue() == RADAR_TOGGLE_KEY)
            {
                if (--currentZoom < 0)
                {
                    currentZoom = NUM_ZOOM_LEVELS;
                }

                setZoomLevel(currentZoom);
                event.consume();
                break;
            }
        }
    }

    private void render(float amount)
    {
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
        for (CombatRenderer renderer : renderers)
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

        checkInit();
        checkInput(events);

        // Zoom 0 = radar disabled
        if (currentZoom != 0)
        {
            render(amount);
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        initialized = false;

        renderRadius = Display.getHeight() / 10f;
        renderCenter = new Vector2f(Display.getWidth() - (renderRadius * 1.2f),
                renderRadius * 1.2f);
        setZoomLevel(NUM_ZOOM_LEVELS);
    }

    private class CombatRadarInfo implements CombatRadar
    {
        @Override
        @Deprecated // TEMPORARY
        public void resetView()
        {
            // TODO: call glOrtho() and glViewport() here
        }

        @Override
        public Vector2f getRenderCenter()
        {
            return renderCenter;
        }

        @Override
        public float getRenderRadius()
        {
            return renderRadius;
        }

        @Override
        public float getPixelsPerSU()
        {
            return radarScaling;
        }

        @Override
        public float getZoomLevel()
        {
            return NUM_ZOOM_LEVELS / (float) currentZoom;
        }

        @Override
        public float getRadarAlpha()
        {
            return RADAR_ALPHA;
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
            loc.scale(radarScaling);
            // Translate point to inside the radar box
            Vector2f.add(loc, renderCenter, loc);
            return loc;
        }

        @Override
        public List<CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts)
        {
            List<CombatEntityAPI> visible = new ArrayList<>();
            for (CombatEntityAPI contact : contacts)
            {
                if (MathUtils.isWithinRange(contact, player, sightRadius))
                {
                    if (RESPECT_FOG_OF_WAR && !CombatUtils.isVisibleToSide(
                            contact, player.getOwner()))
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

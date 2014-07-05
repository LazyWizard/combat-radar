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
import static org.lazywizard.lazylib.JSONUtils.toColor;
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
    private static final String SETTINGS_FILE = "data/config/radar/combat_radar.json";
    private static final String CSV_PATH = "data/config/radar/combat_radar_plugins.csv";
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float MAX_SIGHT_RANGE;
    private static float RADAR_SIGHT_RANGE, RADAR_SCALING;
    private static final List<Class<? extends CombatRenderer>> RENDERER_CLASSES = new ArrayList<>();
    // Performance settings
    private static boolean RESPECT_FOG_OF_WAR = true;
    // Radar color settings
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static Color FRIENDLY_COLOR;
    private static Color ENEMY_COLOR;
    private static Color NEUTRAL_COLOR;
    // Radar toggle button constant
    private static int RADAR_TOGGLE_KEY;
    private static int ZOOM_LEVELS;
    // Whether the radar is active
    private static int currentZoom;
    private ShipAPI player;
    private final List<CombatRenderer> renderers = new ArrayList<>();
    private boolean initialized = false;
    private CombatEngineAPI engine;

    static
    {
        // If resizing during game becomes possible, this will
        // have to be refactored into its own method
        RADAR_RADIUS = Display.getHeight() / 10f;
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.2f),
                RADAR_RADIUS * 1.2f);
    }

    static void reloadSettings() throws IOException, JSONException, ClassNotFoundException
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
        ZOOM_LEVELS = settings.getInt("zoomLevels");
        setZoomLevel(ZOOM_LEVELS);

        // Radar contact colors
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : toColor(settings.getJSONArray("enemyColor"));
        NEUTRAL_COLOR = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : toColor(settings.getJSONArray("neutralColor"));

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
            String settingsFile = row.optString("settings file (optional)", null);

            Class renderClass = loader.loadClass(className);

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
            if (settingsFile != null)
            {
                // Keep track of already loaded files since some renderers use
                // the same settings file; this helps lower the file I/O impact
                JSONObject renderSettings;
                if (loadedFiles.containsKey(settingsFile))
                {
                    renderSettings = loadedFiles.get(settingsFile);
                }
                else
                {
                    renderSettings = Global.getSettings().loadJSON(settingsFile);
                    loadedFiles.put(settingsFile, renderSettings);
                }

                // Tell renderer to reload settings using the provided file
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

        // Load settings for individual renderer components
        // TODO: Load 'proper' settings file for each plugin
        List<CombatRenderer> tmpRenderers = new ArrayList<>();
        // TODO: populate tmpRenderers with a copy of each renderer
        for (CombatRenderer renderer : tmpRenderers)
        {
            renderer.reloadSettings(settings, useVanillaColors);
        }
    }

    private static void setZoomLevel(int zoom)
    {
        float zoomLevel = (zoom / (float) ZOOM_LEVELS);
        RADAR_SIGHT_RANGE = MAX_SIGHT_RANGE * zoomLevel;
        RADAR_SCALING = RADAR_RADIUS / RADAR_SIGHT_RANGE;
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
                    currentZoom = ZOOM_LEVELS;
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
    }

    private class CombatRadarInfo implements CombatRadar
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
        public float getZoomLevel()
        {
            return ZOOM_LEVELS / (float) currentZoom;
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

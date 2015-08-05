package org.lazywizard.radar;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.renderers.BaseRenderer;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.renderers.NullRenderer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GLContext;

/**
 *
 * @author LazyWizard
 * @since 2.0
 */
// TODO: Javadoc this class
public class RadarSettings
{
    // Path to master settings files, link to individual renderers + their settings
    private static final String SETTINGS_FILE = "data/config/radar/radar_settings.json";
    private static final String COMBAT_CSV_PATH = "data/config/radar/combat_radar_plugins.csv";
    private static final String CAMPAIGN_CSV_PATH = "data/config/radar/campaign_radar_plugins.csv";
    // List of loaded rendering plugins
    private static final List<Class<? extends CombatRenderer>> COMBAT_RENDERER_CLASSES = new ArrayList<>();
    private static final List<Class<? extends CampaignRenderer>> CAMPAIGN_RENDERER_CLASSES = new ArrayList<>();
    private static final Logger LOG = Global.getLogger(RadarSettings.class);

    // Performance settings
    private static boolean RESPECT_FOG_OF_WAR, USE_VBOS;
    private static float TIME_BETWEEN_UPDATE_FRAMES;
    // Radar range settings
    private static float COMBAT_SIGHT_RANGE, CAMPAIGN_SIGHT_RANGE;
    // Zoom controls
    private static float ZOOM_ANIMATION_DURATION = .4f;
    private static int NUM_ZOOM_LEVELS;
    // Radar color settings
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static Color FRIENDLY_COLOR, ENEMY_COLOR, NEUTRAL_COLOR;
    // Radar button LWJGL constants
    private static int RADAR_TOGGLE_KEY, ZOOM_IN_KEY, ZOOM_OUT_KEY;

    public static void reloadSettings() throws JSONException, IOException
    {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        // Radar framerate limiter
        TIME_BETWEEN_UPDATE_FRAMES = 1f / (float) settings.getDouble("radarFPS");
        ZOOM_ANIMATION_DURATION = (float) settings.getDouble("zoomDuration");

        // Key bindings
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        ZOOM_IN_KEY = settings.getInt("zoomInKey");
        ZOOM_OUT_KEY = settings.getInt("zoomOutKey");
        LOG.info("Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Performance tweak settings
        RESPECT_FOG_OF_WAR = settings.getBoolean("onlyShowVisibleContacts");

        // Only use vertex buffer objects if the graphics card supports them
        // Every graphics card that's still in use should, but just in case...
        USE_VBOS = GLContext.getCapabilities().OpenGL15 && settings.getBoolean("useVBOs");
        LOG.info("Using vertex buffer objects: " + USE_VBOS);

        // Radar options
        RADAR_ALPHA = (float) settings.getDouble("radarUIAlpha");

        // Radar range
        COMBAT_SIGHT_RANGE = (float) settings.getDouble("combatRadarRange");
        CAMPAIGN_SIGHT_RANGE = (float) settings.getDouble("campaignRadarRange");
        NUM_ZOOM_LEVELS = Math.max(1, settings.getInt("zoomLevels"));

        // Radar contact colors
        final boolean useVanillaColors = settings.getBoolean("useVanillaColors");
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : JSONUtils.toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : JSONUtils.toColor(settings.getJSONArray("enemyColor"));
        NEUTRAL_COLOR = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : JSONUtils.toColor(settings.getJSONArray("neutralColor"));

        reloadRenderers(COMBAT_RENDERER_CLASSES, COMBAT_CSV_PATH, CombatRenderer.class);
        reloadRenderers(CAMPAIGN_RENDERER_CLASSES, CAMPAIGN_CSV_PATH, CampaignRenderer.class);
    }

    private static void reloadRenderers(List toPopulate, String csvPath,
            Class<? extends BaseRenderer> baseClass) throws IOException, JSONException
    {
        // Load renderers from CSV
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "renderer id", csvPath, "lw_radar");
        final ClassLoader loader = Global.getSettings().getScriptClassLoader();
        toPopulate.clear();
        final List<RendererWrapper<BaseRenderer>> preSorted = new ArrayList<>();
        final Map<String, JSONObject> loadedFiles = new HashMap<>();
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

            // Don't even bother loading NullRenderers
            if (renderClass == NullRenderer.class)
            {
                continue;
            }

            // Ensure this is actually a valid renderer
            if (!baseClass.isAssignableFrom(renderClass))
            {
                throw new RuntimeException(renderClass.getCanonicalName()
                        + " does not implement interface " + baseClass.getCanonicalName());
            }

            // Wrap the renderer's class and rendering info to be used later
            // This will be sorted using the "render order" column
            preSorted.add(new RendererWrapper(renderClass, renderOrder));

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
                    BaseRenderer tmp = ((BaseRenderer) renderClass.newInstance());
                    tmp.reloadSettings(renderSettings);
                }
                catch (InstantiationException | IllegalAccessException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }

        // Actually register the renderers with the radar, in the proper order
        Collections.sort(preSorted);
        for (RendererWrapper<BaseRenderer> wrapper : preSorted)
        {
            toPopulate.add(wrapper.getRendererClass());
        }
    }

    public static List<Class<? extends CombatRenderer>> getCombatRendererClasses()
    {
        return COMBAT_RENDERER_CLASSES;
    }

    public static List<Class<? extends CampaignRenderer>> getCampaignRendererClasses()
    {
        return CAMPAIGN_RENDERER_CLASSES;
    }

    public static boolean isRespectingFogOfWar()
    {
        return RESPECT_FOG_OF_WAR;
    }

    public static boolean usesVertexBufferObjects()
    {
        return USE_VBOS;
    }

    public static float getTimeBetweenUpdateFrames()
    {
        return TIME_BETWEEN_UPDATE_FRAMES;
    }

    public static float getMaxCombatSightRange()
    {
        return COMBAT_SIGHT_RANGE;
    }

    public static float getMaxCampaignSightRange()
    {
        return CAMPAIGN_SIGHT_RANGE;
    }

    public static float getZoomAnimationDuration()
    {
        return ZOOM_ANIMATION_DURATION;
    }

    public static int getNumZoomLevels()
    {
        return NUM_ZOOM_LEVELS;
    }

    // Radar color settings
    public static float getRadarUIAlpha()
    {
        return RADAR_ALPHA;
    }

    public static float getRadarContactAlpha()
    {
        return CONTACT_ALPHA;
    }

    public static Color getFriendlyContactColor()
    {
        return FRIENDLY_COLOR;
    }

    public static Color getEnemyContactColor()
    {
        return ENEMY_COLOR;
    }

    public static Color getNeutralContactColor()
    {
        return NEUTRAL_COLOR;
    }

    public static int getRadarToggleKey()
    {
        return RADAR_TOGGLE_KEY;
    }

    public static int getZoomInKey()
    {
        return ZOOM_IN_KEY;
    }

    public static int getZoomOutKey()
    {
        return ZOOM_OUT_KEY;
    }

    private RadarSettings()
    {
    }

    private static class RendererWrapper<T> implements Comparable<RendererWrapper>
    {
        private final Class<T> renderClass;
        private final int renderOrder;

        RendererWrapper(Class<T> renderClass, int renderOrder)
        {
            this.renderClass = renderClass;
            this.renderOrder = renderOrder;
        }

        Class<T> getRendererClass()
        {
            return renderClass;
        }

        int getRenderOrder()
        {
            return renderOrder;
        }

        @Override
        public boolean equals(Object other)
        {
            if (other == null)
            {
                return false;
            }

            if (!(other instanceof RendererWrapper))
            {
                return false;
            }

            RendererWrapper tmp = (RendererWrapper) other;
            return renderClass.equals(tmp.renderClass) && renderOrder == tmp.renderOrder;
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 61 * hash + Objects.hashCode(this.renderClass);
            hash = 61 * hash + this.renderOrder;
            return hash;
        }

        @Override
        public int compareTo(RendererWrapper other)
        {
            return Integer.compare(this.renderOrder, other.renderOrder);
        }
    }
}

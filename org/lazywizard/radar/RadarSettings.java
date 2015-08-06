package org.lazywizard.radar;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
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
 * Contains the radar's configuration data obtained from the files within
 * {@code data/config/radar/}.
 *
 * @author LazyWizard
 * @since 2.0
 */
public class RadarSettings
{
    // Path to master settings files, link to individual renderers + their settings
    private static final String SETTINGS_FILE = "data/config/radar/radar_settings.json";
    private static final String COMBAT_CSV_PATH = "data/config/radar/combat_radar_plugins.csv";
    private static final String CAMPAIGN_CSV_PATH = "data/config/radar/campaign_radar_plugins.csv";
    private static final String EXCLUDED_CSV_PATH = "data/config/radar/excluded_ships.csv";
    // Controls what tokens/ships are excluded from the radar
    private static final String NODRAW_TAG = "radar_nodraw";
    private static final Set<String> EXCLUDED_HULLS = new HashSet<>();
    private static final Set<String> EXCLUDED_PREFIXES = new HashSet<>();
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

    /**
     * Reloads all radar settings from the config file. Some changes may not
     * take effect until the next battle.
     * <p>
     * @throws JSONException if any settings are missing.
     * @throws IOException   if the settings file couldn't be loaded.
     * @since 2.0
     */
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

        reloadExcludedShips();
        reloadRenderers(COMBAT_RENDERER_CLASSES, COMBAT_CSV_PATH, CombatRenderer.class);
        reloadRenderers(CAMPAIGN_RENDERER_CLASSES, CAMPAIGN_CSV_PATH, CampaignRenderer.class);
    }

    private static void reloadExcludedShips() throws IOException, JSONException
    {
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "id", EXCLUDED_CSV_PATH, "lw_radar");
        EXCLUDED_HULLS.clear();
        EXCLUDED_PREFIXES.clear();
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            final String id = row.getString("id");
            final boolean isPrefix = row.optBoolean("prefix", false);

            if (isPrefix)
            {
                EXCLUDED_PREFIXES.add(id);
            }
            else
            {
                EXCLUDED_HULLS.add(id);
            }
        }
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
            final JSONObject row = csv.getJSONObject(x);
            final String className = row.getString("script");
            final String settingsPath = row.optString("settings file (optional)", null);
            final int renderOrder = row.getInt("render order");
            final Class renderClass;

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

    /**
     * Returns the list of renderers used in combat.
     * <p>
     * @return the {@link List} of {@link CombatRenderer}s used in combat.
     * <p>
     * @since 2.0
     */
    public static List<Class<? extends CombatRenderer>> getCombatRendererClasses()
    {
        return COMBAT_RENDERER_CLASSES;
    }

    /**
     * Returns the list of renderers used on the campaign map.
     * <p>
     * @return the {@link List} of {@link CampaignRenderer}s used on the
     *         campaign
     *         map.
     * <p>
     * @since 2.0
     */
    public static List<Class<? extends CampaignRenderer>> getCampaignRendererClasses()
    {
        return CAMPAIGN_RENDERER_CLASSES;
    }

    /**
     * Returns whether the radar will ignore contacts that the player can't
     * currently see.
     * <p>
     * @return {@code true} if contacts outside the fog of war will be ignored,
     *         {@code false} otherwise.
     * <p>
     * @since 2.0
     */
    public static boolean isRespectingFogOfWar()
    {
        return RESPECT_FOG_OF_WAR;
    }

    /**
     * Returns whether the radar will use vertex buffer objects (VBOs) when
     * rendering.
     * <p>
     * @return {@code true} if VBOs are enabled and the user's card supports
     *         them, {@code false} otherwise.
     * <p>
     * @since 2.0
     */
    public static boolean usesVertexBufferObjects()
    {
        return USE_VBOS;
    }

    /**
     * Checks if a campaign object should be drawn on the campaign radar or not.
     * <p>
     * @param token The {@link SectorEntityToken} to check.
     * <p>
     * @return {@code true} if {@code token} should <i>not</i> be drawn,
     *         {@code false} otherwise.
     * <p>
     * @since 2.0
     */
    public static boolean isFilteredOut(SectorEntityToken token)
    {
        return token.hasTag(NODRAW_TAG);
    }

    /**
     * Checks if a combat object should be drawn on the combat radar or not.
     * <p>
     * @param token The {@link CombatEntityAPI} to check.
     * <p>
     * @return {@code true} if {@code entity} should <i>not</i> be drawn,
     *         {@code false} otherwise.
     * <p>
     * @since 2.0
     */
    // TODO: Ship filtering could probably be optimized via caching
    public static boolean isFilteredOut(CombatEntityAPI entity)
    {
        // Filter out ships that mod authors don't want shown
        if (entity instanceof ShipAPI)
        {
            // Player is always visible
            if (Global.getCombatEngine() != null
                    && entity == Global.getCombatEngine().getPlayerShip())
            {
                return false;
            }

            final ShipAPI ship = (ShipAPI) entity;
            final String hullId = ship.getHullSpec().getHullId(),
                    baseHullId = ship.getHullSpec().getBaseHullId();

            // Directly excluded hulls (and their skins)
            if (EXCLUDED_HULLS.contains(hullId) || EXCLUDED_HULLS.contains(baseHullId))
            {
                return true;
            }

            // Faction-level exclusions, expensive but should be rare!
            for (String prefix : EXCLUDED_PREFIXES)
            {
                if (hullId.startsWith(prefix) || baseHullId.startsWith(prefix))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns how long there is between each radar update frame.
     * <p>
     * @return How long between each radar update frame, in seconds.
     * <p>
     * @since 2.0
     */
    public static float getTimeBetweenUpdateFrames()
    {
        return TIME_BETWEEN_UPDATE_FRAMES;
    }

    /**
     * Returns the maximum combat radar range.
     * <p>
     * @return How far the radar can see in combat at maximum zoom, in SU.
     * <p>
     * @since 2.0
     */
    public static float getMaxCombatSightRange()
    {
        return COMBAT_SIGHT_RANGE;
    }

    /**
     * Returns the maximum campaign radar range.
     * <p>
     * @return How far the radar can see on the campaign map at maximum zoom, in
     *         SU.
     * <p>
     * @since 2.0
     */
    public static float getMaxCampaignSightRange()
    {
        return CAMPAIGN_SIGHT_RANGE;
    }

    /**
     * Returns how long it takes for the radar to animate switching zoom levels.
     * <p>
     * @return How long it takes the radar to switch zoom levels, in seconds.
     * <p>
     * @since 2.0
     */
    public static float getZoomAnimationDuration()
    {
        return ZOOM_ANIMATION_DURATION;
    }

    /**
     * Returns how many zoom levels the radar supports.
     * <p>
     * @return How many different zoom levels the radar is configured to
     *         support.
     * <p>
     * @since 2.0
     */
    public static int getNumZoomLevels()
    {
        return NUM_ZOOM_LEVELS;
    }

    /**
     * Returns the alpha modifier for all radar user interface elements.
     * <p>
     * @return The alpha modifier that should be applied to all radar interface
     *         elements (but not contacts, see
     *         {@link RadarSettings#getRadarContactAlpha()}).
     * <p>
     * @since 2.0
     */
    public static float getRadarUIAlpha()
    {
        return RADAR_ALPHA;
    }

    /**
     * Returns the alpha modifier for all radar contacts.
     * <p>
     * @return The alpha modifier that should be applied to all radar contacts
     *         (but not the user interface, see
     *         {@link RadarSettings#getRadarUIAlpha()}).
     * <p>
     * @since 2.0
     */
    public static float getRadarContactAlpha()
    {
        return CONTACT_ALPHA;
    }

    /**
     * Returns the color that friendly radar contacts should be drawn with.
     * <p>
     * @return The {@link Color} that friendly contacts should appear as.
     * <p>
     * @since 2.0
     */
    public static Color getFriendlyContactColor()
    {
        return FRIENDLY_COLOR;
    }

    /**
     * Returns the color that hostile radar contacts should be drawn with.
     * <p>
     * @return The {@link Color} that hostile contacts should appear as.
     * <p>
     * @since 2.0
     */
    public static Color getEnemyContactColor()
    {
        return ENEMY_COLOR;
    }

    /**
     * Returns the color that neutral radar contacts should be drawn with.
     * <p>
     * @return The {@link Color} that neutral contacts should appear as.
     * <p>
     * @since 2.0
     */
    public static Color getNeutralContactColor()
    {
        return NEUTRAL_COLOR;
    }

    /**
     * Returns the LWJGL keyboard constant for the radar toggle key.
     * <p>
     * @return The LWJGL {@link Keyboard} constant of the key used to toggle the
     *         radar on or off. See
     * <a href="http://legacy.lwjgl.org/javadoc/constant-values.html#org.lwjgl.input.Keyboard.KEY_0">
     * the LWJGL documentation page</a> for details.
     * <p>
     * @since 2.0
     */
    public static int getRadarToggleKey()
    {
        return RADAR_TOGGLE_KEY;
    }

    /**
     * Returns the LWJGL keyboard constant for the radar zoom in key.
     * <p>
     * @return The LWJGL {@link Keyboard} constant of the key used to zoom the
     *         radar in. See
     * <a href="http://legacy.lwjgl.org/javadoc/constant-values.html#org.lwjgl.input.Keyboard.KEY_0">
     * the LWJGL documentation page</a> for details.
     * <p>
     * @since 2.0
     */
    public static int getZoomInKey()
    {
        return ZOOM_IN_KEY;
    }

    /**
     * Returns the LWJGL keyboard constant for the radar zoom out key.
     * <p>
     * @return The LWJGL {@link Keyboard} constant of the key used to zoom the
     *         radar out. See
     * <a href="http://legacy.lwjgl.org/javadoc/constant-values.html#org.lwjgl.input.Keyboard.KEY_0">
     * the LWJGL documentation page</a> for details.
     * <p>
     * @since 2.0
     */
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

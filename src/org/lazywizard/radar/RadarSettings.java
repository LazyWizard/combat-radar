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
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.renderers.BaseRenderer;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.renderers.NullRenderer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
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
    private static final String COMBAT_CSV_PATH = "data/config/radar/radar_plugins.csv";
    private static final String EXCLUDED_SHIPS_CSV_PATH = "data/config/radar/excluded_ships.csv";
    private static final String EXCLUDED_MISSILES_CSV_PATH = "data/config/radar/excluded_missiles.csv";
    private static final Set<String> EXCLUDED_HULLS = new HashSet<>();
    private static final Set<String> EXCLUDED_HULL_PREFIXES = new HashSet<>();
    private static final Set<String> EXCLUDED_MISSILES = new HashSet<>();
    // List of loaded rendering plugins
    private static final List<Class<? extends CombatRenderer>> COMBAT_RENDERER_CLASSES = new ArrayList<>();
    private static final Logger LOG = Global.getLogger(RadarSettings.class);
    // Performance settings
    private static boolean respectFogOfWar, useVBOS;
    private static float timeBetweenUpdateFrames;
    private static int verticesPerCircle;
    // Display settings
    private static float radarRenderRadius;
    // Radar range settings
    private static float sightRange;
    // Zoom controls
    private static float zoomAnimationDuration = .4f;
    private static int numZoomLevels;
    // Radar color settings
    private static float radarAlpha, contactAlpha;
    private static Color friendlyColor, enemyColor, neutralColor, allyColor;
    // Radar button LWJGL constants
    private static int radarToggleKey, zoomInKey, zoomOutKey;

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
        timeBetweenUpdateFrames = 1f / (float) settings.getDouble("radarFPS");
        zoomAnimationDuration = (float) settings.getDouble("zoomDuration");

        // Key bindings
        radarToggleKey = settings.getInt("toggleKey");
        zoomInKey = settings.getInt("zoomInKey");
        zoomOutKey = settings.getInt("zoomOutKey");
        LOG.info("Radar toggle key set to " + Keyboard.getKeyName(radarToggleKey)
                + " (" + radarToggleKey + ")");

        // Performance tweak settings
        respectFogOfWar = settings.getBoolean("onlyShowVisibleContacts");
        verticesPerCircle = settings.getInt("verticesPerCircle");

        // Size of radar on screen
        radarRenderRadius = (float) (Display.getHeight()
                * settings.getDouble("radarSize") * .5f);

        // Only use vertex buffer objects if the graphics card supports them
        // Every graphics card that's still in use should, but just in case...
        useVBOS = GLContext.getCapabilities().OpenGL15 && settings.getBoolean("useVBOs");
        LOG.info("Using vertex buffer objects: " + useVBOS);

        // Radar options
        radarAlpha = (float) settings.getDouble("radarUIAlpha");

        // Radar range
        sightRange = (float) settings.getDouble("radarRange");
        numZoomLevels = Math.max(1, settings.getInt("zoomLevels"));

        // Radar contact colors
        final boolean useVanillaColors = settings.getBoolean("useVanillaColors");
        contactAlpha = (float) settings.getDouble("contactAlpha");
        friendlyColor = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : JSONUtils.toColor(settings.getJSONArray("friendlyColor"));
        enemyColor = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : JSONUtils.toColor(settings.getJSONArray("enemyColor"));
        neutralColor = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : JSONUtils.toColor(settings.getJSONArray("neutralColor"));
        allyColor = //useVanillaColors ? Global.getSettings().getColor("") // TODO: Find correct color!
                JSONUtils.toColor(settings.getJSONArray("allyColor"));

        reloadExcluded();
        reloadRenderers(COMBAT_RENDERER_CLASSES, COMBAT_CSV_PATH, CombatRenderer.class);
    }

    private static void reloadExcluded() throws IOException, JSONException
    {
        EXCLUDED_HULLS.clear();
        EXCLUDED_HULL_PREFIXES.clear();
        EXCLUDED_MISSILES.clear();

        // Reload excluded ships
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "id", EXCLUDED_SHIPS_CSV_PATH, "lw_radar");
        for (int x = 0; x < csv.length(); x++)
        {
            final JSONObject row = csv.getJSONObject(x);
            final String id = row.getString("id");
            if (id.isEmpty())
            {
                continue;
            }

            if (row.optBoolean("prefix", false))
            {
                EXCLUDED_HULL_PREFIXES.add(id);
            }
            else
            {
                EXCLUDED_HULLS.add(id);
            }
        }

        // Reload excluded missiles
        csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "missile projectile id", EXCLUDED_MISSILES_CSV_PATH, "lw_radar");
        for (int x = 0; x < csv.length(); x++)
        {
            final JSONObject row = csv.getJSONObject(x);
            EXCLUDED_MISSILES.add(row.getString("missile projectile id"));
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
            final String id = row.getString("renderer id");
            if (id.isEmpty())
            {
                continue;
            }

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
        return Collections.unmodifiableList(COMBAT_RENDERER_CLASSES);
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
        return respectFogOfWar;
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
        return useVBOS;
    }

    /**
     * Returns how many vertices the radar should use when creating circles.
     * <p>
     * @return How many vertices any drawn circles should use.
     * <p>
     * @since 2.1
     */
    public static int getVerticesPerCircle()
    {
        return verticesPerCircle;
    }

    /**
     * Checks if a combat object should be drawn on the combat radar or not.
     * <p>
     * @param entity The {@link CombatEntityAPI} to check.
     * <p>
     * @return {@code true} if {@code entity} should <i>not</i> be drawn,
     *         {@code false} otherwise.
     * <p>
     * @since 2.0
     */
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

            // Match both ship's hull and its base hull (to block skins)
            final ShipAPI ship = (ShipAPI) entity;
            final String hullId = ship.getHullSpec().getHullId(),
                    baseHullId = ship.getHullSpec().getBaseHullId();

            // Faction-level exclusions, should only be called once to cache matching hulls
            if (!EXCLUDED_HULL_PREFIXES.isEmpty())
            {
                // Cache all excluded hulls as soon as possible
                if (Global.getSector() != null)
                {
                    final List<String> allIds = Global.getSector().getAllEmptyVariantIds();
                    allIds.addAll(Global.getSector().getAllFighterWingIds());
                    for (String prefix : EXCLUDED_HULL_PREFIXES)
                    {
                        for (String id : allIds)
                        {
                            if (id.startsWith(prefix))
                            {
                                // Remove _Hull and _wing
                                EXCLUDED_HULLS.add(id.substring(0, id.length() - 5));
                            }
                        }
                    }

                    EXCLUDED_HULL_PREFIXES.clear();
                }
                // Fallback: expensive startsWith() checks, should never run
                else
                {
                    for (String prefix : EXCLUDED_HULL_PREFIXES)
                    {
                        if (hullId.startsWith(prefix) || baseHullId.startsWith(prefix))
                        {
                            return true;
                        }
                    }
                }
            }

            // Directly excluded hulls (and their skins)
            if (EXCLUDED_HULLS.contains(hullId) || EXCLUDED_HULLS.contains(baseHullId))
            {
                return true;
            }
        }

        // Filter out decorative missiles
        else if (entity instanceof MissileAPI)
        {
            final MissileAPI missile = (MissileAPI) entity;
            if (EXCLUDED_MISSILES.contains(missile.getProjectileSpecId()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the radius of the rendered radar circle, in pixels.
     * <p>
     * @return The radius of the radar circle, in pixels.
     * <p>
     * @since 2.2
     */
    public static float getRadarRenderRadius()
    {
        return radarRenderRadius;
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
        return timeBetweenUpdateFrames;
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
        return sightRange;
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
        return zoomAnimationDuration;
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
        return numZoomLevels;
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
        return radarAlpha;
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
        return contactAlpha;
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
        return friendlyColor;
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
        return enemyColor;
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
        return neutralColor;
    }

    /**
     * Returns the color that allied radar contacts should be drawn with.
     * <p>
     * @return The {@link Color} that allied contacts should appear as.
     * <p>
     * @since 2.2
     */
    public static Color getAlliedContactColor()
    {
        return allyColor;
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
        return radarToggleKey;
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
        return zoomInKey;
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
        return zoomOutKey;
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

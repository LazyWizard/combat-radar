package org.lazywizard.radar;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.renderers.NullRenderer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class CampaignRadarPlugin implements EveryFrameScript
{
    // == CONSTANTS ==
    // Path to master settings files, link to individual renderers + their settings
    private static final String SETTINGS_FILE = "data/config/radar/campaign_radar.json";
    private static final String CSV_PATH = "data/config/radar/campaign_radar_plugins.csv";
    // How fast the zoom animates
    private static final float ZOOM_ANIMATION_DURATION = .4f;
    // List of loaded rendering plugins
    private static final List<Class<? extends CampaignRenderer>> RENDERER_CLASSES = new ArrayList<>();

    // == STATIC VARIABLES ==
    // Performance settings
    private static float TIME_BETWEEN_UPDATE_FRAMES;
    // Radar range settings
    private static float MAX_SIGHT_RANGE;
    private static int NUM_ZOOM_LEVELS;
    // Radar color settings
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static Color FRIENDLY_COLOR, ENEMY_COLOR, NEUTRAL_COLOR;
    // Radar button LWJGL constants
    private static int RADAR_TOGGLE_KEY, ZOOM_IN_KEY, ZOOM_OUT_KEY;

    // == LOCAL VARIABLES ==
    private final List<CampaignRenderer> renderers = new ArrayList<>();
    private CampaignRadarInfo radarInfo;
    private float timeSinceLastUpdateFrame = 9999f;
    private Vector2f renderCenter;
    private float renderRadius, sightRadius, radarScaling, currentZoom, intendedZoom;
    private int zoomLevel;
    private CampaignFleetAPI player;
    private boolean initialized = false, keyDown = false, enabled = true;

    static void reloadSettings() throws IOException, JSONException
    {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        // Key bindings
        RADAR_TOGGLE_KEY = settings.getInt("toggleKey");
        ZOOM_IN_KEY = settings.getInt("zoomInKey");
        ZOOM_OUT_KEY = settings.getInt("zoomOutKey");
        Global.getLogger(CampaignRadarPlugin.class).log(Level.INFO,
                "Radar toggle key set to " + Keyboard.getKeyName(RADAR_TOGGLE_KEY)
                + " (" + RADAR_TOGGLE_KEY + ")");

        // Radar options
        RADAR_ALPHA = (float) settings.getDouble("radarUIAlpha");

        // Radar range
        MAX_SIGHT_RANGE = (float) settings.getDouble("maxRadarRange");
        NUM_ZOOM_LEVELS = settings.getInt("zoomLevels");

        // Radar contact colors
        final boolean useVanillaColors = settings.getBoolean("useVanillaColors");
        CONTACT_ALPHA = (float) settings.getDouble("contactAlpha");
        FRIENDLY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconFriendColor")
                : JSONUtils.toColor(settings.getJSONArray("friendlyColor"));
        ENEMY_COLOR = useVanillaColors ? Global.getSettings().getColor("iconEnemyColor")
                : JSONUtils.toColor(settings.getJSONArray("enemyColor"));
        NEUTRAL_COLOR = useVanillaColors ? Global.getSettings().getColor("iconNeutralShipColor")
                : JSONUtils.toColor(settings.getJSONArray("neutralColor"));

        reloadRenderers();
    }

    private static void reloadRenderers() throws IOException, JSONException
    {
        // Load renderers from CSV
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "renderer id", CSV_PATH, "lw_radar");
        final ClassLoader loader = Global.getSettings().getScriptClassLoader();
        RENDERER_CLASSES.clear();
        final List<RendererWrapper<CampaignRenderer>> preSorted = new ArrayList<>();
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
            if (!CampaignRenderer.class.isAssignableFrom(renderClass))
            {
                throw new RuntimeException(renderClass.getCanonicalName()
                        + " does not implement interface "
                        + CampaignRenderer.class.getCanonicalName());
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
                    CampaignRenderer tmp = ((CampaignRenderer) renderClass.newInstance());
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
        for (RendererWrapper<CampaignRenderer> wrapper : preSorted)
        {
            RENDERER_CLASSES.add(wrapper.getRendererClass());
        }
    }

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    private void setZoomLevel(int zoom)
    {
        intendedZoom = (zoom / (float) NUM_ZOOM_LEVELS);

        if (zoomLevel == 0)
        {
            currentZoom = intendedZoom;
        }

        zoomLevel = zoom;
    }

    private void checkInit()
    {
        if (!initialized)
        {
            initialized = true;
            renderRadius = Display.getHeight() / 10f;
            renderCenter = new Vector2f(Display.getWidth() - (renderRadius * 1.2f),
                    renderRadius * 1.2f);
            setZoomLevel(NUM_ZOOM_LEVELS);
            currentZoom = intendedZoom;

            renderers.clear(); // Needed due to a .6.2a bug
            radarInfo = new CampaignRadarInfo();
            for (Class<? extends CampaignRenderer> rendererClass : RENDERER_CLASSES)
            {
                try
                {
                    CampaignRenderer renderer = rendererClass.newInstance();
                    renderers.add(renderer);
                    renderer.init(radarInfo);
                }
                catch (InstantiationException | IllegalAccessException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private void checkInput()
    {
        final boolean zoomIn = Keyboard.isKeyDown(ZOOM_IN_KEY),
                zoomOut = Keyboard.isKeyDown(ZOOM_OUT_KEY),
                toggle = Keyboard.isKeyDown(RADAR_TOGGLE_KEY);
        if (zoomIn || zoomOut || toggle)
        {
            if (keyDown == true)
            {
                return;
            }

            // Radar on/off toggle
            if (toggle)
            {
                enabled = !enabled;
            }
            // Radar zoom levels
            else
            {
                int newZoom = zoomLevel;
                if (zoomIn)
                {
                    if (--newZoom <= 0)
                    {
                        newZoom = NUM_ZOOM_LEVELS;
                    }
                }
                else
                {
                    if (++newZoom > NUM_ZOOM_LEVELS)
                    {
                        newZoom = 1;
                    }
                }

                setZoomLevel(newZoom);
            }

            keyDown = true;
        }
        else
        {
            keyDown = false;
        }
    }

    private void advanceZoom(float amount)
    {
        // Gradually zoom towards actual zoom level
        final float animationSpeed = (ZOOM_ANIMATION_DURATION * amount)
                * (float) NUM_ZOOM_LEVELS;
        if (currentZoom < intendedZoom)
        {
            currentZoom = Math.min(intendedZoom, currentZoom + animationSpeed);
        }
        else if (currentZoom > intendedZoom)
        {
            currentZoom = Math.max(intendedZoom, currentZoom - animationSpeed);
        }

        // Calculate zoom effect on radar elements
        sightRadius = MAX_SIGHT_RANGE * currentZoom;
        radarScaling = renderRadius / sightRadius;
    }

    private void render(float amount)
    {
        boolean isUpdateFrame = false;
        timeSinceLastUpdateFrame += amount;
        if (timeSinceLastUpdateFrame > TIME_BETWEEN_UPDATE_FRAMES)
        {
            isUpdateFrame = true;
            timeSinceLastUpdateFrame = 0;
        }

        // Retina display fix
        int width = (int) (Display.getWidth() * Display.getPixelScaleFactor()),
                height = (int) (Display.getHeight() * Display.getPixelScaleFactor());

        // Set OpenGL flags
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glViewport(0, 0, width, height);
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glTranslatef(0.01f, 0.01f, 0);

        // Set up the stencil test
        glClear(GL_STENCIL_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);
        glColorMask(false, false, false, false);
        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        DrawUtils.drawCircle(renderCenter.x, renderCenter.y, renderRadius, 144, true);
        glColorMask(true, true, true, true);
        radarInfo.disableStencilTest();

        // Draw the radar elements individually
        for (CampaignRenderer renderer : renderers)
        {
            renderer.render(player, amount, isUpdateFrame);
        }

        // Finalize drawing
        //radarInfo.disableStencilTest(); // Minor idiot-proofing
        glDisable(GL_BLEND);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
    }

    @Override
    public void advance(float amount)
    {
        SectorAPI sector = Global.getSector();

        // Don't display over menus
        if (sector.getCampaignUI().isShowingDialog())
        {
            return;
        }

        // Don't render if no player is found
        player = sector.getPlayerFleet();
        if (player == null || !player.isAlive())
        {
            return;
        }

        checkInit();
        checkInput();

        // Zoom 0 = radar disabled
        if (enabled && zoomLevel != 0)
        {
            advanceZoom(amount);
            render(amount);
        }
    }

    private class CampaignRadarInfo implements CampaignRadar
    {
        @Override
        @Deprecated // TEMPORARY
        public void resetView()
        {
            // TODO: call glOrtho() and glViewport() here
        }

        @Override
        public void enableStencilTest()
        {
            glEnable(GL_STENCIL_TEST);
            glStencilFunc(GL_EQUAL, 1, 1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        }

        @Override
        public void disableStencilTest()
        {
            //glStencilFunc(GL_ALWAYS, 1, 1);
            //glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glDisable(GL_STENCIL_TEST);
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
        public float getCurrentPixelsPerSU()
        {
            return radarScaling;
        }

        @Override
        public float getCurrentZoomLevel()
        {
            return NUM_ZOOM_LEVELS / (float) zoomLevel;
        }

        @Override
        public float getCurrentSightRadius()
        {
            return sightRadius;
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
        public Vector2f getPointOnRadar(Vector2f worldLoc)
        {
            float[] loc = getRawPointOnRadar(worldLoc);
            return new Vector2f(loc[0], loc[1]);
        }

        @Override
        public float[] getRawPointOnRadar(Vector2f worldLoc)
        {
            return getRawPointOnRadar(worldLoc.x, worldLoc.y);
        }

        @Override
        public float[] getRawPointOnRadar(float worldX, float worldY)
        {
            float[] loc = new float[2];

            // Get position relative to {0,0}
            // Scale point to fit within the radar properly
            // Translate point to inside the radar box
            loc[0] = ((worldX - player.getLocation().x) * radarScaling) + renderCenter.x;
            loc[1] = ((worldY - player.getLocation().y) * radarScaling) + renderCenter.y;

            return loc;
        }

        @Override
        public float[] getRawPointsOnRadar(float[] worldCoords)
        {
            if ((worldCoords.length & 1) != 0)
            {
                throw new RuntimeException("Coordinates must be in x,y pairs!");
            }

            float[] coords = new float[worldCoords.length];
            float playerX = player.getLocation().x, playerY = player.getLocation().y;
            for (int x = 0; x < worldCoords.length; x += 2)
            {
                // Get position relative to {0,0}
                // Scale point to fit within the radar properly
                // Translate point to inside the radar box
                coords[x] = ((worldCoords[x] - playerX) * radarScaling) + renderCenter.x;
                coords[x + 1] = ((worldCoords[x + 1] - playerY) * radarScaling) + renderCenter.y;
            }

            return coords;
        }

        @Override
        public List<SectorEntityToken> filterVisible(List<? extends SectorEntityToken> contacts,
                int maxContacts)
        {
            List<SectorEntityToken> visible = new ArrayList<>();
            for (SectorEntityToken contact : contacts)
            {
                // Limit maximum contacts displayed
                if (visible.size() >= maxContacts)
                {
                    return visible;
                }

                // Check if any part of the contact is visible
                // Ignore any tokens with the nodraw tag
                if (MathUtils.isWithinRange(contact, player.getLocation(),
                        sightRadius + contact.getRadius())
                        && !contact.hasTag(CampaignRenderer.NODRAW_TAG))
                {
                    visible.add(contact);
                }
            }

            return visible;
        }
    }
}

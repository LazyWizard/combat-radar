package org.lazywizard.radar;

import org.lazywizard.radar.campaign.CampaignRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.campaign.CampaignRadar;
import org.lazywizard.radar.campaign.renderers.BoxRenderer;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

class CampaignRadarPlugin implements EveryFrameScript
{
    // Location and size of radar on screen
    private static final Vector2f RADAR_CENTER;
    private static final float RADAR_RADIUS;
    private static float MAX_SIGHT_RANGE;
    private static float RADAR_SIGHT_RANGE, RADAR_SCALING;
    private static float RADAR_ALPHA, CONTACT_ALPHA;
    private static final List<CampaignRenderer> RENDERERS;
    private static int ZOOM_LEVELS;
    // Whether the radar is active
    private static int currentZoom;
    private CampaignFleetAPI player;
    private boolean hasInitiated = false;

    static
    {
        // If resizing during game becomes possible, this will
        // have to be refactored into its own method
        RADAR_RADIUS = Display.getHeight() / 10f;
        RADAR_CENTER = new Vector2f(Display.getWidth() - (RADAR_RADIUS * 1.2f),
                RADAR_RADIUS * 1.2f);

        RENDERERS = new ArrayList<>();
        RENDERERS.add(new BoxRenderer());
    }

    static void reloadSettings() throws IOException, JSONException
    {
        // TODO
        JSONObject settings = null;
        for (CampaignRenderer renderer : RENDERERS)
        {
            renderer.reloadSettings(settings);
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
        return false;
    }

    private void checkInit()
    {
        if (!hasInitiated)
        {
            hasInitiated = true;

            CampaignRadar info = new CampaignRadarInfo();
            for (CampaignRenderer renderer : RENDERERS)
            {
                renderer.init(info);
            }
        }
    }

    @Override
    public void advance(float amount)
    {
        checkInit();
    }

    private class CampaignRadarInfo implements CampaignRadar
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
        public CampaignFleetAPI getPlayer()
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
    }
}

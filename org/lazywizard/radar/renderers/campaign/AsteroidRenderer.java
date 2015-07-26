package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CampaignRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static int MAX_ASTEROIDS_SHOWN;
    private static Color ASTEROID_COLOR;
    private DrawQueue drawQueue;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showCampaignAsteroids");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("asteroidRenderer");
        MAX_ASTEROIDS_SHOWN = settings.optInt("maxShown", 1_000);
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(MAX_ASTEROIDS_SHOWN);
        drawQueue.setNextColor(ASTEROID_COLOR, radar.getContactAlpha());
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            List<? extends SectorEntityToken> asteroids = radar.filterVisible(
                    player.getContainingLocation().getAsteroids(), MAX_ASTEROIDS_SHOWN);
            if (!asteroids.isEmpty())
            {
                for (SectorEntityToken asteroid : asteroids)
                {
                    drawQueue.addVertices(radar.getRawPointOnRadar(asteroid.getLocation()));
                }
                drawQueue.finishShape(GL_POINTS);
            }
            
            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw asteroids
        radar.enableStencilTest();
        glPointSize(2f * radar.getCurrentZoomLevel());
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        radar.disableStencilTest();
    }
}

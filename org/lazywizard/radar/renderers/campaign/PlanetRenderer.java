package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.ShapeUtils;
import static org.lwjgl.opengl.GL11.*;

public class PlanetRenderer implements CampaignRenderer
{
    private static boolean SHOW_PLANETS;
    private static int MAX_PLANETS_SHOWN;
    private DrawQueue drawQueue;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_PLANETS = settings.getBoolean("showPlanets");

        settings.getJSONObject("campaignRenderers")
                .getJSONObject("planetRenderer");
        MAX_PLANETS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_PLANETS)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(MAX_PLANETS_SHOWN * 64);
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_PLANETS)
        {
            return;
        }

        // Update frame = regenerate all vertex data
        if (isUpdateFrame)
        {
            drawQueue.clear();
            List<PlanetAPI> planets = radar.filterVisible(
                    player.getContainingLocation().getPlanets(), MAX_PLANETS_SHOWN);
            if (!planets.isEmpty())
            {
                for (PlanetAPI planet : planets)
                {
                    float[] center = radar.getRawPointOnRadar(planet.getLocation());
                    float radius = planet.getRadius() * radar.getCurrentPixelsPerSU();
                    drawQueue.setNextColor(planet.getSpec().getIconColor(), radar.getContactAlpha());
                    drawQueue.addVertices(ShapeUtils.createCircle(center[0], center[1], radius, 64));
                    drawQueue.finishShape(GL_TRIANGLE_FAN);
                }
            }

            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw planets
        radar.enableStencilTest();
        glEnable(GL_LINE_SMOOTH);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_LINE_SMOOTH);
        radar.disableStencilTest();
    }
}

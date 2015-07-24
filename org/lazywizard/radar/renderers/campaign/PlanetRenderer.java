package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// TODO: Update to use isUpdateFrame
public class PlanetRenderer implements CampaignRenderer
{
    private static boolean SHOW_PLANETS;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_PLANETS = settings.getBoolean("showPlanets");
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_PLANETS)
        {
            List<PlanetAPI> planets = radar.filterVisible(
                    player.getContainingLocation().getPlanets(), 1_000);
            if (!planets.isEmpty())
            {
                radar.enableStencilTest();
                glEnable(GL_LINE_SMOOTH);

                // TODO: make planets look better
                for (PlanetAPI planet : planets)
                {
                    float[] center = radar.getRawPointOnRadar(planet.getLocation());
                    float radius = planet.getRadius() * radar.getCurrentPixelsPerSU();
                    glColor(planet.getSpec().getIconColor(), radar.getContactAlpha(), false);
                    DrawUtils.drawCircle(center[0], center[1], radius, 64, true);
                }

                glDisable(GL_LINE_SMOOTH);
                radar.disableStencilTest();
            }
        }
    }
}

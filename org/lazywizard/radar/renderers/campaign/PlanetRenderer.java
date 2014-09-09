package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;

public class PlanetRenderer implements CampaignRenderer
{
    private static boolean SHOW_PLANETS = true; // TODO: read from settings
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount)
    {
        if (SHOW_PLANETS)
        {
            List<PlanetAPI> planets = radar.filterVisible(
                    player.getContainingLocation().getPlanets(), 1_000);
            if (!planets.isEmpty())
            {
                radar.enableStencilTest();

                //for ()
                // TODO
                //DrawUtils.doSomething();

                radar.disableStencilTest();
            }
        }
    }
}

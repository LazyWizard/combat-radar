package org.lazywizard.radar.renderers.campaign;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.BaseMemoryUsageRenderer;
import org.lazywizard.radar.renderers.CampaignRenderer;

public class MemoryUsageRenderer extends BaseMemoryUsageRenderer implements CampaignRenderer
{

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        super.initiate(radar);
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        super.render(isUpdateFrame);
    }
}

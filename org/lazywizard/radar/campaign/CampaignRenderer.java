package org.lazywizard.radar.campaign;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.json.JSONException;
import org.json.JSONObject;

public interface CampaignRenderer
{
    public void reloadSettings(JSONObject settings) throws JSONException;

    public void init(CampaignRadar radar);

    public void render(CampaignFleetAPI player, float amount);
}

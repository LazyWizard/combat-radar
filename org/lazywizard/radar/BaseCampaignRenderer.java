package org.lazywizard.radar;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

public interface BaseCampaignRenderer
{
    public interface CampaignRadar
    {
        public Vector2f getRenderCenter();
        public float getRenderRadius();
        public float getScale();
        public float getZoomLevel();

        public float getRadarAlpha();
        public float getContactAlpha();

        public CampaignFleetAPI getPlayer();

        public Vector2f getPointOnRadar(Vector2f worldLoc);
    }

    public void reloadSettings(JSONObject settings) throws JSONException;

    public void init(CampaignRadar radar);

    public void render(CampaignFleetAPI player, float amount);
}

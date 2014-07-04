package org.lazywizard.radar.campaign;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.lwjgl.util.vector.Vector2f;

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

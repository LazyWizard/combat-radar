package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.OrbitalStationAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class StationRenderer implements CampaignRenderer
{
    private static boolean SHOW_STATIONS;
    private static String STATION_ICON;
    private SpriteAPI icon;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_STATIONS = settings.getBoolean("showStations");

        settings = settings.getJSONObject("stationRenderer");
        STATION_ICON = settings.optString("stationIcon", null);
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;

        if (SHOW_STATIONS)
        {
            icon = Global.getSettings().getSprite("radar", STATION_ICON);
        }
    }

    @Override
    public void render(CampaignFleetAPI player, float amount)
    {
        if (SHOW_STATIONS)
        {
            List<OrbitalStationAPI> stations = radar.filterVisible(
                    player.getContainingLocation().getOrbitalStations(), 1_000);
            if (!stations.isEmpty())
            {
                radar.enableStencilTest();
                glEnable(GL_TEXTURE_2D);

                icon.setAlphaMult(radar.getContactAlpha());
                for (OrbitalStationAPI station : stations)
                {
                    // Calculate color of station
                    float relationship = station.getFaction().getRelationship(
                            player.getFaction().getId());
                    if (relationship < 0)
                    {
                        icon.setColor(radar.getEnemyContactColor());
                    }
                    else if (relationship > 0)
                    {
                        icon.setColor(radar.getFriendlyContactColor());
                    }
                    else
                    {
                        icon.setColor(radar.getNeutralContactColor());
                    }

                    // Resize and draw station on radar
                    Vector2f center = radar.getPointOnRadar(station.getLocation());
                    float size = station.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    icon.setSize(size, size);
                    icon.setAlphaMult(radar.getContactAlpha());
                    icon.renderAtCenter(center.x, center.y);
                }

                glDisable(GL_TEXTURE_2D);
                radar.disableStencilTest();
            }
        }
    }
}

package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.SpriteBatch;

public class StationRenderer implements CampaignRenderer
{
    private static boolean SHOW_STATIONS;
    private static int MAX_STATIONS_SHOWN;
    private static String STATION_ICON;
    private SpriteBatch toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_STATIONS = settings.getBoolean("showStations");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("stationRenderer");
        STATION_ICON = settings.getString("stationIcon");
        MAX_STATIONS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_STATIONS)
        {
            return;
        }

        this.radar = radar;
        toDraw = new SpriteBatch(Global.getSettings().getSprite("radar", STATION_ICON));
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_STATIONS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            final List<SectorEntityToken> stations = radar.filterVisible(
                    player.getContainingLocation().getEntitiesWithTag(
                            Tags.STATION), MAX_STATIONS_SHOWN);
            if (!stations.isEmpty())
            {
                for (SectorEntityToken station : stations)
                {
                    // Calculate color of station
                    final float relationship = station.getFaction().getRelationship(
                            player.getFaction().getId());
                    final Color color;
                    if (relationship < 0)
                    {
                        color = radar.getEnemyContactColor();
                    }
                    else if (relationship > 0)
                    {
                        color = radar.getFriendlyContactColor();
                    }
                    else
                    {
                        color = radar.getNeutralContactColor();
                    }

                    // Resize and draw station on radar
                    float[] center = radar.getRawPointOnRadar(station.getLocation());
                    float size = station.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    toDraw.add(center[0], center[1], 0f, size, color, radar.getContactAlpha());
                }
            }

            toDraw.finish();
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        // Draw all stations
        radar.enableStencilTest();
        toDraw.draw();
        radar.disableStencilTest();
    }
}

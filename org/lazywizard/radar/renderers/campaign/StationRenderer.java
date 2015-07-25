package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

public class StationRenderer implements CampaignRenderer
{
    private static boolean SHOW_STATIONS;
    private static int MAX_STATIONS_SHOWN;
    private static String STATION_ICON;
    private SpriteAPI icon;
    private List<StationIcon> toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_STATIONS = settings.getBoolean("showStations");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("stationRenderer");
        STATION_ICON = settings.optString("stationIcon", null);
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
        icon = Global.getSettings().getSprite("radar", STATION_ICON);
        toDraw = new ArrayList<>();
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
                    toDraw.add(new StationIcon(center[0], center[1], size, color));
                }
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        icon.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all stations
        glEnable(GL_TEXTURE_2D);
        for (StationIcon sIcon : toDraw)
        {
            sIcon.render();
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class StationIcon
    {
        private final float x, y, size;
        private final Color color;

        private StationIcon(float x, float y, float size, Color color)
        {
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
        }

        private void render()
        {
            icon.setSize(size, size);
            icon.setColor(color);
            icon.renderAtCenter(x, y);
        }
    }
}

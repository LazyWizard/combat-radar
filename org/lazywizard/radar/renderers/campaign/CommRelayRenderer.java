package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.SpriteBatch;

public class CommRelayRenderer implements CampaignRenderer
{
    private static boolean SHOW_RELAYS;
    private static int MAX_RELAYS_SHOWN;
    private static String RELAY_ICON;
    private SpriteBatch toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_RELAYS = settings.getBoolean("showRelays");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("relayRenderer");
        RELAY_ICON = settings.getString("relayIcon");
        MAX_RELAYS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_RELAYS)
        {
            return;
        }

        this.radar = radar;
        toDraw = new SpriteBatch(Global.getSettings().getSprite("radar", RELAY_ICON));
    }

    private static List<SectorEntityToken> getValidRelays(LocationAPI loc)
    {
        List<SectorEntityToken> allRelays = loc.getEntitiesWithTag(Tags.COMM_RELAY);
        List<SectorEntityToken> relays = new ArrayList<>(allRelays.size());
        for (SectorEntityToken tmp : allRelays)
        {
            // Support for Knights Templar and Exigency relay sources
            if (tmp.hasTag(Tags.STATION) || tmp.hasTag(Tags.PLANET))
            {
                continue;
            }

            relays.add(tmp);
        }

        return relays;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_RELAYS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            final List<SectorEntityToken> relays = radar.filterVisible(
                    getValidRelays(player.getContainingLocation()), MAX_RELAYS_SHOWN);
            if (!relays.isEmpty())
            {
                for (SectorEntityToken relay : relays)
                {
                    // Calculate color of station
                    final float relationship = relay.getFaction().getRelationship(
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
                    float[] center = radar.getRawPointOnRadar(relay.getLocation());
                    float size = relay.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    toDraw.add(center[0], center[1], 0f, size, color,
                            radar.getContactAlpha());
                }
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        // Draw all relays
        radar.enableStencilTest();
        toDraw.render();
        radar.disableStencilTest();
    }
}

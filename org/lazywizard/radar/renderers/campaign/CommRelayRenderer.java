package org.lazywizard.radar.renderers.campaign;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

// TODO: Update to use isUpdateFrame
public class CommRelayRenderer implements CampaignRenderer
{
    private static boolean SHOW_RELAYS;
    private static String RELAY_ICON;
    private SpriteAPI icon;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_RELAYS = settings.getBoolean("showRelays");

        settings = settings.getJSONObject("relayRenderer");
        RELAY_ICON = settings.optString("relayIcon", null);
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;

        if (SHOW_RELAYS)
        {
            icon = Global.getSettings().getSprite("radar", RELAY_ICON);
        }
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
        if (SHOW_RELAYS)
        {
            List<SectorEntityToken> relays = radar.filterVisible(
                    getValidRelays(player.getContainingLocation()), 1_000);
            if (!relays.isEmpty())
            {
                radar.enableStencilTest();
                glEnable(GL_TEXTURE_2D);

                icon.setAlphaMult(radar.getContactAlpha());
                for (SectorEntityToken relay : relays)
                {
                    // Calculate color of station
                    float relationship = relay.getFaction().getRelationship(
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
                    float[] center = radar.getRawPointOnRadar(relay.getLocation());
                    float size = relay.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    icon.setSize(size, size);
                    icon.setAlphaMult(radar.getContactAlpha());
                    icon.renderAtCenter(center[0], center[1]);
                }

                glDisable(GL_TEXTURE_2D);
                radar.disableStencilTest();
            }
        }
    }
}

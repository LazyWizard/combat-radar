package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;

// TODO: Update to use isUpdateFrame
// TODO: Show bounties in gold
// TODO: Implement transponder support after Starsector 0.7a is released
public class FleetRenderer implements CampaignRenderer
{
    private static boolean SHOW_FLEETS;
    private static int MAX_FLEETS_SHOWN;
    private static Color BOUNTY_COLOR;
    private CommonRadar<SectorEntityToken> radar;
    private float lastFacing, flashTimer = 0f;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_FLEETS = settings.getBoolean("showFleets");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("fleetRenderer");
        MAX_FLEETS_SHOWN = settings.optInt("maxShown", 1_000);
        BOUNTY_COLOR = JSONUtils.toColor(settings.getJSONArray("bountyColor"));
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        this.radar = radar;
        lastFacing = 0f;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_FLEETS)
        {
            flashTimer += amount;
            if (flashTimer > 1f)
            {
                flashTimer -= 1f;
            }

            List<CampaignFleetAPI> fleets = radar.filterVisible(
                    player.getContainingLocation().getFleets(), MAX_FLEETS_SHOWN);
            if (!fleets.isEmpty())
            {
                radar.enableStencilTest();

                for (CampaignFleetAPI fleet : fleets)
                {
                    // Calculate color of fleet
                    if (FleetTypes.PERSON_BOUNTY_FLEET.equals(
                            fleet.getMemoryWithoutUpdate().getString(
                                    MemFlags.MEMORY_KEY_FLEET_TYPE)))
                    {
                        glColor(BOUNTY_COLOR, radar.getContactAlpha(), false);
                    }
                    else if (fleet.getFaction().isHostileTo(player.getFaction()))
                    {
                        glColor(radar.getEnemyContactColor(), radar.getContactAlpha(), false);
                    }
                    else
                    {
                        glColor(radar.getFriendlyContactColor(), radar.getContactAlpha(), false);
                    }

                    // Calculate facing of player ship
                    float facing;
                    if (fleet.getVelocity().lengthSquared() < 25f)
                    {
                        facing = (fleet == player ? lastFacing : 0f);
                    }
                    else
                    {
                        facing = VectorUtils.getFacing(fleet.getVelocity());
                        lastFacing = facing;
                    }

                    // Draw fleet on radar as angled triangle
                    float[] center = radar.getRawPointOnRadar(fleet.getLocation());
                    float size = Math.max(60f, fleet.getRadius())
                            * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    DrawUtils.drawEllipse(center[0], center[1],
                            size, size * .65f, facing, 3, true);
                }

                radar.disableStencilTest();
            }
        }
    }
}

package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.ids.Events;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.ShapeUtils;
import static org.lwjgl.opengl.GL11.*;

public class FleetRenderer implements CampaignRenderer
{
    private static final Logger LOG = Global.getLogger(FleetRenderer.class);
    private static final float TIME_BETWEEN_BOUNTY_UPDATES = 5f;
    private static final float BOUNTY_FLASH_SPEED = 0.5f;
    private static boolean SHOW_FLEETS;
    private static int MAX_FLEETS_SHOWN;
    private static Color BOUNTY_COLOR;
    private DrawQueue drawQueue;
    private CommonRadar<SectorEntityToken> radar;
    private Set<FactionAPI> factionsWithBounties;
    private float flashTimer = 0f, timeSinceBountyUpdate = TIME_BETWEEN_BOUNTY_UPDATES;
    private boolean isFlashing = false;

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
        factionsWithBounties = new HashSet<>();
        drawQueue = new DrawQueue(MAX_FLEETS_SHOWN * 3);
    }

    private static Set<FactionAPI> getFactionsWithBounty(LocationAPI location)
    {
        // Ignore places where system-wide bounties don't count
        if (location == null || location.isHyperspace())
        {
            return Collections.<FactionAPI>emptySet();
        }

        final Set<FactionAPI> factionsWithBounties = new HashSet<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            // Ignore non-local and factionless markets as we won't get paid by them
            if (market.getFaction() == null || market.getContainingLocation() != location)
            {
                continue;
            }

            // Check if there's an active bounty placed by this market
            final CampaignEventPlugin event = Global.getSector().getEventManager()
                    .getOngoingEvent(new CampaignEventTarget(market), Events.SYSTEM_BOUNTY);
            if (event != null)
            {
                // If so, find every faction they are willing to pay for kills against
                for (FactionAPI faction : Global.getSector().getAllFactions())
                {
                    if (market.getFaction().isHostileTo(faction))
                    {
                        factionsWithBounties.add(faction);
                    }
                }
            }
        }

        return factionsWithBounties;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_FLEETS)
        {
            return;
        }

        // Only update active bounties occasionally since it's relatively expensive
        if (!Global.getSector().isPaused())
        {
            timeSinceBountyUpdate += amount;
            if (timeSinceBountyUpdate > TIME_BETWEEN_BOUNTY_UPDATES)
            {
                factionsWithBounties = getFactionsWithBounty(Misc.getNearbyStarSystem(player));
                timeSinceBountyUpdate = 0f;
            }
        }

        // Cycle flashing of bounty targets
        flashTimer += amount;
        if (flashTimer > BOUNTY_FLASH_SPEED)
        {
            flashTimer = 0f;
            isFlashing = !isFlashing;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            final List<CampaignFleetAPI> fleets = radar.filterVisible(
                    player.getContainingLocation().getFleets(), MAX_FLEETS_SHOWN);
            if (!fleets.isEmpty())
            {
                for (CampaignFleetAPI fleet : fleets)
                {
                    // Calculate color of fleet
                    // Person bounty
                    if (FleetTypes.PERSON_BOUNTY_FLEET.equals(
                            fleet.getMemoryWithoutUpdate().getString(
                                    MemFlags.MEMORY_KEY_FLEET_TYPE)))
                    {
                        drawQueue.setNextColor(BOUNTY_COLOR, radar.getContactAlpha());
                    }
                    // General faction bounty (flash between bounty and regular color)
                    else if (isFlashing && factionsWithBounties.contains(fleet.getFaction()))
                    {
                        drawQueue.setNextColor(BOUNTY_COLOR, radar.getContactAlpha());
                    }
                    // Enemy
                    else if (fleet.getFaction().isHostileTo(player.getFaction()))
                    {
                        drawQueue.setNextColor(radar.getEnemyContactColor(), radar.getContactAlpha());
                    }
                    // Neutral/ally
                    else
                    {
                        drawQueue.setNextColor(radar.getFriendlyContactColor(), radar.getContactAlpha());
                    }

                    // Draw fleet on radar as angled triangle
                    float[] center = radar.getRawPointOnRadar(fleet.getLocation());
                    float size = Math.max(60f, fleet.getRadius())
                            * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    drawQueue.addVertices(ShapeUtils.createEllipse(center[0], center[1],
                            size, size * 0.65f, fleet.getFacing(), 3));
                    drawQueue.finishShape(GL_TRIANGLE_FAN);
                }
            }

            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw fleets
        radar.enableStencilTest();
        glPointSize(2f * radar.getCurrentZoomLevel());
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        radar.disableStencilTest();
    }
}

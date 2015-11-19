package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Events;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

// TODO: After 0.7a comes out, play around with neutral/friendly colors for Expanded Battles
public class FleetRenderer implements CampaignRenderer
{
    private static final float TIME_BETWEEN_BOUNTY_UPDATES = 5f;
    private static final float BOUNTY_FLASH_SPEED = 0.5f;
    private static boolean SHOW_FLEETS;
    private static int MAX_FLEETS_SHOWN;
    private static String FLEET_ICON;
    private static Color BOUNTY_COLOR;
    private SpriteAPI icon;
    private List<FleetIcon> toDraw;
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
        FLEET_ICON = settings.getString("fleetIcon");
        MAX_FLEETS_SHOWN = settings.optInt("maxShown", 1_000);
        BOUNTY_COLOR = JSONUtils.toColor(settings.getJSONArray("bountyColor"));
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        this.radar = radar;
        icon = Global.getSettings().getSprite("radar", FLEET_ICON);
        toDraw = new ArrayList<>();
        factionsWithBounties = new HashSet<>();
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
                    if (faction.isPlayerFaction())
                    {
                        continue;
                    }

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
            toDraw.clear();
            final List<CampaignFleetAPI> fleets = radar.filterVisible(
                    player.getContainingLocation().getFleets(), MAX_FLEETS_SHOWN);
            if (!fleets.isEmpty())
            {
                for (CampaignFleetAPI fleet : fleets)
                {
                    // Calculate color of fleet
                    final Color color;
                    // Unknown contacts
                    if (fleet.getVisibilityLevelToPlayerFleet()
                            != VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS)
                    {
                        color = radar.getNeutralContactColor();
                    }
                    // Person bounty
                    else if (FleetTypes.PERSON_BOUNTY_FLEET.equals(
                            fleet.getMemoryWithoutUpdate().getString(
                                    MemFlags.MEMORY_KEY_FLEET_TYPE)))
                    {
                        color = BOUNTY_COLOR;
                    }
                    // General faction bounty (flash between bounty and regular color)
                    else if (isFlashing && factionsWithBounties.contains(fleet.getFaction()))
                    {
                        color = BOUNTY_COLOR;
                    }
                    // Enemy
                    else if (fleet.getFaction().isHostileTo(player.getFaction()))
                    {
                        color = radar.getEnemyContactColor();
                    }
                    // Neutral/ally
                    else
                    {
                        color = radar.getFriendlyContactColor();
                    }

                    // Draw fleet on radar as angled triangle
                    float[] center = radar.getRawPointOnRadar(fleet.getLocation());
                    float size = Math.max(100f, fleet.getRadius() * 2f)
                            * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    toDraw.add(new FleetIcon(center[0], center[1],
                            size, fleet.getFacing(), color));
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

        // Draw all fleets
        glEnable(GL_TEXTURE_2D);
        for (FleetIcon fIcon : toDraw)
        {
            fIcon.render();
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class FleetIcon
    {
        private final float x, y, size, facing;
        private final Color color;

        private FleetIcon(float x, float y, float size, float facing, Color color)
        {
            this.x = x;
            this.y = y;
            this.size = size;
            this.facing = facing;
            this.color = color;
        }

        private void render()
        {
            icon.setSize(size, size);
            icon.setAngle(facing - 90f);
            icon.setColor(color);
            icon.renderAtCenter(x, y);
        }
    }
}

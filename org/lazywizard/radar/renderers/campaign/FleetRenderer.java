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
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.ShapeUtils;
import static org.lwjgl.opengl.GL11.*;

// TODO: Show faction bounties flashing between gold and regular color
// TODO: Implement transponder fog of war support after Starsector 0.7a is released
public class FleetRenderer implements CampaignRenderer
{
    private static boolean SHOW_FLEETS;
    private static int MAX_FLEETS_SHOWN;
    private static Color BOUNTY_COLOR;
    private DrawQueue drawQueue;
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
        drawQueue = new DrawQueue(MAX_FLEETS_SHOWN * 3);
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_FLEETS)
        {
            return;
        }

        flashTimer += amount;
        if (flashTimer > 1f)
        {
            flashTimer -= 1f;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            List<CampaignFleetAPI> fleets = radar.filterVisible(
                    player.getContainingLocation().getFleets(), MAX_FLEETS_SHOWN);
            if (!fleets.isEmpty())
            {
                for (CampaignFleetAPI fleet : fleets)
                {
                    // Calculate color of fleet
                    if (FleetTypes.PERSON_BOUNTY_FLEET.equals(
                            fleet.getMemoryWithoutUpdate().getString(
                                    MemFlags.MEMORY_KEY_FLEET_TYPE)))
                    {
                        drawQueue.setNextColor(BOUNTY_COLOR, radar.getContactAlpha());
                    }
                    // TODO: General faction bounty
                    else if (fleet.getFaction().isHostileTo(player.getFaction()))
                    {
                        drawQueue.setNextColor(radar.getEnemyContactColor(), radar.getContactAlpha());
                    }
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

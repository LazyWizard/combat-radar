package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.glLineWidth;

public class FleetRenderer implements CampaignRenderer
{
    private static boolean SHOW_FLEETS;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_FLEETS = settings.getBoolean("showFleets");
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount)
    {
        if (SHOW_FLEETS)
        {
            List<CampaignFleetAPI> fleets = radar.filterVisible(
                    player.getContainingLocation().getFleets(), 1_000);
            if (!fleets.isEmpty())
            {
                radar.enableStencilTest();

                glLineWidth(1f);
                for (CampaignFleetAPI fleet : fleets)
                {
                    // Calculate color of fleet
                    float relationship = fleet.getFaction().getRelationship(
                            player.getFaction().getId());
                    if (relationship < 0)
                    {
                        glColor(radar.getEnemyContactColor(), radar.getContactAlpha(), false);
                    }
                    else
                    {
                        glColor(radar.getFriendlyContactColor(), radar.getContactAlpha(), false);
                    }

                    // Draw fleet on radar as angled triangle
                    Vector2f center = radar.getPointOnRadar(fleet.getLocation());
                    float facing = (fleet.getVelocity().lengthSquared() > 1f
                            ? VectorUtils.getFacing(fleet.getVelocity()) : 0f);
                    float size = Math.max(60f, fleet.getRadius())
                            * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    DrawUtils.drawEllipse(center.x, center.y, size, size * .65f,
                            facing, 3, true);
                }

                radar.disableStencilTest();
            }
        }
    }
}
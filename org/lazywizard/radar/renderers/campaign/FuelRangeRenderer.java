package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.RadarSettings;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.ShapeUtils;
import static org.lwjgl.opengl.GL11.*;

// TODO: Show max range in hyperspace with current fuel stores
public class FuelRangeRenderer implements CampaignRenderer
{
    private static final Logger Log = Logger.getLogger(FuelRangeRenderer.class);
    private static final boolean SHOW_FUEL_RANGE = true;
    private DrawQueue drawQueue;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {

    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        this.radar = radar;
        drawQueue = new DrawQueue(RadarSettings.getVerticesPerCircle() * 2);
        drawQueue.finish();
    }

    private static float getFuelRangeInSU(CampaignFleetAPI fleet)
    {
        // TODO: Find a way to get the remaining fuel as an actual float
        final float fuel = SharedData.getData().getPlayerActivityTracker()
                .getPlayerFleetStats().getFuelOnBoard();
        //final float fuel = fleet.getCargo().getCommodityQuantity(Commodities.FUEL);
        //final float fuel = fleet.getCargo().getFuel();
        System.out.println("Fuel: " + fuel);
        return (fuel / fleet.getLogistics().getFuelCostPerLightYear())
                * Misc.getUnitsPerLightYear();
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_FUEL_RANGE || !player.getContainingLocation().isHyperspace())
        {
            return;
        }

        // TODO
        if (isUpdateFrame)
        {
            drawQueue.clear();

            final float fuelRange = getFuelRangeInSU(player), fuelRadius;
            if (fuelRange <= radar.getCurrentSightRadius())
            {
                System.out.println("Range: " + fuelRange + "su");
                fuelRadius = fuelRange * radar.getCurrentPixelsPerSU();
            }
            else
            {
                fuelRadius = 0f;
            }

            if (fuelRadius > 0f)
            {
                drawQueue.setNextColor(Color.ORANGE, radar.getRadarAlpha());
                drawQueue.addVertices(ShapeUtils.createCircle(radar.getRenderCenter().x,
                        radar.getRenderCenter().y, fuelRadius, RadarSettings.getVerticesPerCircle()));
                drawQueue.finishShape(GL_LINE_LOOP);
            }

            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw cached data
        glEnable(GL_LINE_SMOOTH);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glLineWidth(5f);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_LINE_SMOOTH);
    }
}

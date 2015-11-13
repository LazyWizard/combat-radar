package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

public class PlanetRenderer implements CampaignRenderer
{
    private static boolean SHOW_PLANETS;
    private static int MAX_PLANETS_SHOWN;
    private static String PLANET_ICON, STAR_ICON;
    private SpriteAPI planetIcon, starIcon;
    private List<PlanetIcon> toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_PLANETS = settings.getBoolean("showPlanets");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("planetRenderer");
        PLANET_ICON = settings.getString("planetIcon");
        STAR_ICON = settings.getString("starIcon");
        MAX_PLANETS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_PLANETS)
        {
            return;
        }

        this.radar = radar;
        planetIcon = Global.getSettings().getSprite("radar", PLANET_ICON);
        starIcon = Global.getSettings().getSprite("radar", STAR_ICON);
        toDraw = new ArrayList<>();
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_PLANETS)
        {
            return;
        }

        // Update frame = regenerate all vertex data
        if (isUpdateFrame)
        {
            toDraw.clear();
            final List<PlanetAPI> planets = radar.filterVisible(
                    player.getContainingLocation().getPlanets(), MAX_PLANETS_SHOWN);
            if (!planets.isEmpty())
            {
                for (PlanetAPI planet : planets)
                {
                    float[] center = radar.getRawPointOnRadar(planet.getLocation());
                    float radius = planet.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    toDraw.add(new PlanetIcon(center[0],center[1], radius,
                            planet.isStar(), planet.getSpec().getIconColor()));
                }
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        planetIcon.setAlphaMult(radar.getContactAlpha());
        starIcon.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all fleets
        glEnable(GL_TEXTURE_2D);
        for (PlanetIcon pIcon : toDraw)
        {
            pIcon.render();
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

        private class PlanetIcon
    {
        private final float x, y, size;
        private final boolean isStar;
        private final Color color;

        private PlanetIcon(float x, float y, float size, boolean isStar, Color color)
        {
            this.x = x;
            this.y = y;
            this.size = size;
            this.isStar = isStar;
            this.color = color;
        }

        private void render()
        {
            final SpriteAPI icon = (isStar ? starIcon : planetIcon);
            icon.setSize(size, size);
            icon.setColor(color);
            icon.renderAtCenter(x, y);
        }
    }
}

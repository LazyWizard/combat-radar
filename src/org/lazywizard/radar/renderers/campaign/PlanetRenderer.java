package org.lazywizard.radar.renderers.campaign;

import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.SpriteBatch;
import static org.lwjgl.opengl.GL11.*;

// TODO: Get planet icon sprite directly somehow?
public class PlanetRenderer implements CampaignRenderer
{
    private static boolean SHOW_PLANETS;
    private static int MAX_PLANETS_SHOWN;
    private static String PLANET_ICON, STAR_ICON;
    private SpriteBatch toDrawPlanets, toDrawStars;
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
        toDrawPlanets = new SpriteBatch(Global.getSettings().getSprite("radar", PLANET_ICON));
        toDrawStars = new SpriteBatch(Global.getSettings().getSprite("radar", STAR_ICON));
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
            toDrawStars.clear();
            toDrawPlanets.clear();
            final List<PlanetAPI> planets = radar.filterVisible(
                    player.getContainingLocation().getPlanets(), MAX_PLANETS_SHOWN);
            if (!planets.isEmpty())
            {
                for (PlanetAPI planet : planets)
                {
                    float[] center = radar.getRawPointOnRadar(planet.getLocation());
                    float radius = Math.max(160f, planet.getRadius() * 2f)
                            * radar.getCurrentPixelsPerSU();
                    if (planet.isStar())
                    {
                        toDrawStars.add(center[0], center[1], 0f, radius,
                                planet.getSpec().getIconColor(), radar.getContactAlpha());
                    }
                    else
                    {
                        toDrawPlanets.add(center[0], center[1], 0f, radius,
                                planet.getSpec().getIconColor(), radar.getContactAlpha());
                    }
                }
            }

            toDrawStars.finish();
            toDrawPlanets.finish();
        }

        // Don't draw if there's nothing to render!
        if (toDrawPlanets.isEmpty() && toDrawStars.isEmpty())
        {
            return;
        }

        // Draw all planets and stars
        radar.enableStencilTest();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        toDrawStars.draw();
        toDrawPlanets.draw();
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }
}

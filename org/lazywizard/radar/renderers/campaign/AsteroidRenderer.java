package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CampaignRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static int MAX_ASTEROIDS_SHOWN;
    private static String ASTEROID_ICON;
    private static Color ASTEROID_COLOR;
    private SpriteAPI icon;
    private List<AsteroidIcon> toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showCampaignAsteroids");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("asteroidRenderer");
        ASTEROID_ICON = settings.getString("asteroidIcon");
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
        MAX_ASTEROIDS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        this.radar = radar;
        icon = Global.getSettings().getSprite("radar", ASTEROID_ICON);
        toDraw = new ArrayList<>();
        icon.setColor(ASTEROID_COLOR);
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            List<? extends SectorEntityToken> asteroids = radar.filterVisible(
                    player.getContainingLocation().getAsteroids(), MAX_ASTEROIDS_SHOWN);
            if (!asteroids.isEmpty())
            {
                for (SectorEntityToken asteroid : asteroids)
                {
                    final float[] loc = radar.getRawPointOnRadar(asteroid.getLocation());
                    float size = asteroid.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2.5f; // Scale upwards for better visibility
                    toDraw.add(new AsteroidIcon(loc[0], loc[1], size));
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

        // Draw all asteroids
        glEnable(GL_TEXTURE_2D);
        for (AsteroidIcon aIcon : toDraw)
        {
            aIcon.render();
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class AsteroidIcon
    {
        private final float x, y, size;

        private AsteroidIcon(float x, float y, float size)
        {
            this.x = x;
            this.y = y;
            this.size = size;
        }

        private void render()
        {
            icon.setSize(size, size);
            icon.renderAtCenter(x, y);
        }
    }
}

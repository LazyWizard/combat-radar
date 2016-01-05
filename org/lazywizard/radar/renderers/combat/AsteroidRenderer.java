package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.SpriteBatch;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CombatRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static int MAX_ASTEROIDS_SHOWN;
    private static String ASTEROID_ICON;
    private static Color ASTEROID_COLOR;
    private SpriteBatch toDraw;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showCombatAsteroids");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("asteroidRenderer");
        ASTEROID_ICON = settings.getString("asteroidIcon");
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
        MAX_ASTEROIDS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        this.radar = radar;
        toDraw = new SpriteBatch(Global.getSettings().getSprite("radar", ASTEROID_ICON));
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_ASTEROIDS || !player.isAlive())
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            final List<CombatEntityAPI> asteroids = radar.filterVisible(
                    Global.getCombatEngine().getAsteroids(), MAX_ASTEROIDS_SHOWN);
            if (!asteroids.isEmpty())
            {
                for (CombatEntityAPI asteroid : asteroids)
                {
                    final float[] loc = radar.getRawPointOnRadar(asteroid.getLocation());
                    float size = Math.max(40f, asteroid.getCollisionRadius() * 2f)
                            * radar.getCurrentPixelsPerSU();
                    size *= 1.5f; // Scale upwards for better visibility
                    toDraw.add(loc[0], loc[1], 0f, size, ASTEROID_COLOR,
                            radar.getContactAlpha());
                }
            }

            toDraw.finish();
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        // Draw all asteroids
        radar.enableStencilTest();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        toDraw.draw();
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }
}

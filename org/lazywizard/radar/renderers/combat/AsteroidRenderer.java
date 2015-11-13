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
import org.lazywizard.radar.util.DrawQueue;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CombatRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static int MAX_ASTEROIDS_SHOWN;
    private static Color ASTEROID_COLOR;
    private DrawQueue drawQueue;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showCombatAsteroids");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("asteroidRenderer");
        MAX_ASTEROIDS_SHOWN = settings.optInt("maxShown", 1_000);
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_ASTEROIDS)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(MAX_ASTEROIDS_SHOWN);
        drawQueue.setNextColor(ASTEROID_COLOR, radar.getContactAlpha());
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_ASTEROIDS || !player.isAlive())
        {
            return;
        }

        // Update frame = regenerate all vertex data
        if (isUpdateFrame)
        {
            drawQueue.clear();
            final List<CombatEntityAPI> asteroids = radar.filterVisible(
                    Global.getCombatEngine().getAsteroids(), MAX_ASTEROIDS_SHOWN);
            if (!asteroids.isEmpty())
            {
                for (CombatEntityAPI asteroid : asteroids)
                {
                    drawQueue.addVertices(radar.getRawPointOnRadar(asteroid.getLocation()));
                }
                drawQueue.finishShape(GL_POINTS);
            }
            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw asteroids
        radar.enableStencilTest();
        glPointSize(3f * radar.getCurrentZoomLevel());
        glEnable(GL_POINT_SMOOTH);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_POINT_SMOOTH);
        radar.disableStencilTest();
    }
}

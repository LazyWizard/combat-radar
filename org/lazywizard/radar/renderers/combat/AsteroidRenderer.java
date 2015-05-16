package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CombatRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static int MAX_ASTEROIDS_SHOWN;
    private static Color ASTEROID_COLOR;
    private CombatRadar radar;
    private FloatBuffer vertexMap;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showAsteroids");

        settings = settings.getJSONObject("asteroidRenderer");
        MAX_ASTEROIDS_SHOWN = settings.optInt("maxShown", 1_000);
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
        vertexMap = BufferUtils.createFloatBuffer(MAX_ASTEROIDS_SHOWN * 2);
    }

    private void generateMaps(List<CombatEntityAPI> asteroids)
    {
        vertexMap.clear();

        // Calculate vertices
        for (CombatEntityAPI asteroid : asteroids)
        {
            Vector2f radarLoc = radar.getPointOnRadar(asteroid.getLocation());
            vertexMap.put(radarLoc.x).put(radarLoc.y);
        }

        vertexMap.flip();
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_ASTEROIDS && player.isAlive())
        {
            // Update frame = regenerate all vertex data
            if (isUpdateFrame)
            {
                generateMaps(radar.filterVisible(Global.getCombatEngine().getAsteroids(), MAX_ASTEROIDS_SHOWN));
            }

            // Don't draw if there's nothing to render!
            if (vertexMap.limit() == 0)
            {
                return;
            }

            radar.enableStencilTest();

            // Draw asteroids
            glColor(ASTEROID_COLOR, radar.getContactAlpha(), false);
            glPointSize(2f * radar.getCurrentZoomLevel());
            glEnableClientState(GL_VERTEX_ARRAY);
            glVertexPointer(2, 0, vertexMap);
            glDrawArrays(GL_POINTS, 0, vertexMap.remaining() / 2);
            glDisableClientState(GL_VERTEX_ARRAY);

            radar.disableStencilTest();
        }
    }
}

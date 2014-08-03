package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class AsteroidRenderer implements CombatRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static Color ASTEROID_COLOR;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showAsteroids");

        settings = settings.getJSONObject("asteroidRenderer");
        ASTEROID_COLOR = JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_ASTEROIDS && !player.isHulk())
        {
            List<? extends CombatEntityAPI> asteroids = radar.filterVisible(
                    Global.getCombatEngine().getAsteroids());
            if (!asteroids.isEmpty())
            {
                radar.enableStencilTest();

                // Calculate raw vertices
                float[] vertices = new float[asteroids.size() * 2];
                Iterator<? extends CombatEntityAPI> iter = asteroids.iterator();
                for (int v = 0; v < asteroids.size() * 2; v += 2)
                {
                    CombatEntityAPI asteroid = iter.next();
                    Vector2f radarLoc = radar.getPointOnRadar(asteroid.getLocation());
                    vertices[v] = radarLoc.x;
                    vertices[v + 1] = radarLoc.y;
                }

                // Generate vertex map
                FloatBuffer vertexMap = BufferUtils.createFloatBuffer(vertices.length).put(vertices);
                vertexMap.flip();

                // Draw asteroids
                glColor(ASTEROID_COLOR, radar.getContactAlpha(), false);
                glPointSize(2f * radar.getCurrentZoomLevel());
                glEnableClientState(GL_VERTEX_ARRAY);
                glVertexPointer(2, 0, vertexMap);
                glDrawArrays(GL_POINTS, 0, vertices.length / 2);
                glDisableClientState(GL_VERTEX_ARRAY);

                radar.disableStencilTest();
            }
        }
    }
}

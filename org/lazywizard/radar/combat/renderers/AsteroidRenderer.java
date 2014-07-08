package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
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
                Vector2f radarLoc;
                glColor(ASTEROID_COLOR, radar.getContactAlpha(), false);
                glPointSize(2f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI asteroid : asteroids)
                {
                    radarLoc = radar.getPointOnRadar(asteroid.getLocation());
                    glVertex2f(radarLoc.x, radarLoc.y);
                }
                glEnd();
            }
        }
    }
}

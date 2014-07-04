package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.BaseRenderer;
import org.lazywizard.radar.RadarInfo;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class AsteroidRenderer implements BaseRenderer
{
    private static boolean SHOW_ASTEROIDS;
    private static Color ASTEROID_COLOR;
    private RadarInfo radar;

    @Override
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException
    {
        SHOW_ASTEROIDS = settings.getBoolean("showAsteroids");
        ASTEROID_COLOR = useVanillaColors ? Color.LIGHT_GRAY
                : JSONUtils.toColor(settings.getJSONArray("asteroidColor"));
    }

    @Override
    public void init(RadarInfo radar)
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

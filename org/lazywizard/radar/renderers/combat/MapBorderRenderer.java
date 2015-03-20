package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class MapBorderRenderer implements CombatRenderer
{
    private static boolean SHOW_BORDERS;
    private static Color RETREAT_AREA_COLOR, GRAVITY_BARRIER_COLOR;
    private float mapWidth, mapHeight;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BORDERS = settings.getBoolean("showMapBorder");

        settings = settings.getJSONObject("mapBorderRenderer");
        RETREAT_AREA_COLOR = JSONUtils.toColor(settings.getJSONArray("retreatAreaColor"));
        GRAVITY_BARRIER_COLOR = JSONUtils.toColor(settings.getJSONArray("gravityBarrierColor"));
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        CombatEngineAPI engine = Global.getCombatEngine();
        mapWidth = engine.getMapWidth() / 2f;
        mapHeight = engine.getMapHeight() / 2f;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_BORDERS)
        {
            radar.enableStencilTest();

            final Vector2f ll = radar.getPointOnRadar(new Vector2f(-mapWidth, -mapHeight)),
                    ur = radar.getPointOnRadar(new Vector2f(mapWidth, mapHeight));
            final float retreatDistance = 2000f * radar.getCurrentPixelsPerSU();

            // Draw retreat areas
            glColor(RETREAT_AREA_COLOR, radar.getRadarAlpha(), false);
            glBegin(GL_QUADS);

            // Player retreat area
            glVertex2f(ll.x, ll.y);
            glVertex2f(ll.x, ll.y + retreatDistance);
            glVertex2f(ur.x, ll.y + retreatDistance);
            glVertex2f(ur.x, ll.y);

            // Enemy retreat area
            glVertex2f(ll.x, ur.y);
            glVertex2f(ll.x, ur.y - retreatDistance);
            glVertex2f(ur.x, ur.y - retreatDistance);
            glVertex2f(ur.x, ur.y);
            glEnd();

            // Draw gravity barrier
            // FIXME: Doesn't quite line up with retreat area at max zoom
            glLineWidth(50f * radar.getCurrentPixelsPerSU());
            glColor(GRAVITY_BARRIER_COLOR, radar.getRadarAlpha(), false);
            glBegin(GL_LINE_LOOP);
            glVertex2f(ll.x, ll.y);
            glVertex2f(ll.x, ur.y);
            glVertex2f(ur.x, ur.y);
            glVertex2f(ur.x, ll.y);
            glEnd();

            radar.disableStencilTest();
        }
    }
}

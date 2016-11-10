package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class MapBorderRenderer implements CombatRenderer
{
    private static final float RETREAT_AREA_SIZE = 2000f;
    private static boolean SHOW_BORDERS;
    private static Color RETREAT_AREA_COLOR, GRAVITY_BARRIER_COLOR;
    private Vector2f rawLL, rawUR;
    private DrawQueue drawQueue;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BORDERS = settings.getBoolean("showMapBorder");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("mapBorderRenderer");
        RETREAT_AREA_COLOR = JSONUtils.toColor(settings.getJSONArray("retreatAreaColor"));
        GRAVITY_BARRIER_COLOR = JSONUtils.toColor(settings.getJSONArray("gravityBarrierColor"));
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_BORDERS)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(28);

        // Calculate where the map borders are in raw engine coordinates
        CombatEngineAPI engine = Global.getCombatEngine();
        final float mapWidth = engine.getMapWidth() / 2f,
                mapHeight = engine.getMapHeight() / 2f;
        rawLL = new Vector2f(-mapWidth, -mapHeight);
        rawUR = new Vector2f(mapWidth, mapHeight);
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_BORDERS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            final float[] ll = radar.getRawPointOnRadar(rawLL),
                    ur = radar.getRawPointOnRadar(rawUR);
            final float retreatDistance = RETREAT_AREA_SIZE * radar.getCurrentPixelsPerSU();
            final float outerDistance = 1.5f * radar.getCurrentSightRadius() * radar.getCurrentPixelsPerSU();

            // Retreat areas
            drawQueue.setNextColor(RETREAT_AREA_COLOR, radar.getRadarAlpha());
            drawQueue.addVertices(new float[]
            {
                // Player retreat
                ll[0], ll[1],
                ll[0], ll[1] + retreatDistance,
                ur[0], ll[1] + retreatDistance,
                ur[0], ll[1],
                // Enemy retreat
                ll[0], ur[1],
                ll[0], ur[1] - retreatDistance,
                ur[0], ur[1] - retreatDistance,
                ur[0], ur[1]
            });
            // Out of bounds
            drawQueue.setNextColor(Color.GRAY, radar.getRadarAlpha() * 0.3f);
            drawQueue.addVertices(new float[]
            {
                // Top
                ll[0] - outerDistance, ur[1],
                ll[0] - outerDistance, ur[1] + outerDistance,
                ur[0] + outerDistance, ur[1] + outerDistance,
                ur[0] + outerDistance, ur[1],
                // Bottom
                ll[0] - outerDistance, ll[1],
                ll[0] - outerDistance, ll[1] - outerDistance,
                ur[0] + outerDistance, ll[1] - outerDistance,
                ur[0] + outerDistance, ll[1],
                // Right
                ur[0], ur[1],
                ur[0] + outerDistance, ur[1],
                ur[0] + outerDistance, ll[1],
                ur[0], ll[1],
                // Left
                ll[0], ll[1],
                ll[0] - outerDistance, ll[1],
                ll[0] - outerDistance, ur[1],
                ll[0], ur[1]
            });
            drawQueue.finishShape(GL_QUADS);

            // Gravity barriers
            drawQueue.setNextColor(GRAVITY_BARRIER_COLOR, radar.getRadarAlpha());
            drawQueue.addVertices(new float[]
            {
                ll[0], ll[1],
                ll[0], ur[1],
                ur[0], ur[1],
                ur[0], ll[1]
            });
            drawQueue.finishShape(GL_LINE_LOOP);
            drawQueue.finish();
        }

        if (drawQueue.isEmpty())
        {
            return;
        }

        // Draw retreat areas and gravity barriers
        radar.enableStencilTest();
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        radar.disableStencilTest();
    }
}

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

// TODO: Update to use isUpdateFrame
public class MapBorderRenderer implements CombatRenderer
{
    private static final float RETREAT_AREA_SIZE = 2000f;
    private static boolean SHOW_BORDERS;
    private static Color RETREAT_AREA_COLOR, GRAVITY_BARRIER_COLOR;
    private Vector2f rawLL, rawUR;
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
        final float mapWidth = engine.getMapWidth() / 2f,
                mapHeight = engine.getMapHeight() / 2f;
        rawLL = new Vector2f(-mapWidth, -mapHeight);
        rawUR = new Vector2f(mapWidth, mapHeight);
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_BORDERS)
        {
            radar.enableStencilTest();

            final float[] ll = radar.getRawPointOnRadar(rawLL),
                    ur = radar.getRawPointOnRadar(rawUR);
            final float retreatDistance = RETREAT_AREA_SIZE * radar.getCurrentPixelsPerSU();

            // Draw retreat areas
            glColor(RETREAT_AREA_COLOR, radar.getRadarAlpha(), false);
            glBegin(GL_QUADS);

            // Player retreat area
            glVertex2f(ll[0], ll[1]);
            glVertex2f(ll[0], ll[1] + retreatDistance);
            glVertex2f(ur[0], ll[1] + retreatDistance);
            glVertex2f(ur[0], ll[1]);

            // Enemy retreat area
            glVertex2f(ll[0], ur[1]);
            glVertex2f(ll[0], ur[1] - retreatDistance);
            glVertex2f(ur[0], ur[1] - retreatDistance);
            glVertex2f(ur[0], ur[1]);
            glEnd();

            // Draw gravity barrier
            glLineWidth(50f * radar.getCurrentPixelsPerSU());
            glColor(GRAVITY_BARRIER_COLOR, radar.getRadarAlpha(), false);
            glBegin(GL_LINE_LOOP);
            glVertex2f(ll[0], ll[1]);
            glVertex2f(ll[0], ur[1]);
            glVertex2f(ur[0], ur[1]);
            glVertex2f(ur[0], ll[1]);
            glEnd();

            radar.disableStencilTest();
        }
    }
}

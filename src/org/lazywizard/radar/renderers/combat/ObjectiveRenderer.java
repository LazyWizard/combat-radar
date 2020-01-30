package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import static org.lwjgl.opengl.GL11.*;

public class ObjectiveRenderer implements CombatRenderer
{
    private static boolean SHOW_OBJECTIVES;
    private static int MAX_OBJECTIVES_SHOWN;
    private DrawQueue drawQueue;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_OBJECTIVES = settings.getBoolean("showObjectives");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("objectiveRenderer");
        MAX_OBJECTIVES_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_OBJECTIVES)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(24);
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_OBJECTIVES || !player.isAlive())
        {
            return;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            List<BattleObjectiveAPI> objectives = radar.filterVisible(
                    Global.getCombatEngine().getObjectives(), MAX_OBJECTIVES_SHOWN);
            if (!objectives.isEmpty())
            {
                for (BattleObjectiveAPI objective : objectives)
                {
                    final Color color;
                    // Owned by player
                    if (objective.getOwner() == player.getOwner())
                    {
                        color = radar.getFriendlyContactColor();
                    }
                    // Owned by opposition
                    else if (objective.getOwner() + player.getOwner() == 1)
                    {
                        color = radar.getEnemyContactColor();
                    }
                    // Not owned yet
                    else
                    {
                        color = radar.getNeutralContactColor();
                    }

                    final float[] radarLoc = radar.getRawPointOnRadar(objective.getLocation());
                    final float size = 250f * radar.getCurrentPixelsPerSU();
                    final float[] vertices = new float[]
                    {
                        radarLoc[0], radarLoc[1] + size,
                        radarLoc[0] + size, radarLoc[1],
                        radarLoc[0], radarLoc[1] - size,
                        radarLoc[0] - size, radarLoc[1]
                    };
                    drawQueue.setNextColor(color, radar.getContactAlpha());
                    drawQueue.addVertices(vertices);
                    drawQueue.finishShape(GL_LINE_LOOP);
                    drawQueue.setNextColor(color, radar.getContactAlpha() * 0.15f);
                    drawQueue.addVertices(vertices);
                    drawQueue.finishShape(GL_QUADS);
                }
            }

            drawQueue.finish();
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        radar.enableStencilTest();

        // Draw objectives
        glLineWidth(radar.getCurrentPixelsPerSU() * 25f);
        glEnable(GL_POLYGON_SMOOTH);
        glEnable(GL_BLEND);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisable(GL_BLEND);
        glDisable(GL_POLYGON_SMOOTH);

        radar.disableStencilTest();
    }
}

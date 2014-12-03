package org.lazywizard.radar.renderers.combat;

import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class ObjectiveRenderer implements CombatRenderer
{
    private static boolean SHOW_OBJECTIVES;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_OBJECTIVES = settings.getBoolean("showObjectives");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_OBJECTIVES && player.isAlive())
        {
            List<BattleObjectiveAPI> objectives = radar.filterVisible(
                    Global.getCombatEngine().getObjectives(), 1_000);
            if (!objectives.isEmpty())
            {
                radar.enableStencilTest();

                Vector2f radarLoc;
                float size = 250f * radar.getCurrentPixelsPerSU();
                glEnable(GL_LINE_SMOOTH);
                glLineWidth(size / 5f);
                for (BattleObjectiveAPI objective : objectives)
                {
                    // Owned by player
                    if (objective.getOwner() == player.getOwner())
                    {
                        glColor(radar.getFriendlyContactColor(),
                                radar.getContactAlpha(), false);
                    }
                    // Owned by opposition
                    else if (objective.getOwner() + player.getOwner() == 1)
                    {
                        glColor(radar.getEnemyContactColor(),
                                radar.getContactAlpha(), false);
                    }
                    // Not owned yet
                    else
                    {
                        glColor(radar.getNeutralContactColor(),
                                radar.getContactAlpha(), false);
                    }

                    radarLoc = radar.getPointOnRadar(objective.getLocation());

                    glBegin(GL_LINE_LOOP);
                    glVertex2f(radarLoc.x, radarLoc.y + size);
                    glVertex2f(radarLoc.x + size, radarLoc.y);
                    glVertex2f(radarLoc.x, radarLoc.y - size);
                    glVertex2f(radarLoc.x - size, radarLoc.y);
                    glEnd();
                }

                glDisable(GL_LINE_SMOOTH);
                radar.disableStencilTest();
            }
        }
    }
}
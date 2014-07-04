package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.BaseRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class ObjectiveRenderer implements BaseRenderer
{
    private static boolean SHOW_OBJECTIVES;
    private RadarInfo radar;

    @Override
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException
    {
        SHOW_OBJECTIVES = settings.getBoolean("showObjectives");
    }

    @Override
    public void init(RadarInfo radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_OBJECTIVES && !player.isHulk())
        {
            List<? extends CombatEntityAPI> objectives = radar.filterVisible(
                    Global.getCombatEngine().getObjectives());
            if (!objectives.isEmpty())
            {
                // TODO: Add customizable colors to settings
                Vector2f radarLoc;
                float size = 250f * radar.getScale();
                glLineWidth(size / 5f);
                for (CombatEntityAPI objective : objectives)
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
            }
        }
    }
}
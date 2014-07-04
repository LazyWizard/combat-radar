package org.lazywizard.radar.combat.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class BattleProgressRenderer implements CombatRenderer
{
    private static boolean SHOW_BATTLE_PROGRESS;
    private CombatRadar radar;
    private Vector2f barLocation;
    private float barWidth;
    private float barHeight;

    @Override
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException
    {
        SHOW_BATTLE_PROGRESS = settings.getBoolean("showBattleProgress");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();
        barLocation = new Vector2f(radarCenter.x + (radarRadius * 1.1f),
                radarCenter.y - (radarRadius * 1.1f));
        barWidth = radarRadius * .09f;
        barHeight = radarRadius * 2f;
    }

    @Override
    // TODO: Animate the bar (gradually move to new fleet balance)
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_BATTLE_PROGRESS)
        {
            int fpPlayer = 0, fpEnemy = 0;

            // Total up player fleet strength
            CombatFleetManagerAPI fm = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
            List<FleetMemberAPI> ships = fm.getDeployedCopy();
            //if (!engine.isSimulationBattle())
            ships.addAll(fm.getReservesCopy());
            for (FleetMemberAPI ship : ships)
            {
                fpPlayer += ship.getMemberStrength(); //.getFleetPointCost();
            }

            // Total up enemy fleet strength
            fm = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY);
            ships = fm.getDeployedCopy();
            //if (!engine.isSimulationBattle())
            ships.addAll(fm.getReservesCopy());
            for (FleetMemberAPI ship : ships)
            {
                fpEnemy += ship.getMemberStrength(); //.getFleetPointCost();
            }

            if (fpPlayer + fpEnemy <= 0)
            {
                return;
            }

            float relativeStrength = fpPlayer / (float) (fpPlayer + fpEnemy);

            glBegin(GL_QUADS);
            // Player strength
            glColor(radar.getFriendlyContactColor(), radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x, barLocation.y);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + (barHeight * relativeStrength));
            glVertex2f(barLocation.x, barLocation.y
                    + (barHeight * relativeStrength));

            // Enemy strength
            glColor(radar.getEnemyContactColor(), radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x, barLocation.y
                    + (barHeight * relativeStrength));
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + (barHeight * relativeStrength));
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x, barLocation.y
                    + barHeight);
            glEnd();
        }
    }
}

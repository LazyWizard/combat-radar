package org.lazywizard.radar.combat.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import java.awt.Color;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: Animate changes to the bar
public class BattleProgressRenderer implements CombatRenderer
{
    private static boolean SHOW_BATTLE_PROGRESS;
    private CombatRadar radar;
    private Vector2f barLocation;
    private float relativeStrengthAtBattleStart;
    private float barHeight;
    private float barWidth;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BATTLE_PROGRESS = settings.getBoolean("showBattleProgress");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();
        barLocation = new Vector2f(radarCenter.x - radarRadius,
                radarCenter.y - (radarRadius * 1.1f));
        barWidth = radarRadius * 2f;
        barHeight = radarRadius * .09f;

        relativeStrengthAtBattleStart = getRelativeStrength();
    }

    private static float getRelativeStrength()
    {
        float playerStrength = 0f, enemyStrength = 0f;

        // Total up player fleet strength
        CombatFleetManagerAPI fm = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
        List<FleetMemberAPI> ships = fm.getDeployedCopy();
        //if (!engine.isSimulationBattle())
        ships.addAll(fm.getReservesCopy());
        for (FleetMemberAPI ship : ships)
        {
            playerStrength += ship.getMemberStrength(); //.getFleetPointCost();
        }

        // Total up enemy fleet strength
        fm = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY);
        ships = fm.getDeployedCopy();
        //if (!engine.isSimulationBattle())
        ships.addAll(fm.getReservesCopy());
        for (FleetMemberAPI ship : ships)
        {
            enemyStrength += ship.getMemberStrength(); //.getFleetPointCost();
        }

        // No ships on either side = assume a draw
        float totalStrength = playerStrength + enemyStrength;
        return (totalStrength <= 0f ? 0.5f : (playerStrength / totalStrength));
    }

    @Override
    // TODO: Animate the bar (gradually move to new fleet balance)
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_BATTLE_PROGRESS)
        {
            float relativeStrengthPos = barWidth * getRelativeStrength(),
                    battleStartPos = barWidth * relativeStrengthAtBattleStart;

            glBegin(GL_QUADS);
            // Player strength
            glColor(radar.getFriendlyContactColor(), radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y);
            glVertex2f(barLocation.x,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x + relativeStrengthPos,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x + relativeStrengthPos,
                    barLocation.y);

            // Enemy strength
            glColor(radar.getEnemyContactColor(), radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x + relativeStrengthPos,
                    barLocation.y);
            glVertex2f(barLocation.x + relativeStrengthPos,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y);
            glEnd();

            glLineWidth(1f);
            glColor(Color.WHITE, radar.getRadarAlpha(), false);
            glBegin(GL_LINES);
            glVertex2f(barLocation.x + battleStartPos,
                    barLocation.y + (barHeight * 1.5f));
            glVertex2f(barLocation.x + battleStartPos,
                    barLocation.y - (barHeight * 0.5f));
            glEnd();
        }
    }
}

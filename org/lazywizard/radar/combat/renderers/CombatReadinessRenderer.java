package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// Shows combat readineess max/current/at start of battle
// Only registers between 0-100% CR (sorry Starsector+)
// Bar subtly flashes if CR is currently draining
public class CombatReadinessRenderer implements CombatRenderer
{
    private static boolean SHOW_COMBAT_READINESS;
    private static Color CURRENT_CR_COLOR, LOST_CR_COLOR, NO_CR_COLOR;
    private CombatRadar radar;
    private Vector2f barLocation;
    private float barWidth, barHeight;
    private float flashProgress;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_COMBAT_READINESS = settings.getBoolean("showCombatReadiness");

        settings = settings.getJSONObject("combatReadinessRenderer");
        CURRENT_CR_COLOR = JSONUtils.toColor(
                settings.getJSONArray("currentCRColor"));
        LOST_CR_COLOR = JSONUtils.toColor(
                settings.getJSONArray("lostCRColor"));
        NO_CR_COLOR = JSONUtils.toColor(
                settings.getJSONArray("emptyBarColor"));
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();

        barWidth = radarRadius * .09f;
        barHeight = radarRadius * 2f;
        barLocation = new Vector2f(radarCenter.x + (radarRadius * 1.1f) - barWidth,
                radarCenter.y - radarRadius);

        flashProgress = 0.5f;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_COMBAT_READINESS && !player.isHulk())
        {
            float currentCRPos = barHeight * Math.min(1f, player.getCurrentCR()),
                    initialCRPos = Math.max(currentCRPos,
                            barHeight * Math.min(1f, player.getCRAtDeployment()));

            // Current CR flashes when player is losing CR
            if (player.losesCRDuringCombat()
                    && currentCRPos < initialCRPos)
            {
                flashProgress -= amount;
            }

            // Low CR increases flash frequency
            if (player.getCurrentCR() < .4f)
            {
                flashProgress -= amount;
            }

            // Critical CR flashes even faster
            if (player.getCurrentCR() < .2f)
            {
                flashProgress -= amount;
            }

            // Clamp flash progress positive
            while (flashProgress <= 0f)
            {
                flashProgress += 1f;
            }

            float alphaMod = radar.getRadarAlpha();
            alphaMod *= ((flashProgress / 2f)) + .75f;

            // Dim the bar if player ship doesn't lose CR during battle
            if (!player.losesCRDuringCombat())
            {
                alphaMod *= .6f;
            }

            glBegin(GL_QUADS);
            // Current CR
            glColor(CURRENT_CR_COLOR, alphaMod, false);
            glVertex2f(barLocation.x,
                    barLocation.y);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x,
                    barLocation.y + currentCRPos);

            // Lost CR
            glColor(LOST_CR_COLOR, radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x,
                    barLocation.y + initialCRPos);

            // Unobtained CR
            glColor(NO_CR_COLOR, radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x - barWidth,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x, barLocation.y
                    + barHeight);
            glEnd();

            // Draw CR threshold notches
            glColor(Color.WHITE, .5f, false);
            glLineWidth(1f);
            glBegin(GL_LINES);
            for (int x = 1; x <= 4; x++)
            {
                float lineHeight = barLocation.y + ((barHeight / 5f) * x);
                glVertex2f(barLocation.x, lineHeight);
                glVertex2f(barLocation.x - (barWidth * .33f), lineHeight);
                glVertex2f(barLocation.x - (barWidth * .66f), lineHeight);
                glVertex2f(barLocation.x - barWidth, lineHeight);
            }
            glEnd();

            // Draw max CR possible as horizontal line
            if (Global.getCombatEngine().isInCampaign())
            {
                FleetMemberAPI tmp = CombatUtils.getFleetMember(player);
                if (tmp != null)
                {
                    float maxCRPos = barHeight * Math.min(1f,
                            tmp.getRepairTracker().getMaxCR());
                    glLineWidth(1.5f);
                    glColor(Color.WHITE, radar.getRadarAlpha(), false);
                    glBegin(GL_LINES);
                    glVertex2f(barLocation.x - (barWidth * 1.5f),
                            barLocation.y + maxCRPos);
                    glVertex2f(barLocation.x + (barWidth * 0.5f),
                            barLocation.y + maxCRPos);
                    glEnd();
                }
            }
        }
    }
}

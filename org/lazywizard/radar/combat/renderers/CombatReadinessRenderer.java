package org.lazywizard.radar.combat.renderers;

import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import org.json.JSONException;
import org.json.JSONObject;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// Shows combat readineess max/current/at start of battle
// Only registers between 0-100% CR (sorry Starsector+)
// Rendered as vertical bar to the right of radar
// Max CR possible w/ current stats/crew is horizontal line in bar
// Current CR is brighter portion of bar
// CR expended this battle is darker portion of bar
// Rest of bar is black
// Bar subtly flashes if CR is currently draining
// TODO: Rewrite this (based on battle progress bar)
public class CombatReadinessRenderer implements CombatRenderer
{
    private static boolean SHOW_COMBAT_READINESS;
    private static Color CURRENT_CR_COLOR, LOST_CR_COLOR, NO_CR_COLOR;
    private CombatRadar radar;
    private Vector2f barLocation;
    private float barWidth;
    private float barHeight;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_COMBAT_READINESS = settings.getBoolean("showCombatReadiness");
        // TODO: load color settings from JSON
        CURRENT_CR_COLOR = Color.CYAN;
        LOST_CR_COLOR = Color.BLUE;
        NO_CR_COLOR = Color.DARK_GRAY;
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();
        barLocation = new Vector2f(radarCenter.x + (radarRadius * 1.1f),
                radarCenter.y - radarRadius);
        barWidth = radarRadius * .09f;
        barHeight = radarRadius * 2f;
    }

    @Override
    // TODO: Animate the bar (gradually move to new fleet balance)
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_COMBAT_READINESS)
        {
            float currentCRPos = barHeight * player.getCurrentCR(),
                    initialCRPos = barHeight * player.getCRAtDeployment(),
                    maxCRPos = barHeight * player.getMutableStats()
                    .getMaxCombatReadiness().getModifiedValue();

            glBegin(GL_QUADS);
            // Current CR
            glColor(CURRENT_CR_COLOR, radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x,
                    barLocation.y + currentCRPos);

            // Lost CR
            glColor(LOST_CR_COLOR, radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + currentCRPos);
            glVertex2f(barLocation.x+barWidth,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x,
                    barLocation.y + initialCRPos);

            // Unobtained CR
            glColor(NO_CR_COLOR, radar.getRadarAlpha(), false);
            glVertex2f(barLocation.x,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + initialCRPos);
            glVertex2f(barLocation.x + barWidth,
                    barLocation.y + barHeight);
            glVertex2f(barLocation.x, barLocation.y
                    + barHeight);
            glEnd();

            // TODO: add outline
        }
    }
}

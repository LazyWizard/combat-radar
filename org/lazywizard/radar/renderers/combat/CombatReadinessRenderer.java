package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// Shows combat readineess max/current/at start of battle
// Only registers between 0-100% CR (sorry Starsector+)
// Bar subtly flashes if CR is currently draining
// TODO: Update to use isUpdateFrame
public class CombatReadinessRenderer implements CombatRenderer
{
    private static boolean SHOW_COMBAT_READINESS;
    private static Color CURRENT_CR_COLOR, LOST_CR_COLOR, NO_CR_COLOR;
    private CombatRadar radar;
    private Vector2f barLocation;
    private FloatBuffer vertexMap, colorMap;
    private IntBuffer indexMap;
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

    private static float[] getQuadColor(Color color, float alphaMult)
    {
        float r = color.getRed() / 255f,
                g = color.getGreen() / 255f,
                b = color.getBlue() / 255f,
                a = (color.getAlpha() / 255f) * alphaMult;

        return new float[]
        {
            r, g, b, a,
            r, g, b, a,
            r, g, b, a,
            r, g, b, a
        };
    }

    @Override
    public void init(CombatRadar radar)
    {
        // Location and size of radar on the screen
        this.radar = radar;
        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();

        // Location and size of bar on the screen
        barWidth = radarRadius * .09f;
        barHeight = radarRadius * 2f;
        barLocation = new Vector2f(radarCenter.x + (radarRadius * 1.15f) - barWidth,
                radarCenter.y - radarRadius);

        // Generate OpenGL index mappings
        int[] indices = new int[]
        {
            // First quad (as triangles)
            0, 1, 2,
            0, 2, 3,
            // Second quad (as triangles)
            4, 5, 6,
            4, 6, 7,
            // Third quad (as triangles)
            8, 9, 10,
            8, 10, 11,
        };

        vertexMap = BufferUtils.createFloatBuffer(24);
        colorMap = BufferUtils.createFloatBuffer(48);
        indexMap = BufferUtils.createIntBuffer(18).put(indices);
        indexMap.flip();

        flashProgress = 0.5f;
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_COMBAT_READINESS && player.isAlive())
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

            // Calculate current flash alpha
            float alphaMod = radar.getRadarAlpha();
            alphaMod *= ((flashProgress / 2f)) + .75f;

            // Dim the bar if player ship doesn't lose CR during battle
            if (!player.losesCRDuringCombat())
            {
                alphaMod *= .5f;
            }

            // Generate OpenGL color mappings
            float[] colors = new float[48];
            System.arraycopy(getQuadColor(CURRENT_CR_COLOR, alphaMod),
                    0, colors, 0, 16);
            System.arraycopy(getQuadColor(LOST_CR_COLOR, radar.getRadarAlpha()),
                    0, colors, 16, 16);
            System.arraycopy(getQuadColor(NO_CR_COLOR, radar.getRadarAlpha()),
                    0, colors, 32, 16);
            colorMap.put(colors).flip();

            // Generate vertex mappings
            float[] vertices = new float[]
            {
                // Current CR
                barLocation.x, barLocation.y,
                barLocation.x + barWidth, barLocation.y,
                barLocation.x + barWidth, barLocation.y + currentCRPos,
                barLocation.x, barLocation.y + currentCRPos,
                // Lost CR
                barLocation.x, barLocation.y + currentCRPos,
                barLocation.x + barWidth, barLocation.y + currentCRPos,
                barLocation.x + barWidth, barLocation.y + initialCRPos,
                barLocation.x, barLocation.y + initialCRPos,
                // Unobtained CR
                barLocation.x, barLocation.y + initialCRPos,
                barLocation.x + barWidth, barLocation.y + initialCRPos,
                barLocation.x + barWidth, barLocation.y + barHeight,
                barLocation.x, barLocation.y + barHeight
            };
            vertexMap.put(vertices).flip();

            // Finally, we can actually draw the bar
            glEnableClientState(GL_VERTEX_ARRAY);
            glEnableClientState(GL_COLOR_ARRAY);
            glVertexPointer(2, 0, vertexMap);
            glColorPointer(4, 0, colorMap);
            glDrawElements(GL_TRIANGLES, indexMap);
            glDisableClientState(GL_VERTEX_ARRAY);
            glDisableClientState(GL_COLOR_ARRAY);

            // Draw CR threshold notches
            glColor(Color.WHITE, .5f, false);
            glLineWidth(1f);
            glBegin(GL_LINES);
            for (int x = 1; x <= 4; x++)
            {
                float lineHeight = barLocation.y + ((barHeight / 5f) * x);
                glVertex2f(barLocation.x, lineHeight);
                glVertex2f(barLocation.x + (barWidth * .33f), lineHeight);
                glVertex2f(barLocation.x + (barWidth * .66f), lineHeight);
                glVertex2f(barLocation.x + barWidth, lineHeight);
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
                    glVertex2f(barLocation.x - (barWidth * 0.5f),
                            barLocation.y + maxCRPos);
                    glVertex2f(barLocation.x + (barWidth * 1.5f),
                            barLocation.y + maxCRPos);
                    glEnd();
                }
            }
        }
    }
}

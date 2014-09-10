package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// TODO: Draw part of bar that's yet to be filled slightly darker
public class BattleProgressRenderer implements CombatRenderer
{
    private static boolean SHOW_BATTLE_PROGRESS, ANIMATE_BAR;
    private static float ANIMATION_SPEED;
    private static final float TIME_BETWEEN_CHECKS = .2f;
    private CombatRadar radar;
    private Vector2f barLocation;
    private FloatBuffer vertexMap, colorMap;
    private IntBuffer indexMap;
    private float relativeStrength, relativeStrengthAtBattleStart,
            displayedRelativeStrength, nextCheck;
    private float barWidth, barHeight, flashProgress;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BATTLE_PROGRESS = settings.getBoolean("showBattleProgress");

        settings = settings.getJSONObject("battleProgressRenderer");
        ANIMATE_BAR = settings.getBoolean("animateProgressBar");
        ANIMATION_SPEED = (float) settings.getDouble("barAnimationSpeed") / 100f;
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
        barWidth = radarRadius * 2f;
        barHeight = radarRadius * .09f;
        barLocation = new Vector2f(radarCenter.x - radarRadius,
                radarCenter.y - (radarRadius * 1.15f));

        // Relative bar section locations
        relativeStrength = getRelativeStrength();
        relativeStrengthAtBattleStart = relativeStrength;
        displayedRelativeStrength = relativeStrength;

        // Generate OpenGL index mappings
        int[] indices = new int[]
        {
            // First quad (as triangles)
            0, 1, 2,
            0, 2, 3,
            // Second quad (as triangles)
            4, 5, 6,
            4, 6, 7
        };

        vertexMap = BufferUtils.createFloatBuffer(16);
        colorMap = BufferUtils.createFloatBuffer(32);
        indexMap = BufferUtils.createIntBuffer(12).put(indices);
        indexMap.flip();

        flashProgress = 0.5f;
        nextCheck = TIME_BETWEEN_CHECKS;
    }

    private static float getRelativeStrength()
    {
        CombatEngineAPI engine = Global.getCombatEngine();
        float playerStrength = 0f, enemyStrength = 0f;

        // Total up player fleet strength
        CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
        List<FleetMemberAPI> ships = fm.getDeployedCopy();
        //if (!engine.isSimulation())
        ships.addAll(fm.getReservesCopy());
        for (FleetMemberAPI ship : ships)
        {
            playerStrength += ship.getMemberStrength(); //.getFleetPointCost();
        }

        // Total up enemy fleet strength
        fm = engine.getFleetManager(FleetSide.ENEMY);
        ships = fm.getDeployedCopy();
        //if (!engine.isSimulation())
        ships.addAll(fm.getReservesCopy());
        for (FleetMemberAPI ship : ships)
        {
            enemyStrength += ship.getMemberStrength(); //.getFleetPointCost();
        }

        // No ships on either side = assume a draw
        float totalStrength = playerStrength + enemyStrength;
        return (totalStrength <= 0f ? 0.5f : (playerStrength / totalStrength));
    }

    // TODO: can be replaced with proper API method after .6.5a
    private static boolean isRetreating(FleetSide side)
    {
        int owner = side.ordinal();
        boolean hasDeployed = false;
        CombatFleetManagerAPI fm = Global.getCombatEngine().getFleetManager(side);
        for (ShipAPI ship : Global.getCombatEngine().getShips())
        {
            if (ship.getOwner() == owner)
            {
                hasDeployed = true;

                AssignmentInfo orders = fm.getAssignmentFor(ship);
                if (orders == null || orders.getType() != CombatAssignmentType.RETREAT)
                {
                    return false;
                }
            }
        }

        return hasDeployed;
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_BATTLE_PROGRESS)
        {
            nextCheck -= amount;
            if (nextCheck <= 0)
            {
                relativeStrength = getRelativeStrength();
                nextCheck = TIME_BETWEEN_CHECKS;
            }

            // If animated, gradually move to the current fleet balance
            if (ANIMATE_BAR)
            {
                // Balance moved towards player, grow bar
                if (displayedRelativeStrength < relativeStrength)
                {
                    displayedRelativeStrength = Math.min(relativeStrength,
                            displayedRelativeStrength + (ANIMATION_SPEED * amount));
                }
                // Balance moved towards enemy, shrink bar
                else if (displayedRelativeStrength > relativeStrength)
                {
                    displayedRelativeStrength = Math.max(relativeStrength,
                            displayedRelativeStrength - (ANIMATION_SPEED * amount));
                }
            }
            // If not animated, instantly move to new fleet balance
            else
            {
                displayedRelativeStrength = relativeStrength;
            }

            float relativeStrengthPos = barWidth * displayedRelativeStrength,
                    battleStartPos = barWidth * relativeStrengthAtBattleStart;

            // Calculate current flash alpha
            boolean playerRetreating = isRetreating(FleetSide.PLAYER),
                    enemyRetreating = isRetreating(FleetSide.ENEMY);
            if (playerRetreating || enemyRetreating)
            {
                flashProgress -= amount * 2f;
                if (flashProgress <= 0f)
                {
                    flashProgress += 1f;
                }
            }
            else
            {
                flashProgress = 0.5f;
            }

            float flashAlpha = (flashProgress / 2f) + .75f;

            // Generate OpenGL color mappings
            float[] colors = new float[32];
            System.arraycopy(getQuadColor(radar.getFriendlyContactColor(),
                    radar.getRadarAlpha() * (playerRetreating ? flashAlpha : 1f)),
                    0, colors, 0, 16);
            System.arraycopy(getQuadColor(radar.getEnemyContactColor(),
                    radar.getRadarAlpha() * (enemyRetreating ? flashAlpha : 1f)),
                    0, colors, 16, 16);
            colorMap.put(colors).flip();

            // Generate vertex mappings
            float[] vertices = new float[]
            {
                // Player strength
                barLocation.x, barLocation.y,
                barLocation.x, barLocation.y + barHeight,
                barLocation.x + relativeStrengthPos, barLocation.y + barHeight,
                barLocation.x + relativeStrengthPos, barLocation.y,
                // Enemy strength
                barLocation.x + relativeStrengthPos, barLocation.y,
                barLocation.x + relativeStrengthPos, barLocation.y + barHeight,
                barLocation.x + barWidth, barLocation.y + barHeight,
                barLocation.x + barWidth, barLocation.y
            };
            vertexMap.put(vertices).flip();

            // Finally, we can actually draw the bar
            glEnableClientState(GL_VERTEX_ARRAY);
            glEnableClientState(GL_COLOR_ARRAY);
            glVertexPointer(2, 0, vertexMap);
            glColorPointer(4, 0, colorMap);
            glDrawElements(GL_TRIANGLES, indexMap);
            glDisableClientState(GL_COLOR_ARRAY);
            glDisableClientState(GL_VERTEX_ARRAY);

            // Show original relative strengths
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
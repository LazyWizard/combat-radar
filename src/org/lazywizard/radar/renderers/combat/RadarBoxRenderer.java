package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.RadarSettings;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.ShapeUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;

public class RadarBoxRenderer implements CombatRenderer
{
    private static boolean SHOW_BORDER_LINES;
    private static Color RADAR_BG_COLOR, RADAR_FG_COLOR, RADAR_FG_DEAD_COLOR;
    private static float RADAR_OPACITY, RADAR_EDGE_ALPHA;
    private static boolean REVERSE_FADE;
    private DrawQueue boxDrawQueue, zoomDrawQueue;
    private boolean firstFrame = true, wasAliveLastFrame = false;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BORDER_LINES = settings.getBoolean("showBorderLines");

        // Foreground settings (match vanilla HUD)
        RADAR_FG_COLOR = Global.getSettings().getColor("textFriendColor");
        RADAR_FG_DEAD_COLOR = Global.getSettings().getColor("textNeutralColor");
        REVERSE_FADE = settings.getBoolean("reverseRingFade");

        // Background settings
        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("radarBoxRenderer");
        RADAR_BG_COLOR = JSONUtils.toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        RADAR_EDGE_ALPHA = (float) settings.getDouble("radarEdgeAlpha");
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        this.radar = radar;
        firstFrame = true;
        boxDrawQueue = new DrawQueue(500, GL_STATIC_DRAW);
        zoomDrawQueue = new DrawQueue(8);
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        final Vector2f radarCenter = radar.getRenderCenter();
        final float radarRadius = radar.getRenderRadius();

        // The box itself very rarely changes, so it's cached in a separate DrawQueue
        if (firstFrame || (isUpdateFrame && (player.isAlive() != wasAliveLastFrame)))
        {
            firstFrame = false;
            wasAliveLastFrame = player.isAlive();
            boxDrawQueue.clear();

            final float radarAlpha = radar.getRadarAlpha() * 0.8f,
                    radarCenterFade = REVERSE_FADE ? radarAlpha : radarAlpha * RADAR_EDGE_ALPHA,
                    radarEdgeFade = REVERSE_FADE ? radarCenterFade * RADAR_EDGE_ALPHA : radarAlpha,
                    radarMidFade = (radarCenterFade + radarEdgeFade) / 2f;

            // Slight darkening of radar background
            boxDrawQueue.setNextColor(RADAR_BG_COLOR, RADAR_OPACITY);
            boxDrawQueue.addVertices(ShapeUtils.createCircle(radarCenter.x,
                    radarCenter.y, radarRadius, RadarSettings.getVerticesPerCircle()));
            boxDrawQueue.finishShape(GL_TRIANGLE_FAN);

            final Color color = (player.isAlive() ? RADAR_FG_COLOR : RADAR_FG_DEAD_COLOR);

            // Outer circle
            boxDrawQueue.setNextColor(color, radarEdgeFade);
            boxDrawQueue.addVertices(ShapeUtils.createCircle(radarCenter.x,
                    radarCenter.y, radarRadius, RadarSettings.getVerticesPerCircle()));
            boxDrawQueue.finishShape(GL_LINE_LOOP);

            // Middle circle
            boxDrawQueue.setNextColor(color, radarMidFade);
            boxDrawQueue.addVertices(ShapeUtils.createCircle(radarCenter.x,
                    radarCenter.y, radarRadius * .66f, RadarSettings.getVerticesPerCircle()));
            boxDrawQueue.finishShape(GL_LINE_LOOP);

            // Inner circle
            boxDrawQueue.setNextColor(color, radarCenterFade);
            boxDrawQueue.addVertices(ShapeUtils.createCircle(radarCenter.x,
                    radarCenter.y, radarRadius * .33f, RadarSettings.getVerticesPerCircle()));
            boxDrawQueue.finishShape(GL_LINE_LOOP);

            // Vertical line
            boxDrawQueue.setNextColor(color, radarEdgeFade);
            boxDrawQueue.addVertex(radarCenter.x, radarCenter.y - radarRadius);
            boxDrawQueue.setNextColor(color, radarCenterFade);
            boxDrawQueue.addVertex(radarCenter.x, radarCenter.y);
            boxDrawQueue.setNextColor(color, radarEdgeFade);
            boxDrawQueue.addVertex(radarCenter.x, radarCenter.y + radarRadius);
            boxDrawQueue.finishShape(GL_LINE_STRIP);

            // Horizontal line
            boxDrawQueue.setNextColor(color, radarEdgeFade);
            boxDrawQueue.addVertex(radarCenter.x - radarRadius, radarCenter.y);
            boxDrawQueue.setNextColor(color, radarCenterFade);
            boxDrawQueue.addVertex(radarCenter.x, radarCenter.y);
            boxDrawQueue.setNextColor(color, radarEdgeFade);
            boxDrawQueue.addVertex(radarCenter.x + radarRadius, radarCenter.y);
            boxDrawQueue.finishShape(GL_LINE_STRIP);

            // Border lines
            if (SHOW_BORDER_LINES)
            {
                boxDrawQueue.setNextColor(color,
                        (REVERSE_FADE ? radarEdgeFade : radarCenterFade) * 1.25f);
                boxDrawQueue.addVertex(radarCenter.x + (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                boxDrawQueue.setNextColor(color,
                        (REVERSE_FADE ? radarCenterFade : radarEdgeFade) * 1.25f);
                boxDrawQueue.addVertex(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                boxDrawQueue.setNextColor(color,
                        (REVERSE_FADE ? radarEdgeFade : radarCenterFade) * 1.25f);
                boxDrawQueue.addVertex(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y - (radarRadius * 1.1f));
                boxDrawQueue.finishShape(GL_LINE_STRIP);
            }

            boxDrawQueue.finish();
        }

        // The zoom lines are updated regularly, however
        if (isUpdateFrame)
        {
            zoomDrawQueue.clear();
            zoomDrawQueue.setNextColor(Color.WHITE, radar.getRadarAlpha() * .66f);

            // Calculate position and size of zoom level notification lines
            final float zoomLinePos = radarRadius / radar.getCurrentZoomLevel();
            final float zoomLineSize = (radarRadius / 15f)
                    / (.75f + radar.getCurrentZoomLevel() / 2f);

            // Left line
            zoomDrawQueue.addVertex(radarCenter.x - zoomLinePos,
                    radarCenter.y - zoomLineSize);
            zoomDrawQueue.addVertex(radarCenter.x - zoomLinePos,
                    radarCenter.y + zoomLineSize);

            // Right line
            zoomDrawQueue.addVertex(radarCenter.x + zoomLinePos,
                    radarCenter.y - zoomLineSize);
            zoomDrawQueue.addVertex(radarCenter.x + zoomLinePos,
                    radarCenter.y + zoomLineSize);

            // Upper line
            zoomDrawQueue.addVertex(radarCenter.x - zoomLineSize,
                    radarCenter.y + zoomLinePos);
            zoomDrawQueue.addVertex(radarCenter.x + zoomLineSize,
                    radarCenter.y + zoomLinePos);

            // Lower line
            zoomDrawQueue.addVertex(radarCenter.x - zoomLineSize,
                    radarCenter.y - zoomLinePos);
            zoomDrawQueue.addVertex(radarCenter.x + zoomLineSize,
                    radarCenter.y - zoomLinePos);
            zoomDrawQueue.finishShape(GL_LINES);
            zoomDrawQueue.finish();
        }

        // Draw cached data
        glEnable(GL_LINE_SMOOTH);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glLineWidth(1f);
        boxDrawQueue.draw();
        zoomDrawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_LINE_SMOOTH);
    }
}

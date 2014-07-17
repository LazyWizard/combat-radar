package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class RadarBoxRenderer implements CombatRenderer
{
    private static boolean SHOW_BORDER_LINES;
    private static Color RADAR_BG_COLOR, RADAR_FG_COLOR, RADAR_FG_DEAD_COLOR;
    private static float RADAR_OPACITY, RADAR_EDGE_ALPHA;
    // Radar OpenGL buffers/display lists
    private static int RADAR_BOX_DISPLAY_LIST_ID = -123;
    private boolean wasHulkLastFrame = false;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_BORDER_LINES = settings.getBoolean("showBorderLines");

        // Foreground settings (match vanilla HUD)
        RADAR_FG_COLOR = Global.getSettings().getColor("textFriendColor");
        RADAR_FG_DEAD_COLOR = Global.getSettings().getColor("textNeutralColor");

        // Background settings
        settings = settings.getJSONObject("radarBoxRenderer");
        RADAR_BG_COLOR = JSONUtils.toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        RADAR_EDGE_ALPHA = (float) settings.getDouble("radarEdgeAlpha");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
        wasHulkLastFrame = !radar.getPlayer().isHulk();
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();
        float radarAlpha = radar.getRadarAlpha();

        // Cache OpenGL commands for faster execution
        if (player.isHulk() == wasHulkLastFrame)
        {
            glCallList(RADAR_BOX_DISPLAY_LIST_ID);
        }
        else
        {
            wasHulkLastFrame = player.isHulk();

            // Delete old display list, if existant
            if (RADAR_BOX_DISPLAY_LIST_ID >= 0)
            {
                Global.getLogger(RadarBoxRenderer.class).log(Level.DEBUG,
                        "Deleting old list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
                glDeleteLists(RADAR_BOX_DISPLAY_LIST_ID, 1);
            }

            float radarMidFade = (radarAlpha + RADAR_EDGE_ALPHA) / 2f;

            // Generate new display list
            RADAR_BOX_DISPLAY_LIST_ID = glGenLists(1);
            Global.getLogger(RadarBoxRenderer.class).log(Level.DEBUG,
                    "Creating new list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
            glNewList(RADAR_BOX_DISPLAY_LIST_ID, GL_COMPILE);
            glLineWidth(1f);

            // Slight darkening of radar background
            glColor(RADAR_BG_COLOR, RADAR_OPACITY, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 72, true);

            Color color = (player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR);

            // Outer circle
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 72, false);

            // Middle circle
            glColor(color, radarAlpha * radarMidFade, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .66f, 54, false);

            // Inner circle
            glColor(color, radarAlpha, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .33f, 36, false);

            glBegin(GL_LINES);
            // Left line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            glVertex2f(radarCenter.x - radarRadius, radarCenter.y);

            // Right line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            glVertex2f(radarCenter.x + radarRadius, radarCenter.y);

            // Upper line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            glVertex2f(radarCenter.x, radarCenter.y + radarRadius);

            // Lower line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            glVertex2f(radarCenter.x, radarCenter.y - radarRadius);
            glEnd();

            // Border lines
            if (SHOW_BORDER_LINES)
            {
                glLineWidth(1.5f);
                glBegin(GL_LINE_STRIP);
                glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
                glVertex2f(radarCenter.x + (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(color, radarAlpha, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y - (radarRadius * 1.1f));
                glEnd();
            }

            glEndList();
        }

        float zoomLinePos = radarRadius / radar.getCurrentZoomLevel();
        float zoomLineSize = (radarRadius / 15f)
                / (.75f + radar.getCurrentZoomLevel() / 2f);

        // Show current zoom level
        glColor(Color.WHITE, radar.getRadarAlpha() * .66f, false);
        glBegin(GL_LINES);
        // Left line
        glVertex2f(radarCenter.x - zoomLinePos, radarCenter.y - zoomLineSize);
        glVertex2f(radarCenter.x - zoomLinePos, radarCenter.y + zoomLineSize);

        // Right line
        glVertex2f(radarCenter.x + zoomLinePos, radarCenter.y - zoomLineSize);
        glVertex2f(radarCenter.x + zoomLinePos, radarCenter.y + zoomLineSize);

        // Upper line
        glVertex2f(radarCenter.x - zoomLineSize, radarCenter.y + zoomLinePos);
        glVertex2f(radarCenter.x + zoomLineSize, radarCenter.y + zoomLinePos);

        // Lower line
        glVertex2f(radarCenter.x - zoomLineSize, radarCenter.y - zoomLinePos);
        glVertex2f(radarCenter.x + zoomLineSize, radarCenter.y - zoomLinePos);
        glEnd();
    }
}

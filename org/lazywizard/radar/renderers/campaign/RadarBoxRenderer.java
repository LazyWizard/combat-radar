package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// TODO: Update to use isUpdateFrame
public class RadarBoxRenderer implements CampaignRenderer
{
    private static Color RADAR_BG_COLOR, RADAR_FG_COLOR;
    private static float RADAR_OPACITY, RADAR_EDGE_ALPHA;
    // Radar OpenGL buffers/display lists
    private static int RADAR_BOX_DISPLAY_LIST_ID = -123;
    private boolean firstFrame = true;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        // Foreground settings (match vanilla HUD)
        // TODO: switch with campaign UI color ID
        RADAR_FG_COLOR = Global.getSettings().getColor("tripadGridColor");

        // Background settings
        settings = settings.getJSONObject("radarBoxRenderer");
        RADAR_BG_COLOR = JSONUtils.toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        RADAR_EDGE_ALPHA = (float) settings.getDouble("radarEdgeAlpha");
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;
        firstFrame = true;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        Vector2f radarCenter = radar.getRenderCenter();
        float radarRadius = radar.getRenderRadius();
        float radarAlpha = radar.getRadarAlpha();

        // Cache OpenGL commands for faster execution
        if (firstFrame)
        {
            firstFrame = false;

            // Delete old display list, if existant
            if (RADAR_BOX_DISPLAY_LIST_ID >= 0)
            {
                //Global.getLogger(RadarBoxRenderer.class).log(Level.DEBUG,
                //        "Deleting old list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
                glDeleteLists(RADAR_BOX_DISPLAY_LIST_ID, 1);
            }

            float radarMidFade = (radarAlpha + RADAR_EDGE_ALPHA) / 2f;

            // Generate new display list
            RADAR_BOX_DISPLAY_LIST_ID = glGenLists(1);
            //Global.getLogger(RadarBoxRenderer.class).log(Level.DEBUG,
            //        "Creating new list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
            glNewList(RADAR_BOX_DISPLAY_LIST_ID, GL_COMPILE);
            glEnable(GL_LINE_SMOOTH);
            glLineWidth(1f);

            // Slight darkening of radar background
            glColor(RADAR_BG_COLOR, RADAR_OPACITY, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 144, true);

            Color color = RADAR_FG_COLOR;

            // Outer circle
            glColor(color, radarAlpha * RADAR_EDGE_ALPHA, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 144, false);

            // Middle circle
            glColor(color, radarAlpha * radarMidFade, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .66f, 108, false);

            // Inner circle
            glColor(color, radarAlpha, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .33f, 64, false);

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

            glDisable(GL_LINE_SMOOTH);
            glEndList();
        }

        // Call cached OpenGL commands
        glCallList(RADAR_BOX_DISPLAY_LIST_ID);

        // Calculate position and size of zoom level notification lines
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

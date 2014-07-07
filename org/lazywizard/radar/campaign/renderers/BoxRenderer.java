package org.lazywizard.radar.campaign.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import java.awt.Color;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.lazylib.opengl.DrawUtils;
//import org.lazywizard.radar.campaign.CampaignRadar;
//import org.lazywizard.radar.campaign.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class BoxRenderer{}
/* implements CampaignRenderer
{
    private static Color RADAR_BG_COLOR, RADAR_FG_COLOR;
    private static float RADAR_OPACITY, RADAR_FADE;
    private static boolean SHOW_BORDER;
    // Radar OpenGL buffers/display lists
    private static int RADAR_BOX_DISPLAY_LIST_ID = -123;
    private static boolean isInitialized = false;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                "Should reload settings now.");

        // Foreground settings
        RADAR_FG_COLOR = Global.getSettings().getColor("textFriendColor");
        // Background settings
        RADAR_BG_COLOR = JSONUtils.toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_FADE = (float) settings.getDouble("radarEdgeFadeAmount");
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        // Render settings
        SHOW_BORDER = settings.getBoolean("showBorderLines");

        isInitialized = false;
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;
    }

    @Override
    public void render(CampaignFleetAPI player, float amount)
    {
        // Cache OpenGL commands for faster execution
        if (isInitialized)
        {
            glCallList(RADAR_BOX_DISPLAY_LIST_ID);
        }
        else
        {
            isInitialized = true;

            // Delete old display list, if existant
            if (RADAR_BOX_DISPLAY_LIST_ID >= 0)
            {
                Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                        "Deleting old list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
                glDeleteLists(RADAR_BOX_DISPLAY_LIST_ID, 1);
            }

            Vector2f radarCenter = radar.getRenderCenter();
            float radarRadius = radar.getRenderRadius();
            float radarAlpha = radar.getRadarAlpha(),
                    radarMidFade = (radarAlpha + RADAR_FADE) / 2f;

            // Generate new display list
            RADAR_BOX_DISPLAY_LIST_ID = glGenLists(1);
            Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                    "Creating new list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
            glNewList(RADAR_BOX_DISPLAY_LIST_ID, GL_COMPILE);
            glLineWidth(1f);

            // Slight darkening of radar background
            glColor(RADAR_BG_COLOR, RADAR_OPACITY, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 72, true);

            // Outer circle
            glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 72, false);

            // Middle circle
            glColor(RADAR_FG_COLOR, radarAlpha * radarMidFade, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .66f, 54, false);

            // Inner circle
            glColor(RADAR_FG_COLOR, radarAlpha, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius * .33f, 36, false);

            glBegin(GL_LINES);
            // Left line
            glColor(RADAR_FG_COLOR, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
            glVertex2f(radarCenter.x - radarRadius, radarCenter.y);

            // Right line
            glColor(RADAR_FG_COLOR, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
            glVertex2f(radarCenter.x + radarRadius, radarCenter.y);

            // Upper line
            glColor(RADAR_FG_COLOR, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
            glVertex2f(radarCenter.x, radarCenter.y + radarRadius);

            // Lower line
            glColor(RADAR_FG_COLOR, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
            glVertex2f(radarCenter.x, radarCenter.y - radarRadius);
            glEnd();

            // Border lines
            if (SHOW_BORDER)
            {
                glLineWidth(1.5f);
                glBegin(GL_LINE_STRIP);
                glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
                glVertex2f(radarCenter.x + (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(RADAR_FG_COLOR, radarAlpha, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(RADAR_FG_COLOR, radarAlpha * RADAR_FADE, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y - (radarRadius * 1.1f));
                glEnd();
            }

            glEndList();
        }
    }
}
*/
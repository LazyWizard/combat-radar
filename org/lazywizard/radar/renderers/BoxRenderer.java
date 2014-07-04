package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.BaseRenderer;
import org.lazywizard.radar.RadarInfo;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class BoxRenderer implements BaseRenderer
{
    // TODO: Make these loaded through reloadSettings!
    private static Color RADAR_BG_COLOR;
    private static float RADAR_OPACITY;
    private static Color RADAR_FG_COLOR, RADAR_FG_DEAD_COLOR;
    private static boolean SHOW_BORDER;
    // Radar OpenGL buffers/display lists
    private static int RADAR_BOX_DISPLAY_LIST_ID = -123;
    private boolean wasHulkLastFrame = false;
    private RadarInfo radar;

    @Override
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException
    {
        Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                "Should reload settings now.");

        // Foreground settings
        RADAR_FG_COLOR = Global.getSettings().getColor("textFriendColor");
        RADAR_FG_DEAD_COLOR = Global.getSettings().getColor("textNeutralColor");
        // Background settings
        RADAR_BG_COLOR = JSONUtils.toColor(settings.getJSONArray("radarBackgroundColor"));
        RADAR_OPACITY = (float) settings.getDouble("radarBackgroundAlpha");
        // Render settings
        SHOW_BORDER = settings.getBoolean("showBorderLines");
    }

    @Override
    public void init(RadarInfo radar)
    {
        this.radar = radar;
        wasHulkLastFrame = !radar.getPlayer().isHulk();
    }

    @Override
    public void render(float amount)
    {
        ShipAPI player = radar.getPlayer();
        if (player.isHulk() == wasHulkLastFrame)
        {
            // Cache OpenGL commands for faster execution
            glCallList(RADAR_BOX_DISPLAY_LIST_ID);
        }
        else
        {
            wasHulkLastFrame = player.isHulk();

            // Delete old display list, if existant
            if (RADAR_BOX_DISPLAY_LIST_ID >= 0)
            {
                Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                        "Deleting old list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
                glDeleteLists(RADAR_BOX_DISPLAY_LIST_ID, 1);
            }

            Vector2f radarCenter = radar.getRenderCenter();
            float radarRadius = radar.getRenderRadius();
            float radarAlpha = radar.getCenterAlpha(),
                    radarFade = radar.getEdgeAlpha(),
                    radarMidFade = (radarAlpha + radarFade) / 2f;

            // Generate new display list
            RADAR_BOX_DISPLAY_LIST_ID = glGenLists(1);
            Global.getLogger(BoxRenderer.class).log(Level.DEBUG,
                    "Creating new list with ID " + RADAR_BOX_DISPLAY_LIST_ID);
            glNewList(RADAR_BOX_DISPLAY_LIST_ID, GL_COMPILE);
            glLineWidth(1f);

            // Slight darkening of radar background
            glColor(RADAR_BG_COLOR, RADAR_OPACITY, false);
            DrawUtils.drawCircle(radarCenter.x, radarCenter.y, radarRadius, 72, true);

            Color color = (player.isHulk() ? RADAR_FG_DEAD_COLOR : RADAR_FG_COLOR);

            // Outer circle
            glColor(color, radarAlpha * radarFade, false);
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
            glColor(color, radarAlpha * radarFade, false);
            glVertex2f(radarCenter.x - radarRadius, radarCenter.y);

            // Right line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * radarFade, false);
            glVertex2f(radarCenter.x + radarRadius, radarCenter.y);

            // Upper line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * radarFade, false);
            glVertex2f(radarCenter.x, radarCenter.y + radarRadius);

            // Lower line
            glColor(color, radarAlpha, false);
            glVertex2f(radarCenter.x, radarCenter.y);
            glColor(color, radarAlpha * radarFade, false);
            glVertex2f(radarCenter.x, radarCenter.y - radarRadius);
            glEnd();

            // Border lines
            if (SHOW_BORDER)
            {
                glLineWidth(1.5f);
                glBegin(GL_LINE_STRIP);
                glColor(color, radarAlpha * radarFade, false);
                glVertex2f(radarCenter.x + (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(color, radarAlpha, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y + (radarRadius * 1.1f));
                glColor(color, radarAlpha * radarFade, false);
                glVertex2f(radarCenter.x - (radarRadius * 1.1f),
                        radarCenter.y - (radarRadius * 1.1f));
                glEnd();
            }

            glEndList();
        }
    }
}

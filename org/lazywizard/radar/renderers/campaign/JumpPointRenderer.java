package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

// TODO: Update to use isUpdateFrame
public class JumpPointRenderer implements CampaignRenderer
{
    private static boolean SHOW_JUMP_POINTS;
    private static String JUMP_POINT_ICON;
    private static Color JUMP_POINT_COLOR;
    private SpriteAPI icon;
    private CampaignRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_JUMP_POINTS = settings.getBoolean("showJumpPoints");

        settings = settings.getJSONObject("jumpPointRenderer");
        JUMP_POINT_ICON = settings.optString("jumpPointIcon", null);
        JUMP_POINT_COLOR = JSONUtils.toColor(settings.getJSONArray("jumpPointColor"));
    }

    @Override
    public void init(CampaignRadar radar)
    {
        this.radar = radar;

        if (SHOW_JUMP_POINTS)
        {
            icon = Global.getSettings().getSprite("radar", JUMP_POINT_ICON);
        }
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_JUMP_POINTS)
        {
            List<JumpPointAPI> jumpPoints = radar.filterVisible(
                    player.getContainingLocation().getEntities(JumpPointAPI.class), 1_000);
            if (!jumpPoints.isEmpty())
            {
                float angle = (System.currentTimeMillis() / 20) % 360;

                radar.enableStencilTest();
                glEnable(GL_TEXTURE_2D);

                icon.setColor(JUMP_POINT_COLOR);
                icon.setAlphaMult(radar.getContactAlpha());
                for (JumpPointAPI jumpPoint : jumpPoints)
                {
                    // Resize and draw jump point on radar
                    float[] center = radar.getRawPointOnRadar(jumpPoint.getLocation());
                    float size = jumpPoint.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    size *= 2f; // Scale upwards for better visibility
                    icon.setSize(size, size);
                    icon.setAngle(angle);
                    icon.renderAtCenter(center[0], center[1]);

                    // Render again for better visibility
                    size = (player.getContainingLocation().isHyperspace()
                            ? (size / 2f) : (size * 2f));
                    icon.setSize(size, size);
                    icon.setAngle(360f - angle);
                    icon.renderAtCenter(center[0], center[1]);
                }

                glDisable(GL_TEXTURE_2D);
                radar.disableStencilTest();
            }
        }
    }
}

package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import static org.lwjgl.opengl.GL11.*;

public class JumpPointRenderer implements CampaignRenderer
{
    private static boolean SHOW_JUMP_POINTS;
    private static int MAX_JUMP_POINTS_SHOWN;
    private static String JUMP_POINT_ICON;
    private static Color JUMP_POINT_COLOR;
    private float angle;
    private SpriteAPI icon;
    private List<JumpPointIcon> toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_JUMP_POINTS = settings.getBoolean("showJumpPoints");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("jumpPointRenderer");
        JUMP_POINT_ICON = settings.optString("jumpPointIcon", null);
        JUMP_POINT_COLOR = JSONUtils.toColor(settings.getJSONArray("jumpPointColor"));
        MAX_JUMP_POINTS_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
    {
        if (!SHOW_JUMP_POINTS)
        {
            return;
        }

        this.radar = radar;
        icon = Global.getSettings().getSprite("radar", JUMP_POINT_ICON);
        icon.setColor(JUMP_POINT_COLOR);
        toDraw = new ArrayList<>();
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_JUMP_POINTS)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            List<JumpPointAPI> jumpPoints = radar.filterVisible(
                    player.getContainingLocation().getEntities(JumpPointAPI.class), MAX_JUMP_POINTS_SHOWN);
            if (!jumpPoints.isEmpty())
            {
                angle = (System.currentTimeMillis() / 20) % 360;
                for (JumpPointAPI jumpPoint : jumpPoints)
                {
                    // Resize and draw jump point on radar
                    float[] center = radar.getRawPointOnRadar(jumpPoint.getLocation());
                    float size = jumpPoint.getRadius() * 2f * radar.getCurrentPixelsPerSU();
                    // Scale upwards for better visibility
                    size *= (player.getContainingLocation().isHyperspace() ? 2f : 4f);
                    toDraw.add(new JumpPointIcon(center[0], center[1], size));
                }
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        icon.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all relays
        glEnable(GL_TEXTURE_2D);
        for (JumpPointIcon jIcon : toDraw)
        {
            jIcon.render(angle);
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class JumpPointIcon
    {
        private final float x, y, size;

        private JumpPointIcon(float x, float y, float size)
        {
            this.x = x;
            this.y = y;
            this.size = size;
        }

        private void render(float angle)
        {
            icon.setSize(size, size);
            icon.setAngle(angle);
            icon.renderAtCenter(x, y);

            icon.setSize(size / 2f, size / 2f);
            icon.setAngle(360f - angle);
            icon.renderAtCenter(x, y);
        }
    }
}

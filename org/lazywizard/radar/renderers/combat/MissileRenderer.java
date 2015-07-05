package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class MissileRenderer implements CombatRenderer
{
    private static boolean SHOW_MISSILES, SHOW_MISSILE_LOCK_ICON;
    private static int MAX_MISSILES_SHOWN;
    private static Color MISSILE_LOCKED_COLOR;
    private static String MISSILE_ICON;
    private SpriteAPI icon;
    private Vector2f iconLocation;
    private boolean playerLock = false;
    private float highestThreatAlpha = 0f;
    private DrawQueue drawQueue;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_MISSILE_LOCK_ICON = settings.getBoolean("showMissileLockIcon");

        settings = settings.getJSONObject("missileRenderer");
        MAX_MISSILES_SHOWN = settings.optInt("maxShown", 1_000);
        MISSILE_LOCKED_COLOR = JSONUtils.toColor(settings.getJSONArray("lockedMissileColor"));
        MISSILE_ICON = settings.optString("missileLockIcon", null);
    }

    @Override
    public void init(CombatRadar radar)
    {
        if (!SHOW_MISSILES)
        {
            return;
        }

        this.radar = radar;
        drawQueue = new DrawQueue(MAX_MISSILES_SHOWN);

        if (SHOW_MISSILE_LOCK_ICON)
        {
            final Vector2f radarCenter = radar.getRenderCenter();
            final float radarRadius = radar.getRenderRadius();
            icon = Global.getSettings().getSprite("radar", MISSILE_ICON);
            iconLocation = new Vector2f(radarCenter.x - (radarRadius * 0.9f),
                    radarCenter.y + (radarRadius * 0.9f));
        }
    }

    private void generateMaps(ShipAPI player, List<MissileAPI> missiles)
    {
        drawQueue.clear();
        for (MissileAPI missile : missiles)
        {
            // Calculate vertices
            float[] radarLoc = radar.getRawPointOnRadar(missile.getLocation());

            // Calculate color
            float alphaMod = Math.min(1f, Math.max(0.3f,
                    (missile.getDamageAmount() + (missile.getEmpAmount() / 2f)) / 750f));
            alphaMod *= radar.getContactAlpha() * (missile.isFading() ? .5f : 1f);

            // Burnt-out missiles count as hostile
            Color color;
            if (missile.isFizzling())
            {
                color = radar.getEnemyContactColor();
            }
            // Enemy missiles
            else if (missile.getOwner() + player.getOwner() == 1)
            {
                // Color missiles locked onto us differently
                MissileAIPlugin ai = missile.getMissileAI();
                if (ai != null && ai instanceof GuidedMissileAI
                        && player == ((GuidedMissileAI) ai).getTarget())
                {
                    playerLock = true;
                    highestThreatAlpha = alphaMod;
                    color = MISSILE_LOCKED_COLOR;
                }
                else
                {
                    color = radar.getEnemyContactColor();
                }
            }
            // Allied missiles
            else
            {
                color = radar.getFriendlyContactColor();
            }

            drawQueue.setNextColor(color, alphaMod);
            drawQueue.addVertices(radarLoc);
        }

        drawQueue.finishShape(GL_POINTS);
        drawQueue.finish();
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_MISSILES || !player.isAlive())
        {
            return;
        }

        // Update frame = regenerate all vertex data
        if (isUpdateFrame)
        {
            playerLock = false;
            highestThreatAlpha = 0f;
            generateMaps(player, radar.filterVisible(Global.getCombatEngine().getMissiles(), MAX_MISSILES_SHOWN));
        }

        // Don't draw if there's nothing to render!
        if (drawQueue.isEmpty())
        {
            return;
        }

        radar.enableStencilTest();

        // Draw missiles
        glPointSize(2f * radar.getCurrentZoomLevel());
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);

        radar.disableStencilTest();

        if (SHOW_MISSILE_LOCK_ICON && playerLock)
        {
            glEnable(GL_TEXTURE_2D);
            icon.setAlphaMult(radar.getRadarAlpha() * highestThreatAlpha);
            icon.setColor(MISSILE_LOCKED_COLOR);
            icon.renderAtCenter(iconLocation.x, iconLocation.y);
            glDisable(GL_TEXTURE_2D);
        }
    }
}

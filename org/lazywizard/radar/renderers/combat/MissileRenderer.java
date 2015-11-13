package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class MissileRenderer implements CombatRenderer
{
    private static boolean SHOW_MISSILES, SHOW_MISSILE_LOCK_ICON;
    private static int MAX_MISSILES_SHOWN;
    private static Color MISSILE_LOCKED_COLOR;
    private static String MISSILE_ICON, FLARE_ICON, MISSILE_LOCK_ICON;
    private static float DEBUG_SIZE_VALUE;
    private SpriteAPI missileIcon, flareIcon, lockIcon;
    private Vector2f lockIconLocation;
    private boolean playerLock = false;
    private float highestThreatAlpha = 0f, hScale;
    private List<MissileIcon> toDraw;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_MISSILE_LOCK_ICON = settings.getBoolean("showMissileLockIcon");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("missileRenderer");
        MAX_MISSILES_SHOWN = settings.optInt("maxShown", 1_000);
        MISSILE_LOCKED_COLOR = JSONUtils.toColor(settings.getJSONArray("lockedMissileColor"));
        MISSILE_ICON = settings.getString("missileIcon");
        FLARE_ICON = settings.getString("flareIcon");
        MISSILE_LOCK_ICON = settings.optString("missileLockIcon", null);
        DEBUG_SIZE_VALUE = (float) settings.getDouble("missileSize");
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_MISSILES)
        {
            return;
        }

        this.radar = radar;
        toDraw = new ArrayList<>();
        missileIcon = Global.getSettings().getSprite("radar", MISSILE_ICON);
        flareIcon = Global.getSettings().getSprite("radar", FLARE_ICON);
        hScale = missileIcon.getWidth() / missileIcon.getHeight();

        if (SHOW_MISSILE_LOCK_ICON)
        {
            final Vector2f radarCenter = radar.getRenderCenter();
            final float radarRadius = radar.getRenderRadius();
            lockIcon = Global.getSettings().getSprite("radar", MISSILE_LOCK_ICON);
            lockIcon.setColor(MISSILE_LOCKED_COLOR);
            lockIconLocation = new Vector2f(radarCenter.x - (radarRadius * 0.9f),
                    radarCenter.y + (radarRadius * 0.9f));
        }
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
            toDraw.clear();
            final List<MissileAPI> missiles = radar.filterVisible(
                    Global.getCombatEngine().getMissiles(), MAX_MISSILES_SHOWN);
            for (MissileAPI missile : missiles)
            {
                // Calculate vertices
                float[] radarLoc = radar.getRawPointOnRadar(missile.getLocation());

                // Calculate color
                float alphaMod = Math.min(1f, Math.max(0.45f,
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

                toDraw.add(new MissileIcon(radarLoc[0], radarLoc[1],
                        alphaMod, missile.getFacing(), missile.isFlare(), color));
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        final float drawSize = radar.getRenderRadius() * DEBUG_SIZE_VALUE;

        missileIcon.setAlphaMult(radar.getContactAlpha());
        flareIcon.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all asteroids
        glEnable(GL_TEXTURE_2D);
        for (MissileIcon mIcon : toDraw)
        {
            mIcon.render(drawSize);
        }

        if (SHOW_MISSILE_LOCK_ICON && playerLock)
        {
            lockIcon.setAlphaMult(radar.getRadarAlpha() * highestThreatAlpha);
            lockIcon.renderAtCenter(lockIconLocation.x, lockIconLocation.y);
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class MissileIcon
    {
        private final float x, y, alpha, facing;
        private final boolean isFlare;
        private final Color color;

        private MissileIcon(float x, float y, float alpha, float facing,
                boolean isFlare, Color color)
        {
            this.x = x;
            this.y = y;
            this.alpha = alpha;
            this.facing = facing;
            this.isFlare = isFlare;
            this.color = color;
        }

        private void render(float size)
        {
            final SpriteAPI icon = (isFlare ? flareIcon : missileIcon);
            icon.setSize(size * hScale, size);
            icon.setAngle(facing - 90f);
            icon.setColor(color);
            icon.setAlphaMult(alpha);
            icon.renderAtCenter(x, y);
        }
    }
}

package org.lazywizard.radar.combat.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.Color;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

public class MissileRenderer implements CombatRenderer
{
    private static boolean SHOW_MISSILES;
    private static boolean SHOW_MISSILE_LOCK_ICON;
    private static String MISSILE_ICON;
    private static Color MISSILE_LOCKED_COLOR;
    private SpriteAPI icon;
    private Vector2f iconLocation;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_MISSILE_LOCK_ICON = settings.getBoolean("showMissileLockIcon");
        MISSILE_LOCKED_COLOR = JSONUtils.toColor(settings.getJSONArray("lockedMissileColor"));
        MISSILE_ICON = settings.optString("missileLockIcon", null);
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

        if (SHOW_MISSILE_LOCK_ICON)
        {
            Vector2f radarCenter = radar.getRenderCenter();
            float radarRadius = radar.getRenderRadius();
            icon = Global.getSettings().getSprite("radar", MISSILE_ICON);
            iconLocation = new Vector2f(radarCenter.x - (radarRadius * 0.9f),
                radarCenter.y + (radarRadius * 0.9f));
        }
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_MISSILES && !player.isHulk())
        {
            List<? extends CombatEntityAPI> missiles = radar.filterVisible(
                    Global.getCombatEngine().getMissiles());
            if (!missiles.isEmpty())
            {
                boolean playerLock = false;
                float highestThreatAlpha = 0f;

                glPointSize(2f);
                glBegin(GL_POINTS);
                for (CombatEntityAPI entity : missiles)
                {
                    MissileAPI missile = (MissileAPI) entity;
                    // TODO: Add a setting for missile damage alpha mod
                    float alphaMod = Math.min(1f, Math.max(0.3f,
                            missile.getDamageAmount() / 750f));
                    alphaMod *= (missile.isFading() ? .5f : 1f);

                    // Burnt-out missiles count as hostile
                    if (missile.isFizzling())
                    {
                        glColor(radar.getEnemyContactColor(),
                                radar.getContactAlpha() * alphaMod, false);
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
                            glColor(MISSILE_LOCKED_COLOR,
                                    radar.getContactAlpha() * alphaMod, false);
                        }
                        else
                        {
                            glColor(radar.getEnemyContactColor(),
                                    radar.getContactAlpha() * alphaMod, false);
                        }
                    }

                    // Allied missiles
                    else
                    {
                        glColor(radar.getFriendlyContactColor(),
                                radar.getContactAlpha() * alphaMod, false);
                    }

                    Vector2f radarLoc = radar.getPointOnRadar(missile.getLocation());
                    glVertex2f(radarLoc.x, radarLoc.y);
                }
                glEnd();

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
    }
}
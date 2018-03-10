package org.lazywizard.radar.renderers.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.SpriteBatch;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

public class MissileRenderer implements CombatRenderer
{
    private static final String EXCLUDED_MISSILE_LOCKS_PATH
            = "data/config/radar/excluded_missile_locks.csv";
    private static boolean SHOW_MISSILES, SHOW_MISSILE_LOCK_ICON;
    private static int MAX_MISSILES_SHOWN;
    private static Set<String> EXCLUDED_MISSILE_LOCKS;
    private static Color FRIENDLY_COLOR, ENEMY_COLOR, MISSILE_LOCKED_COLOR;
    private static String MISSILE_ICON, FLARE_ICON, MISSILE_LOCK_ICON;
    private static float MISSILE_SIZE_MOD, FLARE_SIZE_MOD;
    private SpriteBatch missileToDraw, flareToDraw;
    private SpriteAPI lockIcon;
    private Vector2f lockIconLocation;
    private boolean playerLock = false;
    private float missileSize, flareSize, highestThreatAlpha = 0f;
    private CommonRadar<CombatEntityAPI> radar;

    private static Color optColor(JSONArray array, Color defaultColor) throws JSONException
    {
        if (array != null)
        {
            return JSONUtils.toColor(array);
        }

        return defaultColor;
    }

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_MISSILE_LOCK_ICON = settings.getBoolean("showMissileLockIcon");

        // If missile-specific colors aren't set, default to using ship colors
        final Color friendlyShipColor = JSONUtils.toColor(settings.getJSONArray("friendlyColor")),
                enemyShipColor = JSONUtils.toColor(settings.getJSONArray("enemyColor"));

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("missileRenderer");
        MAX_MISSILES_SHOWN = settings.optInt("maxShown", 1_000);
        FRIENDLY_COLOR = optColor(settings.optJSONArray("friendlyMissileColor"), friendlyShipColor);
        ENEMY_COLOR = optColor(settings.optJSONArray("enemyMissileColor"), enemyShipColor);
        MISSILE_LOCKED_COLOR = JSONUtils.toColor(settings.getJSONArray("lockedMissileColor"));
        MISSILE_ICON = settings.getString("missileIcon");
        FLARE_ICON = settings.getString("flareIcon");
        MISSILE_LOCK_ICON = settings.getString("missileLockIcon");
        MISSILE_SIZE_MOD = (float) settings.getDouble("missileContactSize");
        FLARE_SIZE_MOD = (float) settings.getDouble("flareContactSize");

        reloadExcludedMissileLocks();
    }

    private void reloadExcludedMissileLocks() throws JSONException
    {
        final JSONArray csv;
        try
        {
            csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                    "missile projectile id", EXCLUDED_MISSILE_LOCKS_PATH, "lw_radar");
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Failed to load " + EXCLUDED_MISSILE_LOCKS_PATH + "!");
        }

        EXCLUDED_MISSILE_LOCKS = new HashSet<>();
        for (int x = 0; x < csv.length(); x++)
        {
            final JSONObject row = csv.getJSONObject(x);
            final String id = row.getString("missile projectile id");
            if (id.isEmpty())
            {
                continue;
            }

            EXCLUDED_MISSILE_LOCKS.add(id);
        }
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_MISSILES)
        {
            return;
        }

        this.radar = radar;

        missileToDraw = new SpriteBatch(Global.getSettings().getSprite("radar", MISSILE_ICON));
        missileSize = radar.getRenderRadius() * MISSILE_SIZE_MOD;

        flareToDraw = new SpriteBatch(Global.getSettings().getSprite("radar", FLARE_ICON));
        flareSize = radar.getRenderRadius() * FLARE_SIZE_MOD;

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

    private float getAlphaMod(MissileAPI missile)
    {
        float alphaMod = Math.min(1f, Math.max(0.45f,
                (missile.getDamageAmount() + (missile.getEmpAmount() / 2f)) / 750f));
        alphaMod *= radar.getContactAlpha() * (missile.isFading() ? .5f : 1f);
        return alphaMod;
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
            missileToDraw.clear();
            flareToDraw.clear();
            final List<MissileAPI> missiles = radar.filterVisible(
                    Global.getCombatEngine().getMissiles(), MAX_MISSILES_SHOWN);
            for (MissileAPI missile : missiles)
            {
                // Calculate vertices
                float[] radarLoc = radar.getRawPointOnRadar(missile.getLocation());

                // Calculate color
                Color color;
                float alphaMod = getAlphaMod(missile);
                // Burnt-out missiles count as hostile
                if (missile.isFizzling())
                {
                    color = ENEMY_COLOR;
                }
                // Enemy missiles
                else if (missile.getOwner() + player.getOwner() == 1)
                {
                    // Color missiles locked onto us differently
                    MissileAIPlugin ai = missile.getMissileAI();
                    if (ai instanceof GuidedMissileAI
                            && player == ((GuidedMissileAI) ai).getTarget()
                            && !EXCLUDED_MISSILE_LOCKS.contains(
                            missile.getProjectileSpecId()))
                    {
                        playerLock = true;
                        highestThreatAlpha = alphaMod;
                        color = MISSILE_LOCKED_COLOR;
                    }
                    else
                    {
                        color = ENEMY_COLOR;
                    }
                }
                // Allied missiles
                else
                {
                    color = FRIENDLY_COLOR;
                }

                if (missile.isFlare())
                {
                    flareToDraw.add(radarLoc[0], radarLoc[1], missile.getFacing(),
                            flareSize, color, alphaMod);
                }
                else
                {
                    missileToDraw.add(radarLoc[0], radarLoc[1], missile.getFacing(),
                            missileSize, color, alphaMod);
                }
            }

            missileToDraw.finish();
            flareToDraw.finish();
        }

        // Don't draw if there's nothing to render!
        if (missileToDraw.isEmpty() && flareToDraw.isEmpty())
        {
            return;
        }

        // Draw all missiles and flares
        radar.enableStencilTest();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        missileToDraw.draw();
        flareToDraw.draw();
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();

        if (SHOW_MISSILE_LOCK_ICON && playerLock)
        {
            lockIcon.setAlphaMult(radar.getRadarAlpha() * highestThreatAlpha);
            lockIcon.renderAtCenter(lockIconLocation.x, lockIconLocation.y);
        }

        glDisable(GL_TEXTURE_2D);
    }
}

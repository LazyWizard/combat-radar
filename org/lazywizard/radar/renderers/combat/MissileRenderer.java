package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.Iterator;
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
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
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
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MISSILES = settings.getBoolean("showMissiles");
        SHOW_MISSILE_LOCK_ICON = settings.getBoolean("showMissileLockIcon");

        settings = settings.getJSONObject("missileRenderer");
        MAX_MISSILES_SHOWN = settings.optInt("maxShown", 1000);
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
                    Global.getCombatEngine().getMissiles(), MAX_MISSILES_SHOWN);
            if (!missiles.isEmpty())
            {
                radar.enableStencilTest();

                boolean playerLock = false;
                float highestThreatAlpha = 0f;

                float[] vertices = new float[missiles.size() * 2];
                float[] colors = new float[missiles.size() * 4];
                Iterator<? extends CombatEntityAPI> iter = missiles.iterator();
                for (int v = 0, c = 0; v < missiles.size() * 2; v += 2, c += 4)
                {
                    MissileAPI missile = (MissileAPI) iter.next();

                    // Calculate vertices
                    Vector2f radarLoc = radar.getPointOnRadar(missile.getLocation());
                    vertices[v] = radarLoc.x;
                    vertices[v + 1] = radarLoc.y;

                    // Calculate color
                    Color color;
                    float alphaMod = Math.min(1f, Math.max(0.3f,
                            missile.getDamageAmount() / 750f));
                    alphaMod *= (missile.isFading() ? .5f : 1f);

                    // Burnt-out missiles count as hostile
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

                    colors[c] = color.getRed() / 255f;
                    colors[c + 1] = color.getGreen() / 255f;
                    colors[c + 2] = color.getBlue() / 255f;
                    colors[c + 3] = color.getAlpha() / 255f * alphaMod;
                }

                FloatBuffer vertexMap = BufferUtils.createFloatBuffer(vertices.length).put(vertices);
                vertexMap.flip();

                FloatBuffer colorMap = BufferUtils.createFloatBuffer(colors.length).put(colors);
                colorMap.flip();

                // Draw missiles
                glPointSize(2f * radar.getCurrentZoomLevel());
                glEnableClientState(GL_VERTEX_ARRAY);
                glEnableClientState(GL_COLOR_ARRAY);
                glVertexPointer(2, 0, vertexMap);
                glColorPointer(4, 0, colorMap);
                glDrawArrays(GL_POINTS, 0, vertices.length / 2);
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
    }
}

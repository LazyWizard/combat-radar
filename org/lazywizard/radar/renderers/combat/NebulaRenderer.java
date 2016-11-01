package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatNebulaAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.SpriteBatch;
import static org.lwjgl.opengl.GL11.*;

// TODO: Fix tiling issues
public class NebulaRenderer implements CombatRenderer
{
    private static boolean SHOW_NEBULAE;
    private static int MAX_NEBULAE_SHOWN;
    private static String NEBULA_ICON;
    private static Color NEBULA_COLOR;
    private SpriteBatch toDraw;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_NEBULAE = settings.getBoolean("showNebulae");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("nebulaRenderer");
        NEBULA_ICON = settings.getString("nebulaIcon");
        NEBULA_COLOR = JSONUtils.toColor(settings.getJSONArray("nebulaColor"));
        MAX_NEBULAE_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_NEBULAE)
        {
            return;
        }

        this.radar = radar;
        toDraw = new SpriteBatch(Global.getSettings().getSprite("radar", NEBULA_ICON));
    }

    // Hides tile pattern by rotating sprite by a predictable but non-uniform angle
    private static float getAngle(int cellX, int cellY)
    {
        if ((cellY & 1) == 0)
        {
            if ((cellX & 1) == 0)
            {
                return ((cellX + cellY) & 7) * 45f;
            }

            return ((cellX - cellY) & 15) * 22.5f;
        }

        if ((cellX & 2) == 0)
        {
            return ((cellY - cellX) & 15) * 22.25f;
        }

        return ((cellX + cellY) & 31) * 11.25f;
    }

    private void addNebula(CombatNebulaAPI nebula)
    {
        final float tileSize = nebula.getTileSizeInPixels(),
                halfTile = tileSize * 0.5f, tileRenderSize = tileSize * 2.4f;
        final CombatEngineAPI engine = Global.getCombatEngine();
        final float centerX = engine.getMapWidth() * 0.5f,
                centerY = engine.getMapHeight() * 0.5f;

        // Calculate radar locations for all visible nebula tiles
        for (int x = 0; x < nebula.getTilesWide(); x++)
        {
            for (int y = 0; y < nebula.getTilesHigh(); y++)
            {
                if (toDraw.size() >= MAX_NEBULAE_SHOWN)
                {
                    return;
                }

                if (!nebula.tileHasNebula(x, y))
                {
                    continue;
                }

                final float rawX = (tileSize * x) - centerX - halfTile,
                        rawY = (tileSize * y) - centerY - halfTile;
                if (radar.isPointOnRadar(rawX, rawY, tileRenderSize * 1.28f))
                {
                    final float[] coord = radar.getRawPointOnRadar(rawX, rawY);
                    final float angle = getAngle(x, y);
                    toDraw.add(coord[0], coord[1], angle, tileRenderSize
                            * radar.getCurrentPixelsPerSU(), NEBULA_COLOR, radar.getContactAlpha());
                }
            }
        }
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_NEBULAE)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();
            addNebula(Global.getCombatEngine().getNebula());
            toDraw.finish();
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        // Draw all nebulae
        radar.enableStencilTest();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        toDraw.draw();
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }
}

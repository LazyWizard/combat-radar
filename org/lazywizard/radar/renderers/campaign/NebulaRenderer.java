package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.SpriteBatch;
import org.lwjgl.util.vector.Vector2f;

// TODO: Split nebula and hyperspace rendering once storms are _efficiently_ trackable
// TODO: Use actual nebula/hyperspace map textures instead of radar's version
public class NebulaRenderer implements CampaignRenderer
{
    private static boolean SHOW_NEBULAE;
    private static int MAX_NEBULAE_SHOWN;
    private static String NEBULA_ICON;
    private static Color NEBULA_COLOR;
    private SpriteBatch toDraw;
    private CommonRadar<SectorEntityToken> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_NEBULAE = settings.getBoolean("showNebulae");

        settings = settings.getJSONObject("campaignRenderers")
                .getJSONObject("nebulaRenderer");
        NEBULA_ICON = settings.getString("nebulaIcon");
        NEBULA_COLOR = JSONUtils.toColor(settings.getJSONArray("nebulaColor"));
        MAX_NEBULAE_SHOWN = settings.optInt("maxShown", 1_000);
    }

    @Override
    public void init(CommonRadar<SectorEntityToken> radar)
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

    private void addNebula(CampaignTerrainAPI nebula)
    {
        if (toDraw.size() >= MAX_NEBULAE_SHOWN)
        {
            return;
        }

        if (!(nebula.getPlugin() instanceof BaseTiledTerrain))
        {
            return;
        }

        // These variables are used later to calculate cell positions
        final BaseTiledTerrain plugin = (BaseTiledTerrain) nebula.getPlugin();
        final Vector2f loc = plugin.getEntity().getLocation();
        final int[][] tiles = plugin.getTiles();
        final float tileSize = plugin.getTileSize(), halfTile = tileSize * 0.5f,
                tileRenderSize = plugin.getTileRenderSize(),
                locX = loc.x + halfTile, locY = loc.y + halfTile,
                llx = locX - (tiles.length * halfTile),
                lly = locY - (tiles[0].length * halfTile);

        // Detect all radar-visible hyperspace tiles
        for (int x = 0; x < tiles.length; x++)
        {
            for (int y = 0; y < tiles[0].length; y++)
            {
                if (toDraw.size() >= MAX_NEBULAE_SHOWN)
                {
                    return;
                }

                // Empty tile
                if (tiles[x][y] < 0)
                {
                    continue;
                }

                // Register each visible nebula cell to be rendered
                final float rawX = llx + (tileSize * x), rawY = lly + (tileSize * y);
                if (radar.isPointOnRadar(rawX, rawY, tileRenderSize * 1.2f))
                {
                    final float[] coord = radar.getRawPointOnRadar(rawX, rawY);
                    final float angle = getAngle(x, y);
                    toDraw.add(coord[0], coord[1], angle, tileRenderSize * 1.2f
                            * radar.getCurrentPixelsPerSU(), NEBULA_COLOR, radar.getContactAlpha());
                }
            }
        }
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_NEBULAE)
        {
            return;
        }

        if (isUpdateFrame)
        {
            toDraw.clear();

            final List<CampaignTerrainAPI> nebulae = new ArrayList<>();
            for (CampaignTerrainAPI terrain : player.getContainingLocation().getTerrainCopy())
            {
                if (Terrain.NEBULA.equals(terrain.getType())
                        || Terrain.HYPERSPACE.equals(terrain.getType()))
                {
                    nebulae.add(terrain);
                }
            }

            if (!nebulae.isEmpty())
            {
                for (CampaignTerrainAPI nebula : nebulae)
                {
                    addNebula(nebula);
                }
            }

            toDraw.finish();
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        // Draw all nebulae
        radar.enableStencilTest();
        toDraw.draw();
        radar.disableStencilTest();
    }
}

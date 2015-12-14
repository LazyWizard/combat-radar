package org.lazywizard.radar.renderers.campaign;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class NebulaRenderer implements CampaignRenderer
{
    private static boolean SHOW_NEBULAE;
    private static int MAX_NEBULAE_SHOWN;
    private static String NEBULA_ICON;
    private static Color NEBULA_COLOR;
    private SpriteAPI icon;
    private List<NebulaIcon> toDraw;
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
        icon = Global.getSettings().getSprite("radar", NEBULA_ICON);
        icon.setColor(NEBULA_COLOR);
        toDraw = new ArrayList<>();
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

        final BaseTiledTerrain plugin = (BaseTiledTerrain) nebula.getPlugin();
        final Vector2f loc = plugin.getEntity().getLocation();
        final int[][] tiles = plugin.getTiles();
        final float tileSize = plugin.getTileSize(), halfTile = tileSize/2f,
                tileRenderSize = plugin.getTileRenderSize(),
                locX = loc.x + halfTile, locY = loc.y + halfTile,
                llx = locX - (tiles.length * halfTile),
                lly = locY - (tiles[0].length * halfTile);

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

                final float rawX = llx + (tileSize * x), rawY = lly + (tileSize * y);
                if (radar.isPointOnRadar(rawX, rawY, tileRenderSize))
                {
                    final float[] coord = radar.getRawPointOnRadar(rawX, rawY);
                    toDraw.add(new NebulaIcon(coord, tileRenderSize * radar.getCurrentPixelsPerSU()));
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

            List<CampaignTerrainAPI> nebulae = new ArrayList<>();
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

                System.out.println("Found " + toDraw.size() + " nebulae nearby.");
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        icon.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all nebulae
        glEnable(GL_TEXTURE_2D);
        for (NebulaIcon nIcon : toDraw)
        {
            nIcon.render();
        }
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class NebulaIcon
    {
        private final float[] coord;
        private final float size;

        private NebulaIcon(float[] coord, float size)
        {
            this.coord = coord;
            this.size = size;
        }

        private void render()
        {
            icon.setSize(size, size);
            icon.renderAtCenter(coord[0], coord[1]);
        }
    }
}

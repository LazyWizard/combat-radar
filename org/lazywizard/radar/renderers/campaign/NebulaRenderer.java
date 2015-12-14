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
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// TODO: Split nebula and hyperspace rendering once storms are _efficiently_ trackable
public class NebulaRenderer implements CampaignRenderer
{
    private static boolean SHOW_NEBULAE;
    private static int MAX_NEBULAE_SHOWN;
    private static String NEBULA_ICON;
    private static Color NEBULA_COLOR;
    private SpriteAPI sprite;
    private List<NebulaCell> toDraw;
    private CommonRadar<SectorEntityToken> radar;
    private float lastSize = -1;

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
        sprite = Global.getSettings().getSprite("radar", NEBULA_ICON);
        sprite.setColor(NEBULA_COLOR);
        toDraw = new ArrayList<>();
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

            return ((cellX + cellY) & 15) * 22.5f;
        }

        return ((cellX + cellY) & 31) * 11.25f;
    }

    public static void main(String[] args)
    {
        for (int x = 0; x < 15; x++)
        {
            for (int y = 0; y < 15; y++)
            {
                System.out.println(x + "," + y + ": " + getAngle(x, y));
            }
        }
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
        final float tileSize = plugin.getTileSize(), halfTile = tileSize / 2f,
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
                    toDraw.add(new NebulaCell(coord[0], coord[1], angle,
                            tileRenderSize * 1.2f * radar.getCurrentPixelsPerSU()));
                }
            }
        }
    }

    // MUCH faster than calling SpriteAPI's render() each time (avoids a ton of bindTexture() calls)
    private void renderNebula(List<NebulaCell> toRender)
    {
        final float width = sprite.getWidth(), height = sprite.getHeight(), border = 0.001f,
                texWidth = sprite.getTextureWidth(), texHeight = sprite.getTextureHeight();

        sprite.bindTexture();
        glColor(sprite.getColor(), sprite.getAlphaMult(), false);
        for (NebulaCell nIcon : toRender)
        {
            sprite.setSize(nIcon.size, nIcon.size);
            sprite.setAngle(nIcon.angle);

            final float x = nIcon.x - (width / 2f), y = nIcon.y - (height / 2f);

            glPushMatrix();
            glTranslatef(x + (width / 2f), y + (height / 2f), 0f);
            glRotatef(nIcon.angle, 0f, 0f, 1f);
            glTranslatef(-width / 2f, -height / 2f, 0f);

            glBegin(GL_QUADS);
            glTexCoord2f(border, border);
            glVertex2f(0f, 0f);
            glTexCoord2f(border, texHeight - border);
            glVertex2f(0f, height);
            glTexCoord2f(texWidth - border, texHeight - border);
            glVertex2f(width, height);
            glTexCoord2f(texWidth - border, border);
            glVertex2f(width, 0f);
            glEnd();
            glPopMatrix();
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

                //System.out.println("Found " + toDraw.size() + " nebulae nearby.");
            }
        }

        // Don't draw if there's nothing to render!
        if (toDraw.isEmpty())
        {
            return;
        }

        sprite.setAlphaMult(radar.getContactAlpha());
        radar.enableStencilTest();

        // Draw all nebulae
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderNebula(toDraw);
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);

        radar.disableStencilTest();
    }

    private class NebulaCell
    {
        private final float x, y, angle, size;

        private NebulaCell(float x, float y, float angle, float size)
        {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.size = size;
        }
    }
}

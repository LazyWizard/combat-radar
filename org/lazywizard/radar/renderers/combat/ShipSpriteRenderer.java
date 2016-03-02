package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lazywizard.radar.util.SpriteBatch;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

// TODO: Remove triangulator from options
// TODO: Draw solid color sprites
public class ShipSpriteRenderer implements CombatRenderer
{
    private static final Logger LOG = Global.getLogger(ShipSpriteRenderer.class);
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER,
            DRAW_SOLID_SHIELDS, SIMPLE_SHIPS;
    private static int MAX_SHIPS_SHOWN, MAX_SHIELD_SEGMENTS;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float FIGHTER_SIZE_MOD, MIN_SHIP_ALPHA_MULT;
    private Map<Integer, SpriteBatch> shipBatches;
    private DrawQueue drawQueue;
    private CommonRadar<CombatEntityAPI> radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_SHIPS = settings.getBoolean("showShips");
        SHOW_SHIELDS = settings.getBoolean("showShields");
        SHOW_TARGET_MARKER = settings.getBoolean("showMarkerAroundTarget");

        settings = settings.getJSONObject("combatRenderers")
                .getJSONObject("shipRenderer");
        MAX_SHIPS_SHOWN = settings.optInt("maxShown", 1_000);
        SIMPLE_SHIPS = settings.getBoolean("simpleMode");
        FIGHTER_SIZE_MOD = (float) settings.getDouble("fighterSizeMod");
        SHIELD_COLOR = JSONUtils.toColor(settings.getJSONArray("shieldColor"));
        MARKER_COLOR = JSONUtils.toColor(settings.getJSONArray("targetMarkerColor"));
        DRAW_SOLID_SHIELDS = settings.getBoolean("drawSolidShields");
        MAX_SHIELD_SEGMENTS = settings.getInt("maxShieldSegments");
        MIN_SHIP_ALPHA_MULT = (float) settings.getDouble("minShipAlphaMult");
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_SHIPS)
        {
            return;
        }

        this.radar = radar;

        int initialCapacity = SHOW_TARGET_MARKER ? 8 : 0;
        if (SHOW_SHIELDS)
        {
            initialCapacity += MAX_SHIPS_SHOWN * 2
                    * (MAX_SHIELD_SEGMENTS + (DRAW_SOLID_SHIELDS ? 4 : 2));
        }
        if (SIMPLE_SHIPS)
        {
            initialCapacity += MAX_SHIPS_SHOWN * 3;
        }

        drawQueue = new DrawQueue(initialCapacity);
        shipBatches = new LinkedHashMap<>();
    }

    private void addShieldToBuffer(ShipAPI contact)
    {
        final ShieldAPI shield = contact.getShield();
        if (shield == null || !shield.isOn())
        {
            return;
        }

        final float[] radarLoc = radar.getRawPointOnRadar(shield.getLocation());
        final float size = shield.getRadius() * radar.getCurrentPixelsPerSU()
                * ((contact.isFighter() && shield.getRadius() < 75f) ? FIGHTER_SIZE_MOD : 1f);
        final float startAngle = (float) Math.toRadians(shield.getFacing()
                - (shield.getActiveArc() / 2f));
        final float arcAngle = (float) Math.toRadians(shield.getActiveArc());
        final int numSegments = (int) (MAX_SHIELD_SEGMENTS / (360f / shield.getActiveArc()) + 0.5f);

        if (numSegments < 1)
        {
            return;
        }

        // Precalculate the sine and cosine
        // Instead of recalculating sin/cos for each line segment,
        // this algorithm rotates the line around the center point
        final float theta = arcAngle / numSegments;
        final float cos = (float) FastTrig.cos(theta);
        final float sin = (float) FastTrig.sin(theta);

        // Start at angle startAngle
        float x = (float) (size * FastTrig.cos(startAngle));
        float y = (float) (size * FastTrig.sin(startAngle));
        float tmp;

        float[] vertices = new float[numSegments * 2 + (DRAW_SOLID_SHIELDS ? 4 : 2)];
        if (DRAW_SOLID_SHIELDS)
        {
            vertices[0] = radarLoc[0];
            vertices[1] = radarLoc[1];
        }
        for (int i = (DRAW_SOLID_SHIELDS ? 2 : 0); i < vertices.length - 2; i += 2)
        {
            // Output vertex
            vertices[i] = x + radarLoc[0];
            vertices[i + 1] = y + radarLoc[1];

            // Apply the rotation matrix
            tmp = x;
            x = (cos * x) - (sin * y);
            y = (sin * tmp) + (cos * y);
        }
        vertices[vertices.length - 2] = x + radarLoc[0];
        vertices[vertices.length - 1] = y + radarLoc[1];

        // Add vertices to master vertex map
        drawQueue.addVertices(vertices);
        drawQueue.finishShape(DRAW_SOLID_SHIELDS ? GL_TRIANGLE_FAN : GL_LINE_STRIP);
    }

    private void addTargetMarker(ShipAPI target)
    {
        // Generate vertices
        final float size = target.getCollisionRadius() * radar.getCurrentPixelsPerSU();
        final Vector2f radarLoc = radar.getPointOnRadar(target.getLocation());
        final float margin = size * .5f;
        final float[] vertices = new float[]
        {
            // Upper left corner
            radarLoc.x - size, radarLoc.y + size, // 0
            radarLoc.x - margin, radarLoc.y + size, // 1
            radarLoc.x - size, radarLoc.y + size, // 0
            radarLoc.x - size, radarLoc.y + margin, // 2
            // Upper right corner
            radarLoc.x + size, radarLoc.y + size, // 3
            radarLoc.x + margin, radarLoc.y + size, // 4
            radarLoc.x + size, radarLoc.y + size, // 3
            radarLoc.x + size, radarLoc.y + margin, // 5
            // Lower left corner
            radarLoc.x - size, radarLoc.y - size, // 6
            radarLoc.x - margin, radarLoc.y - size, // 7
            radarLoc.x - size, radarLoc.y - size, // 6
            radarLoc.x - size, radarLoc.y - margin, // 8
            // Lower right corner
            radarLoc.x + size, radarLoc.y - size, // 9
            radarLoc.x + margin, radarLoc.y - size, // 10
            radarLoc.x + size, radarLoc.y - size, // 9
            radarLoc.x + size, radarLoc.y - margin  // 11
        };

        drawQueue.setNextColor(MARKER_COLOR, radar.getContactAlpha());
        drawQueue.addVertices(vertices);
        drawQueue.finishShape(GL_LINES);
    }

    private Color getColor(ShipAPI ship, int playerSide)
    {
        // Hulks
        if (ship.isHulk())
        {
            return radar.getNeutralContactColor();
        }
        // Teammates and allies
        else if (ship.getOwner() == playerSide)
        {
            if (ship.isAlly())
            {
                return radar.getAlliedContactColor();
            }
            else
            {
                return radar.getFriendlyContactColor();
            }
        }
        // Enemies
        else if (ship.getOwner() + playerSide == 1)
        {
            return radar.getEnemyContactColor();
        }
        // Neutral (doesn't show up in vanilla)
        else
        {
            return radar.getNeutralContactColor();
        }
    }

    private float getAlphaMod(ShipAPI ship)
    {
        // Adjust alpha levels for phasing/fighter takeoff and landing
        if (ship.getCombinedAlphaMult() <= 0f)
        {
            return 0f;
        }

        return radar.getContactAlpha() * Math.max(MIN_SHIP_ALPHA_MULT,
                1f - ((1f - ship.getCombinedAlphaMult()) * 2f));
    }

    private void addShip(ShipAPI ship, int playerSide)
    {
        final SpriteAPI sprite = ship.getSpriteAPI();
        if (sprite == null)
        {
            return;
        }

        final int textureId = sprite.getTextureId();
        SpriteBatch batch = shipBatches.get(textureId);
        if (batch == null)
        {
            batch = new SpriteBatch(sprite, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            shipBatches.put(textureId, batch);
        }

        final float[] loc = radar.getRawPointOnRadar(ship.getLocation());
        final float scale = radar.getCurrentPixelsPerSU()
                * (ship.isFighter() ? FIGHTER_SIZE_MOD : 1f);
        batch.add(loc[0], loc[1], ship.getFacing(), sprite.getWidth() * scale,
                sprite.getHeight() * scale, getColor(ship, playerSide), getAlphaMod(ship));
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (!SHOW_SHIPS || !player.isAlive())
        {
            return;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            for (SpriteBatch batch : shipBatches.values())
            {
                batch.clear();
            }

            final List<ShipAPI> ships = radar.filterVisible(
                    Global.getCombatEngine().getShips(), MAX_SHIPS_SHOWN);
            if (!ships.isEmpty())
            {
                for (ShipAPI ship : ships)
                {
                    // Draw marker around current ship target
                    if (SHOW_TARGET_MARKER && player.getShipTarget() == ship)
                    {
                        addTargetMarker(ship);
                    }

                    if (SIMPLE_SHIPS)
                    {
                        // TODO
                        //addSimpleShip(ship, player.getOwner());
                    }
                    else
                    {
                        addShip(ship, player.getOwner());
                    }
                }

                // Get updated list of shields
                if (SHOW_SHIELDS)
                {
                    drawQueue.setNextColor(SHIELD_COLOR, radar.getContactAlpha()
                            * (DRAW_SOLID_SHIELDS ? 0.5f : 1f));
                    for (ShipAPI contact : ships)
                    {
                        addShieldToBuffer(contact);
                    }
                }
            }

            drawQueue.finish();
            for (SpriteBatch batch : shipBatches.values())
            {
                batch.finish();
            }
        }

        // Draw cached render data
        radar.enableStencilTest();

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        for (SpriteBatch toDraw : shipBatches.values())
        {
            toDraw.draw();
        }
        glDisable(GL_TEXTURE_2D);

        if (drawQueue.isEmpty())
        {
            radar.disableStencilTest();
            return;
        }

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glEnable(GL_POLYGON_SMOOTH);
        drawQueue.draw();
        glDisable(GL_POLYGON_SMOOTH);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_BLEND);

        radar.disableStencilTest();
    }
}

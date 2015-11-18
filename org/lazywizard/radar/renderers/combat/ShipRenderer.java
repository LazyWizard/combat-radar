package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.BoundsAPI.SegmentAPI;
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
import org.lwjgl.util.vector.Vector2f;
import radar.org.newdawn.slick.geom.Transform;
import radar.org.newdawn.slick.geom.Triangulator;
import static org.lwjgl.opengl.GL11.*;

public class ShipRenderer implements CombatRenderer
{
    private static final Logger LOG = Global.getLogger(ShipRenderer.class);
    private static final Map<String, RenderData> cachedRenderData = new HashMap<>();
    private static Class TRIANGULATOR_CLASS;
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER,
            DRAW_SOLID_SHIELDS, SIMPLE_SHIPS;
    private static int MAX_SHIPS_SHOWN, MAX_SHIELD_SEGMENTS;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float FIGHTER_SIZE_MOD, MIN_SHIP_ALPHA_MULT;
    private DrawQueue shipDrawQueue, shieldDrawQueue;
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

        try
        {
            TRIANGULATOR_CLASS = Global.getSettings().getScriptClassLoader()
                    .loadClass(settings.getString("triangulator"));
        }
        catch (ClassNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        if (!SHOW_SHIPS)
        {
            return;
        }

        this.radar = radar;

        // Let's just hope more than this isn't needed. A resize would be expensive
        int initialVertexCapacity = MAX_SHIPS_SHOWN * (SIMPLE_SHIPS ? 3 : 25);
        if (SHOW_TARGET_MARKER)
        {
            initialVertexCapacity += 8;
        }
        shipDrawQueue = new DrawQueue(initialVertexCapacity);

        if (SHOW_SHIELDS)
        {
            shieldDrawQueue = new DrawQueue(MAX_SHIPS_SHOWN * 2
                    * (MAX_SHIELD_SEGMENTS + (DRAW_SOLID_SHIELDS ? 4 : 2)));
            shieldDrawQueue.setNextColor(SHIELD_COLOR, radar.getContactAlpha()
                    * (DRAW_SOLID_SHIELDS ? 0.5f : 1f));
        }
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
        shieldDrawQueue.addVertices(vertices);
        shieldDrawQueue.finishShape(DRAW_SOLID_SHIELDS ? GL_TRIANGLE_FAN : GL_LINE_STRIP);
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

        shipDrawQueue.setNextColor(MARKER_COLOR, radar.getContactAlpha());
        shipDrawQueue.addVertices(vertices);
        shipDrawQueue.finishShape(GL_LINES);
    }

    private float[] getColor(ShipAPI ship, int playerSide)
    {
        Color baseColor;

        // Hulks
        if (ship.isHulk())
        {
            baseColor = radar.getNeutralContactColor();
        }
        // Allies
        else if (ship.getOwner() == playerSide)
        {
            baseColor = radar.getFriendlyContactColor();
        }
        // Enemies
        else if (ship.getOwner() + playerSide == 1)
        {
            baseColor = radar.getEnemyContactColor();
        }
        // Neutral (doesn't show up in vanilla)
        else
        {
            baseColor = radar.getNeutralContactColor();
        }

        // Convert color to float array
        float[] color = new float[]
        {
            baseColor.getRed() / 255f,
            baseColor.getGreen() / 255f,
            baseColor.getBlue() / 255f,
            baseColor.getAlpha() / 255f
        };

        // Adjust alpha levels for phasing/fighter takeoff and landing
        if (ship.getCombinedAlphaMult() <= 0f)
        {
            color[3] = 0f;
        }
        else
        {
            color[3] *= radar.getContactAlpha() * Math.max(MIN_SHIP_ALPHA_MULT,
                    1f - ((1f - ship.getCombinedAlphaMult()) * 2f));
        }

        return color;
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
            if (SHOW_SHIELDS)
            {
                shieldDrawQueue.clear();
            }

            shipDrawQueue.clear();
            final List<ShipAPI> contacts = radar.filterVisible(
                    Global.getCombatEngine().getShips(), MAX_SHIPS_SHOWN);
            if (!contacts.isEmpty())
            {
                for (ShipAPI contact : contacts)
                {
                    // Draw marker around current ship target
                    if (SHOW_TARGET_MARKER && player.getShipTarget() == contact)
                    {
                        addTargetMarker(contact);
                    }

                    // Only calculate bounds data once (triangulation is expensive)
                    final String baseHullId = contact.getHullSpec().getBaseHullId();
                    if (!cachedRenderData.containsKey(baseHullId))
                    {

                        cachedRenderData.put(baseHullId, new RenderData(contact));
                    }

                    // Calculate vertices using renderer
                    final RenderData renderer = cachedRenderData.get(
                            contact.getHullSpec().getBaseHullId());
                    final float[] vertices = renderer.getVertices(radar, contact);
                    if (vertices != null)
                    {
                        final float[] color = getColor(contact, player.getOwner());
                        shipDrawQueue.setNextColor(color[0], color[1], color[2], color[3]);
                        shipDrawQueue.addVertices(vertices);
                        shipDrawQueue.finishShape(renderer.drawMode);
                    }
                }

                // Get updated list of shields
                if (SHOW_SHIELDS)
                {
                    for (ShipAPI contact : contacts)
                    {
                        addShieldToBuffer(contact);
                    }

                    shieldDrawQueue.finish();
                }
            }

            shipDrawQueue.finish();
        }

        // Draw cached render data
        radar.enableStencilTest();
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        shipDrawQueue.draw();

        if (SHOW_SHIELDS)
        {
            glEnable(GL_POLYGON_SMOOTH);
            shieldDrawQueue.draw();
            glDisable(GL_POLYGON_SMOOTH);
        }

        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        radar.disableStencilTest();
    }

    private static class RenderData
    {
        private final float[] rawPoints;
        private final int drawMode;

        private RenderData(ShipAPI ship)
        {
            final boolean isFighter = ship.isFighter() || ship.isDrone()
                    || ship.isShuttlePod();

            // Fighters and boundless ships are drawn as simple triangles
            // If "simpleShips" is true, all ships are drawn this way
            final BoundsAPI bounds = ship.getExactBounds();
            if (SIMPLE_SHIPS || isFighter || bounds == null)
            {
                drawMode = GL_TRIANGLES;

                // Base triangle size on sprite size, or collision radius if no sprite
                float size;
                final SpriteAPI sprite = ship.getSpriteAPI();
                if (sprite != null)
                {
                    final float h = sprite.getHeight(), w = sprite.getWidth();
                    size = (float) Math.sqrt((h * h) + (w * w)) * 0.5f;
                }
                else
                {
                    size = ship.getCollisionRadius();
                }

                // Bump fighter contact size for better visibility on the radar
                if (isFighter)
                {
                    // TODO: Eventually move to icons for fighters based on role
                    size *= FIGHTER_SIZE_MOD;
                }

                // Calculate points for triangle
                rawPoints = new float[]
                {
                    // Top
                    size, 0f,
                    // Bottom left
                    -size / 1.5f, -size / 1.75f,
                    // Bottom right
                    -size / 1.5f, size / 1.75f
                };

                LOG.debug("Using simple contact shape for hull '"
                        + ship.getHullSpec().getHullId() + "'");
                return;
            }

            // Ship has less than three segments - not a drawable shape!
            final List<SegmentAPI> segments = bounds.getSegments();
            if (segments.size() < 3)
            {
                rawPoints = null;
                drawMode = -1;

                LOG.error("Invalid bounds: " + ship.getHullSpec().getBaseHullId());
                return;
            }

            // Store bounds as if ship were at {0, 0}, facing 0
            // When drawing we will translate them to the proper position/facing
            bounds.update(new Vector2f(0f, 0f), 0f);

            // tmpPoints will be used as a fallback if triangulation fails
            final Triangulator triangles;
            try
            {
                triangles = (Triangulator) TRIANGULATOR_CLASS.newInstance();
            }
            catch (InstantiationException | IllegalAccessException ex)
            {
                throw new RuntimeException(ex);
            }

            final List<Vector2f> tmpPoints = new ArrayList<>(segments.size() + 2);
            tmpPoints.add(new Vector2f(0f, 0f));
            for (SegmentAPI segment : segments)
            {
                final Vector2f point = segment.getP1();
                tmpPoints.add(point);
                triangles.addPolyPoint(point.x, point.y);
            }
            tmpPoints.add(tmpPoints.get(1));

            // Triangulation successful, can draw as a proper polygon
            if (triangles.triangulate())
            {
                drawMode = GL_TRIANGLES;
                rawPoints = new float[triangles.getTriangleCount() * 6];

                // TODO: If triangle vertices follow a pattern I can make this much more efficient
                for (int x = 0, y = 0; x < triangles.getTriangleCount(); x++, y += 6)
                {
                    float[] point = triangles.getTrianglePoint(x, 0);
                    rawPoints[y] = point[0];
                    rawPoints[y + 1] = point[1];

                    point = triangles.getTrianglePoint(x, 1);
                    rawPoints[y + 2] = point[0];
                    rawPoints[y + 3] = point[1];

                    point = triangles.getTrianglePoint(x, 2);
                    rawPoints[y + 4] = point[0];
                    rawPoints[y + 5] = point[1];
                }

                LOG.debug("Triangulated hull '" + ship.getHullSpec().getHullId()
                        + "' successfully");
            }
            // Triangulation failed, fall back to a crappy approximation of ship's shape
            else
            {
                drawMode = GL_TRIANGLE_FAN;
                rawPoints = new float[tmpPoints.size() * 2];

                for (int x = 0, y = 0; x < tmpPoints.size(); x++, y += 2)
                {
                    Vector2f point = tmpPoints.get(x);
                    rawPoints[y] = point.x;
                    rawPoints[y + 1] = point.y;
                }

                LOG.debug("Failed to triangulate hull '" + ship.getHullSpec().getHullId()
                        + "', defaulting to triangle fan");
            }

            // Reset bounds to real facing
            bounds.update(ship.getLocation(), ship.getFacing());
        }

        private float[] getVertices(CommonRadar radar, ShipAPI ship)
        {
            // Invalid ship bounds
            if (drawMode == -1)
            {
                return null;
            }

            final Vector2f worldLoc = ship.getLocation();
            // First translate bounds to their position in world space, then convert to radar space
            float[] points = new float[rawPoints.length];
            Transform.createTranslateTransform(worldLoc.x, worldLoc.y).concatenate(
                    Transform.createRotateTransform((float) Math.toRadians(ship.getFacing())))
                    .transform(rawPoints, 0, points, 0, rawPoints.length / 2);
            return radar.getRawPointsOnRadar(points);
        }
    }
}

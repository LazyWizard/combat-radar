package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
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
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.newdawn.slick.geom.NeatTriangulator;
import org.newdawn.slick.geom.Transform;
import org.newdawn.slick.geom.Triangulator;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public class ShipNewRenderer implements CombatRenderer
{
    //private static final Map<String, Renderer> cachedRenderData = new HashMap<>();
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER, DRAW_SOLID_SHIELDS;
    private static int MAX_SHIPS_SHOWN, MAX_SHIELD_SEGMENTS;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float PHASE_ALPHA_MULT;
    private Map<String, Renderer> cachedRenderData;
    private FloatBuffer markerVertexMap;
    private IntBuffer markerIndexMap;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_SHIPS = settings.getBoolean("showShips");
        SHOW_SHIELDS = settings.getBoolean("showShields");
        SHOW_TARGET_MARKER = settings.getBoolean("showMarkerAroundTarget");

        settings = settings.getJSONObject("shipRenderer");
        MAX_SHIPS_SHOWN = settings.optInt("maxShown", 1_000);
        SHIELD_COLOR = JSONUtils.toColor(settings.getJSONArray("shieldColor"));
        MARKER_COLOR = JSONUtils.toColor(settings.getJSONArray("targetMarkerColor"));
        DRAW_SOLID_SHIELDS = true; // TODO
        MAX_SHIELD_SEGMENTS = 18; // TODO
        PHASE_ALPHA_MULT = (float) settings.getDouble("phasedShipAlphaMult");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
        // TODO: This should be static, it's only local for debugging purposes (resets between battles)
        cachedRenderData = new HashMap<>();

        if (SHOW_TARGET_MARKER)
        {
            int[] indices = new int[]
            {
                // Upper left corner
                0, 1, 0, 2,
                // Upper right corner
                3, 4, 3, 5,
                // Lower left corner
                6, 7, 6, 8,
                // Lower right corner
                9, 10, 9, 11
            };

            markerIndexMap = BufferUtils.createIntBuffer(indices.length).put(indices);
            markerIndexMap.flip();

            markerVertexMap = BufferUtils.createFloatBuffer(24);
        }
    }

    private void drawShield(ShipAPI contact)
    {
        final ShieldAPI shield = contact.getShield();
        if (shield != null && shield.isOn())
        {
            final float[] radarLoc = radar.getRawPointOnRadar(shield.getLocation());
            final float size = shield.getRadius() * radar.getCurrentPixelsPerSU();
            final float startAngle = (float) Math.toRadians(shield.getFacing()
                    - (shield.getActiveArc() / 2f));
            final float arcAngle = (float) Math.toRadians(shield.getActiveArc());
            final int numSegments = (int) ((shield.getActiveArc() / MAX_SHIELD_SEGMENTS) + 0.5f);

            if (numSegments < 1)
            {
                return;
            }

            // Precalculate the sine and cosine
            // Instead of recalculating sin/cos for each line segment,
            // this algorithm rotates the line around the center point
            final float theta = arcAngle / (float) (numSegments);
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

            // Draw the ellipse
            FloatBuffer vertexMap = BufferUtils.createFloatBuffer(vertices.length);
            vertexMap.put(vertices).flip();
            glVertexPointer(2, 0, vertexMap);
            glDrawArrays(DRAW_SOLID_SHIELDS ? GL_TRIANGLE_FAN : GL_LINE_STRIP, 0, vertices.length / 2);
        }
    }

    private void drawTargetMarker(ShipAPI target)
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
            radarLoc.x - size, radarLoc.y + margin, // 2
            // Upper right corner
            radarLoc.x + size, radarLoc.y + size, // 3
            radarLoc.x + margin, radarLoc.y + size, // 4
            radarLoc.x + size, radarLoc.y + margin, // 5
            // Lower left corner
            radarLoc.x - size, radarLoc.y - size, // 6
            radarLoc.x - margin, radarLoc.y - size, // 7
            radarLoc.x - size, radarLoc.y - margin, // 8
            // Lower right corner
            radarLoc.x + size, radarLoc.y - size, // 9
            radarLoc.x + margin, radarLoc.y - size, // 10
            radarLoc.x + size, radarLoc.y - margin  // 11
        };

        markerVertexMap.put(vertices).flip();

        // Draw the target marker
        glColor(MARKER_COLOR, radar.getContactAlpha(), false);
        glVertexPointer(2, 0, markerVertexMap);
        glDrawElements(GL_LINES, markerIndexMap);
    }

    private float getAlphaMod(ShipAPI ship)
    {
        return (ship.getPhaseCloak() != null && ship.getPhaseCloak().isOn()) ? PHASE_ALPHA_MULT : 1f;
    }

    private Color getColor(ShipAPI ship, int playerSide)
    {
        // Hulks
        if (ship.isHulk())
        {
            return radar.getNeutralContactColor();
        }
        // Allies
        else if (ship.getOwner() == playerSide)
        {
            return radar.getFriendlyContactColor();
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

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        if (SHOW_SHIPS && player.isAlive())
        {
            final List<ShipAPI> contacts = radar.filterVisible(
                    Global.getCombatEngine().getShips(), MAX_SHIPS_SHOWN);
            if (!contacts.isEmpty())
            {
                radar.enableStencilTest();
                glEnableClientState(GL_VERTEX_ARRAY);

                ShipAPI target = null;
                for (ShipAPI contact : contacts)
                {
                    // Check for current ship target
                    if (player.getShipTarget() == contact)
                    {
                        target = contact;
                    }

                    // Only calculate bounds data once (triangulation is expensive)
                    boolean isNew = false;
                    final String baseHullId = contact.getHullSpec().getBaseHullId();
                    if (!cachedRenderData.containsKey(baseHullId))
                    {
                        isNew = true;
                        cachedRenderData.put(baseHullId, new Renderer(contact));
                    }

                    // Call cached renderer to draw ship
                    cachedRenderData.get(contact.getHullSpec().getBaseHullId())
                            .drawShip(radar, contact, getColor(contact, player.getOwner()),
                                    getAlphaMod(contact), (isUpdateFrame || isNew));
                }

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    glLineWidth(1f);
                    glEnable(GL_LINE_SMOOTH);
                    glColor(SHIELD_COLOR, radar.getContactAlpha() * 0.5f, false);
                    for (CombatEntityAPI entity : contacts)
                    {
                        drawShield((ShipAPI) entity);
                    }
                    glDisable(GL_LINE_SMOOTH);
                }

                // Draw marker around current ship target
                if (SHOW_TARGET_MARKER && target != null)
                {
                    drawTargetMarker(target);
                }

                glDisableClientState(GL_VERTEX_ARRAY);
                radar.disableStencilTest();
            }
        }
    }

    // FIXME: Only one buffer shared between all ships of type, so only one ship appears outside an update frame
    private class Renderer
    {
        private final FloatBuffer vertexMap;
        private final float[] rawPoints;
        private final int drawMode;

        private Renderer(ShipAPI ship)
        {
            // Fighters and boundless ships are drawn as simple triangles
            final BoundsAPI bounds = ship.getExactBounds();
            if (ship.getHullSize() == HullSize.FIGHTER || bounds == null)
            {
                drawMode = GL_TRIANGLES;
                float size = ship.getCollisionRadius();
                if (ship.isFighter() || ship.isDrone())
                {
                    // TODO: Add fighter size multiplier setting
                    // TODO: Eventually move to icons for fighters based on role
                    size *= 1.5f;
                }

                // Triangle based on collision radius
                rawPoints = new float[]
                {
                    // Top
                    size, 0f,
                    // Bottom left
                    -size / 1.5f, -size / 1.75f,
                    // Bottom right
                    -size / 1.5f, size / 1.75f
                };

                vertexMap = BufferUtils.createFloatBuffer(rawPoints.length);
                Global.getLogger(ShipNewRenderer.class).log(Level.DEBUG,
                        "Using fallback contact shape for hull '"
                        + ship.getHullSpec().getHullId()
                        + "' (wing, drone or lacks bounds)");
                return;
            }

            // Ship has less than three segments - not a drawable shape!
            final List<SegmentAPI> segments = bounds.getSegments();
            if (segments.size() < 3)
            {
                vertexMap = null;
                rawPoints = null;
                drawMode = -1;

                Global.getLogger(ShipNewRenderer.class).log(Level.ERROR,
                        "Invalid bounds: " + ship.getHullSpec().getBaseHullId());
                return;
            }

            // Store bounds as if ship were at {0, 0}, facing 0
            // When drawing we will translate them to the proper position/facing
            bounds.update(new Vector2f(0f, 0f), 0f);

            // tmpPoints will be used as a fallback if triangulation fails
            final Triangulator triangles = new NeatTriangulator();
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

                Global.getLogger(ShipNewRenderer.class).log(Level.DEBUG,
                        "Triangulated hull '" + ship.getHullSpec().getHullId()
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

                Global.getLogger(ShipNewRenderer.class).log(Level.DEBUG,
                        "Failed to triangulate hull '" + ship.getHullSpec().getHullId()
                        + "', defaulting to triangle fan");
            }

            vertexMap = BufferUtils.createFloatBuffer(rawPoints.length);

            // Reset bounds to real facing
            bounds.update(ship.getLocation(), ship.getFacing());
        }

        private void updateBuffer(ShipAPI ship)
        {
            final Vector2f worldLoc = ship.getLocation();
            // First translate bounds to their position in world space, then convert to radar space
            float[] points = new float[rawPoints.length];
            Transform.createTranslateTransform(worldLoc.x, worldLoc.y).concatenate(
                    Transform.createRotateTransform((float) Math.toRadians(ship.getFacing())))
                    .transform(rawPoints, 0, points, 0, rawPoints.length / 2);
            points = radar.getRawPointsOnRadar(points);
            vertexMap.put(points).flip();
        }

        // TODO: change to add to a buffer instead for faster drawing
        private void drawShip(CombatRadar radar, ShipAPI ship, Color drawColor,
                float alphaMod, boolean isUpdateFrame)
        {
            // Invalid ship bounds
            if (drawMode == -1)
            {
                return;
            }

            if (true || isUpdateFrame) // TODO
            {
                updateBuffer(ship);
            }

            // Draw bounds at position in radar space
            glColor(drawColor, alphaMod, false);
            glVertexPointer(2, 0, vertexMap);
            glDrawArrays(drawMode, 0, vertexMap.remaining() / 2);
        }
    }
}

package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.BoundsAPI.SegmentAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
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
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER;
    private static int MAX_SHIPS_SHOWN;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float PHASE_ALPHA_MULT;
    private Map<String, Renderer> cachedRenderData = new HashMap<>();
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
        PHASE_ALPHA_MULT = (float) settings.getDouble("phasedShipAlphaMult");
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;

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
        ShieldAPI shield = contact.getShield();
        if (shield != null && shield.isOn())
        {
            Vector2f radarLoc = radar.getPointOnRadar(contact.getLocation());
            float size = shield.getRadius() * radar.getCurrentPixelsPerSU();
            DrawUtils.drawArc(radarLoc.x, radarLoc.y, size,
                    shield.getFacing() - (shield.getActiveArc() / 2f),
                    shield.getActiveArc(),
                    (int) (shield.getActiveArc() / 18f) + 1, false);
        }
    }

    private void drawTargetMarker(ShipAPI target)
    {
        // Generate vertices
        float size = target.getCollisionRadius() * radar.getCurrentPixelsPerSU();;
        Vector2f radarLoc = radar.getPointOnRadar(target.getLocation());
        float margin = size * .5f;
        float[] vertices = new float[]
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

        markerVertexMap.put(vertices);
        markerVertexMap.flip();

        // Draw the target marker
        glColor(MARKER_COLOR, radar.getContactAlpha(), false);
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, 0, markerVertexMap);
        glDrawElements(GL_LINES, markerIndexMap);
        glDisableClientState(GL_VERTEX_ARRAY);
    }

    private float getAlphaMod(ShipAPI ship)
    {
        return (ship.getPhaseCloak() != null && ship.getPhaseCloak().isOn()) ? PHASE_ALPHA_MULT : 1f;
    }

    private Color getColor(ShipAPI contact, int playerSide)
    {
        // Hulks
        if (contact.isHulk())
        {
            return radar.getNeutralContactColor();
        }
        // Allies
        else if (contact.getOwner() == playerSide)
        {
            return radar.getFriendlyContactColor();
        }
        // Enemies
        else if (contact.getOwner() + playerSide == 1)
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

                ShipAPI target = null;
                for (ShipAPI contact : contacts)
                {
                    // Check for current ship target
                    if (player.getShipTarget() == contact)
                    {
                        target = contact;
                    }

                    // Only calculate bounds data once per battle
                    final String baseHullId = contact.getHullSpec().getBaseHullId();
                    if (!cachedRenderData.containsKey(baseHullId))
                    {
                        cachedRenderData.put(baseHullId, new Renderer(contact));
                    }

                    // Call renderer to draw ship
                    cachedRenderData.get(contact.getHullSpec().getBaseHullId())
                            .drawShip(radar, contact.getLocation(), contact.getFacing(),
                                    getColor(contact, player.getOwner()), getAlphaMod(contact));
                }

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    glLineWidth(1f);
                    glEnable(GL_LINE_SMOOTH);
                    glColor(SHIELD_COLOR, radar.getContactAlpha(), false);
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

                radar.disableStencilTest();
            }
        }
    }

    private class Renderer
    {
        private final float[] points;
        private final int drawMode;

        private Renderer(ShipAPI ship)
        {
            final BoundsAPI bounds = ship.getExactBounds();
            bounds.update(new Vector2f(0f, 0f), 0f);

            final Triangulator triangles = new NeatTriangulator();
            for (SegmentAPI segment : bounds.getSegments())
            {
                final Vector2f point = segment.getP1();
                triangles.addPolyPoint(point.x, point.y);
            }

            // Triangulation successful, can draw as a proper polygon
            if (triangles.triangulate())
            {
                drawMode = GL_TRIANGLES;
                points = new float[triangles.getTriangleCount() * 6];

                for (int x = 0, y = 0; x < triangles.getTriangleCount(); x++, y += 6)
                {
                    float[] point = triangles.getTrianglePoint(x, 0);
                    points[y] = point[0];
                    points[y + 1] = point[1];
                    System.out.println("Point 1: {" + point[0] + "," + point[1] + "}");

                    point = triangles.getTrianglePoint(x, 1);
                    points[y + 2] = point[0];
                    points[y + 3] = point[1];
                    System.out.println("Point 2: {" + point[0] + "," + point[1] + "}");

                    point = triangles.getTrianglePoint(x, 2);
                    points[y + 4] = point[0];
                    points[y + 5] = point[1];
                    System.out.println("Point 3: {" + point[0] + "," + point[1] + "}");
                }

                Global.getLogger(ShipNewRenderer.class).log(Level.DEBUG,
                        "Triangulated hull '" + ship.getHullSpec().getHullId()
                        + "' successfully");
            }
            // Triangulation failed, fall back to a crappy approximation of ship's shape
            else
            {
                drawMode = GL_TRIANGLE_FAN;
                points = null;

                // TODO: Use triangle fan from center instead
                Global.getLogger(ShipNewRenderer.class).log(Level.DEBUG,
                        "Failed to triangulate hull '" + ship.getHullSpec().getHullId()
                        + "', defaulting to triangle fan");
            }

            // Reset bounds to real facing
            bounds.update(ship.getLocation(), ship.getFacing());
        }

        // TODO: change to add to a buffer instead for faster drawing
        private void drawShip(CombatRadar radar, Vector2f shipWorldLoc, float shipFacing,
                Color drawColor, float alphaMod)
        {
            glColor(drawColor, alphaMod, false);
            Transform transform = Transform.createTranslateTransform(shipWorldLoc.x, shipWorldLoc.y)
                    .concatenate(Transform.createRotateTransform((float) Math.toRadians(shipFacing)));

            float[] pointsTransformed = new float[points.length];
            transform.transform(points, 0, pointsTransformed, 0, points.length / 2);
            pointsTransformed = radar.getRawPointsOnRadar(pointsTransformed);

            switch (drawMode)
            {
                case GL_TRIANGLES:
                    glBegin(GL_TRIANGLES);
                    for (int x = 0; x < pointsTransformed.length; x += 2)
                    {
                        glVertex2f(pointsTransformed[x], pointsTransformed[x + 1]);
                    }
                    glEnd();
                    break;
                case GL_TRIANGLE_FAN:
                    // TODO
                    break;
                default:
                    throw new RuntimeException("Unknown draw mode: " + drawMode);
            }
        }
    }
}

package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.BoundsAPI.SegmentAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import org.newdawn.slick.geom.NeatTriangulator;
import org.newdawn.slick.geom.Transform;
import org.newdawn.slick.geom.Triangulator;
import static org.lwjgl.opengl.GL11.*;

public class ShipNewRenderer implements CombatRenderer
{
    //private static final Map<String, Renderer> cachedRenderData = new HashMap<>();
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER;
    private static int MAX_SHIPS_SHOWN;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float PHASE_ALPHA_MULT;
    private Map<String, Renderer> cachedRenderData = new HashMap<>();
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
                //radar.enableStencilTest();

                for (ShipAPI contact : contacts)
                {

                    // Only calculate bounds data once per battle
                    final String baseHullId = contact.getHullSpec().getBaseHullId();
                    if (!cachedRenderData.containsKey(baseHullId))
                    {
                        cachedRenderData.put(baseHullId, new Renderer(contact));
                    }

                    // Call renderer to draw ship
                    cachedRenderData.get(contact.getHullSpec().getBaseHullId())
                            .drawShip(radar, contact.getLocation(), contact.getFacing());
                }

                //radar.disableStencilTest();
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
        private void drawShip(CombatRadar radar, Vector2f shipWorldLoc, float shipFacing)
        {
            glColor4f(0f, 1f, 1f, 1f);
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

package org.lazywizard.radar.renderers.combat;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.BoundsAPI.SegmentAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector4f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL31.GL_PRIMITIVE_RESTART;

// TODO: Switch to pre-calculated rotations for ships
// TODO: This needs a huge cleanup after new rendering code was added!
public class ShipPolygonRenderer implements CombatRenderer
{
    private static boolean SHOW_SHIPS, SHOW_SHIELDS, SHOW_TARGET_MARKER;
    private static int MAX_SHIPS_SHOWN;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float PHASE_ALPHA_MULT;
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
        MAX_SHIPS_SHOWN = settings.optInt("maxShown", 1000);
        SHIELD_COLOR = JSONUtils.toColor(settings.getJSONArray("shieldColor"));
        MARKER_COLOR = JSONUtils.toColor(settings.getJSONArray("targetMarkerColor"));
        PHASE_ALPHA_MULT = (float) settings.getDouble("phasedShipAlphaMult");
    }

    private static List<Vector2f> rotateAndTranslate(List<Vector2f> points,
            float angle, Vector2f translation)
    {
        if (angle == 0f)
        {
            return new ArrayList<>(points);
        }

        angle = (float) Math.toRadians(angle);
        float cos = (float) FastTrig.cos(angle), sin = (float) FastTrig.sin(angle);
        for (Vector2f point : points)
        {
            point.set((point.x * cos) - (point.y * sin) + translation.x,
                    (point.x * sin) + (point.y * cos) + translation.y);
        }

        return points;
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

    private List<Vector2f> getShape(ShipAPI contact)
    {
        List<Vector2f> shape = new ArrayList<>();

        // Frigates and larger have a polygonal shape based on collision bounds
        // Some fighters (ex: Wasp) have non-simple bounds, hence their exclusion
        if (contact.getHullSize().compareTo(HullSize.FIGHTER) > 0)
        {
            BoundsAPI bounds = contact.getExactBounds();
            bounds.update(contact.getLocation(), contact.getFacing());
            shape.add(radar.getPointOnRadar(contact.getLocation()));
            for (Iterator<SegmentAPI> iter = bounds.getSegments().iterator(); iter.hasNext();)
            {
                SegmentAPI segment = iter.next();
                shape.add(radar.getPointOnRadar(segment.getP1()));

                // Ensure first point is also final point for a complete polygon
                if (!iter.hasNext())
                {
                    shape.add(radar.getPointOnRadar(segment.getP2()));
                }
            }

            return shape;
        }
        else
        {
            float size = contact.getCollisionRadius() * radar.getCurrentPixelsPerSU();
            shape.add(new Vector2f(size, 0f));
            shape.add(new Vector2f(-size / 1.5f, -(size / 1.75f)));
            shape.add(new Vector2f(-size / 1.5f, size / 1.75f));
            shape.add(new Vector2f(size, 0f));

            return rotateAndTranslate(shape, contact.getFacing(),
                    radar.getPointOnRadar(contact.getLocation()));
        }
    }

    private static Vector4f asVector4f(Color color, float alphaMod)
    {
        return new Vector4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f * alphaMod);
    }

    private Vector4f getColor(ShipAPI contact, int playerSide)
    {
        float alphaMod = (contact.getPhaseCloak() != null
                && contact.getPhaseCloak().isOn()) ? PHASE_ALPHA_MULT : 1f;

        // Hulks
        if (contact.isHulk())
        {
            return asVector4f(radar.getNeutralContactColor(),
                    radar.getContactAlpha());
        }
        // Allies
        else if (contact.getOwner() == playerSide)
        {
            return asVector4f(radar.getFriendlyContactColor(),
                    radar.getContactAlpha() * alphaMod);
        }
        // Enemies
        else if (contact.getOwner() + playerSide == 1)
        {
            return asVector4f(radar.getEnemyContactColor(),
                    radar.getContactAlpha() * alphaMod);
        }
        // Neutral (doesn't show up in vanilla)
        else
        {
            return asVector4f(radar.getNeutralContactColor(),
                    radar.getContactAlpha() * alphaMod);
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

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_SHIPS && !player.isHulk())
        {
            List<? extends CombatEntityAPI> contacts = radar.filterVisible(
                    Global.getCombatEngine().getShips(), MAX_SHIPS_SHOWN);
            if (!contacts.isEmpty())
            {
                radar.enableStencilTest();

                // Draw contacts
                ShipAPI target = null;
                List<Vector2f> vertices = new ArrayList<>();
                List<Integer> resetIndices = new ArrayList<>();
                List<Vector4f> colors = new ArrayList<>();
                for (CombatEntityAPI entity : contacts)
                {
                    List<Vector2f> shape = getShape((ShipAPI) entity);
                    vertices.addAll(shape);
                    resetIndices.add(vertices.size());

                    Vector4f color = getColor((ShipAPI) entity, player.getOwner());
                    for (int x = 0; x < shape.size(); x++)
                    {
                        colors.add(color);
                    }

                    // Check for current ship target
                    if (player.getShipTarget() == entity)
                    {
                        target = (ShipAPI) entity;
                    }
                }

                // Generate vertex map
                FloatBuffer vertexMap = BufferUtils.createFloatBuffer(vertices.size() * 2);
                for (Vector2f vec : vertices)
                {
                    vertexMap.put(vec.x);
                    vertexMap.put(vec.y);
                }
                vertexMap.flip();

                // Generate color map
                FloatBuffer colorMap = BufferUtils.createFloatBuffer(colors.size() * 4);
                for (Vector4f col : colors)
                {
                    colorMap.put(col.x);
                    colorMap.put(col.y);
                    colorMap.put(col.z);
                    colorMap.put(col.w);
                }
                colorMap.flip();

                // Draw the ships
                glEnableClientState(GL_VERTEX_ARRAY);
                glEnableClientState(GL_COLOR_ARRAY);
                glVertexPointer(2, 0, vertexMap);
                glColorPointer(4, 0, colorMap);

                int lastIndex = 0;
                for (Integer resetIndex : resetIndices)
                {
                    glDrawArrays(GL_POLYGON, lastIndex, resetIndex - lastIndex);
                    lastIndex = resetIndex;
                }

                glDisable(GL_PRIMITIVE_RESTART);
                glDisableClientState(GL_COLOR_ARRAY);
                glDisableClientState(GL_VERTEX_ARRAY);

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    glLineWidth(1f);
                    glColor(SHIELD_COLOR, radar.getContactAlpha(), false);
                    for (CombatEntityAPI entity : contacts)
                    {
                        drawShield((ShipAPI) entity);
                    }
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
}

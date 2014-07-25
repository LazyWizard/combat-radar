package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.combat.CombatRadar;
import org.lazywizard.radar.combat.CombatRenderer;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

// TODO: Switch to pre-calculated rotations for ships
public class ShipRenderer implements CombatRenderer
{
    private static boolean SHOW_SHIPS, SHOW_SHIELDS;
    private static Color SHIELD_COLOR, MARKER_COLOR;
    private static float PHASE_ALPHA_MULT;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_SHIPS = settings.getBoolean("showShips");
        SHOW_SHIELDS = settings.getBoolean("showShields");

        settings = settings.getJSONObject("shipRenderer");
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
    }

    // Must be a series of three points that make up shape's component triangles
    private List<Vector2f> getShape(ShipAPI contact)
    {
        HullSize hullSize = contact.getHullSize();
        float size = 1.5f * (hullSize.ordinal() + 1)
                * radar.getCurrentZoomLevel();
        List<Vector2f> shape = new ArrayList<>();

        // Large ships have a slightly more complex shape
        if (hullSize.ordinal() > HullSize.DESTROYER.ordinal())
        {
            shape.add(new Vector2f(size, 0f));
            shape.add(new Vector2f(-size / 1.5f, -(size / 1.75f)));
            shape.add(new Vector2f(-size * .5f, 0f));

            shape.add(new Vector2f(size, 0f));
            shape.add(new Vector2f(-size / 1.5f, size / 1.75f));
            shape.add(new Vector2f(-size * .5f, 0f));
        }
        else
        {
            shape.add(new Vector2f(size, 0f));
            shape.add(new Vector2f(-size / 1.5f, -(size / 1.75f)));
            shape.add(new Vector2f(-size / 1.5f, size / 1.75f));
        }

        return shape;
    }

    private void drawShip(ShipAPI contact, int playerSide)
    {
        float alphaMod = (contact.getPhaseCloak() != null
                && contact.getPhaseCloak().isOn()) ? PHASE_ALPHA_MULT : 1f;

        // Hulks
        if (contact.isHulk())
        {
            glColor(radar.getNeutralContactColor(),
                    radar.getContactAlpha(), false);
        }
        // Allies
        else if (contact.getOwner() == playerSide)
        {
            glColor(radar.getFriendlyContactColor(),
                    radar.getContactAlpha() * alphaMod, false);
        }
        // Enemies
        else if (contact.getOwner() + playerSide == 1)
        {
            glColor(radar.getEnemyContactColor(),
                    radar.getContactAlpha() * alphaMod, false);
        }
        // Neutral (doesn't show up in vanilla)
        else
        {
            glColor(radar.getNeutralContactColor(),
                    radar.getContactAlpha() * alphaMod, false);
        }

        Vector2f radarLoc = radar.getPointOnRadar(contact.getLocation());
        List<Vector2f> shape = rotateAndTranslate(getShape(contact),
                contact.getFacing(), radarLoc);
        for (Vector2f point : shape)
        {
            glVertex2f(point.x, point.y);
        }
    }

    private void drawShield(ShipAPI contact)
    {
        ShieldAPI shield = contact.getShield();
        if (shield != null && shield.isOn())
        {
            Vector2f radarLoc = radar.getPointOnRadar(contact.getLocation());
            float size = 1.75f * (contact.getHullSize().ordinal() + 1)
                    * radar.getCurrentZoomLevel();
            DrawUtils.drawArc(radarLoc.x, radarLoc.y, size,
                    shield.getFacing() - (shield.getActiveArc() / 2f),
                    shield.getActiveArc(),
                    (int) (shield.getActiveArc() / 18f) + 1, false);
        }
    }

    private void drawTargetMarker(ShipAPI target)
    {
        float size = 1.8f * (target.getHullSize().ordinal() + 1)
                * radar.getCurrentZoomLevel();
        Vector2f radarLoc = radar.getPointOnRadar(target.getLocation());
        float margin = size * .5f;
        glColor(MARKER_COLOR, radar.getContactAlpha(), false);
        glBegin(GL_LINES);
        // Upper left corner
        glVertex2f(radarLoc.x - size, radarLoc.y + size);
        glVertex2f(radarLoc.x - margin, radarLoc.y + size);
        glVertex2f(radarLoc.x - size, radarLoc.y + size);
        glVertex2f(radarLoc.x - size, radarLoc.y + margin);

        // Upper right corner
        glVertex2f(radarLoc.x + size, radarLoc.y + size);
        glVertex2f(radarLoc.x + margin, radarLoc.y + size);
        glVertex2f(radarLoc.x + size, radarLoc.y + size);
        glVertex2f(radarLoc.x + size, radarLoc.y + margin);

        // Lower left corner
        glVertex2f(radarLoc.x - size, radarLoc.y - size);
        glVertex2f(radarLoc.x - margin, radarLoc.y - size);
        glVertex2f(radarLoc.x - size, radarLoc.y - size);
        glVertex2f(radarLoc.x - size, radarLoc.y - margin);

        // Lower right corner
        glVertex2f(radarLoc.x + size, radarLoc.y - size);
        glVertex2f(radarLoc.x + margin, radarLoc.y - size);
        glVertex2f(radarLoc.x + size, radarLoc.y - size);
        glVertex2f(radarLoc.x + size, radarLoc.y - margin);
        glEnd();
    }

    @Override
    public void render(ShipAPI player, float amount)
    {
        if (SHOW_SHIPS && !player.isHulk())
        {
            List<? extends CombatEntityAPI> contacts = radar.filterVisible(
                    Global.getCombatEngine().getShips());
            if (!contacts.isEmpty())
            {
                radar.enableStencilTest();

                // Draw contacts
                ShipAPI target = null;
                glLineWidth(1f);
                glBegin(GL_TRIANGLES);
                for (CombatEntityAPI entity : contacts)
                {
                    drawShip((ShipAPI) entity, player.getOwner());

                    // Check for current ship target
                    if (player.getShipTarget() == entity)
                    {
                        target = (ShipAPI) entity;
                    }
                }
                glEnd();

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    glColor(SHIELD_COLOR, radar.getContactAlpha(), false);
                    for (CombatEntityAPI entity : contacts)
                    {
                        drawShield((ShipAPI) entity);
                    }
                }

                // Draw marker around current ship target
                if (target != null)
                {
                    drawTargetMarker(target);
                }

                radar.disableStencilTest();
            }
        }
    }
}

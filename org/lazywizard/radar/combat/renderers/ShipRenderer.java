package org.lazywizard.radar.combat.renderers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.MathUtils;
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

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
    }

    private static List<Vector2f> rotate(List<Vector2f> toRotate, float angle)
    {
        if (angle == 0f)
        {
            return new ArrayList<>(toRotate);
        }

        angle = (float) Math.toRadians(angle);
        float cos = (float) FastTrig.cos(angle), sin = (float) FastTrig.sin(angle);
        List<Vector2f> rotated = new ArrayList<>(toRotate.size());
        for (Vector2f point : toRotate)
        {
            rotated.add(new Vector2f((point.x * cos) - (point.y * sin),
                    (point.x * sin) + (point.y * cos)));
        }

        return rotated;
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

        float size = 1.5f * (contact.getHullSize().ordinal() + 1)
                * radar.getCurrentZoomLevel();
        List<Vector2f> shape = new ArrayList<>();
        shape.add(new Vector2f(size, 0f));
        shape.add(new Vector2f(-size / 1.5f, -(size / 1.75f)));
        shape.add(new Vector2f(-size / 1.5f, size / 1.75f));
        shape = rotate(shape, contact.getFacing());
        Vector2f radarLoc = radar.getPointOnRadar(contact.getLocation());
        for (Vector2f point : shape)
        {
            point.x += radarLoc.x;
            point.y += radarLoc.y;
            glVertex2f(point.x, point.y);
        }

        glColor(Color.MAGENTA, .25f, false);
        for (Vector2f point : MathUtils.getPointsAlongCircumference(
                radar.getPointOnRadar(contact.getLocation()),
                size, 3, contact.getFacing()))
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
            }
        }
    }
}

package org.lazywizard.radar;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

// TODO: Add box and darkened edges
// TODO: Add marker around current target
// TODO: Lower alpha of phased ships
// TODO: Display asteroids
// TODO: Display missiles
// TODO: Display shield arcs (?)
public class CombatRadarPlugin implements EveryFrameCombatPlugin
{
    // Radar box color constants
    private static final float RADAR_BOX_ALPHA = .25f;
    private static final float RADAR_R, RADAR_G, RADAR_B;
    // Radar contact color constants
    private static final float RADAR_CONTACT_ALPHA = .85f;
    private static final float FRIENDLY_R, FRIENDLY_G, FRIENDLY_B;
    private static final float ENEMY_R, ENEMY_G, ENEMY_B;
    private static final float NEUTRAL_R, NEUTRAL_G, NEUTRAL_B;
    // Location and size of radar on screen
    private Vector2f renderPos;
    private float renderRadius;
    private CombatEngineAPI engine;

    static
    {
        // Radar color
        Color tmp = Global.getSettings().getColor("textFriendColor");
        RADAR_R = tmp.getRed() / 255f;
        RADAR_G = tmp.getGreen() / 255f;
        RADAR_B = tmp.getBlue() / 255f;

        // Friendly color
        tmp = Global.getSettings().getColor("iconFriendColor");
        FRIENDLY_R = tmp.getRed() / 255f;
        FRIENDLY_G = tmp.getGreen() / 255f;
        FRIENDLY_B = tmp.getBlue() / 255f;

        // Enemy color
        tmp = Global.getSettings().getColor("iconEnemyColor");
        ENEMY_R = tmp.getRed() / 255f;
        ENEMY_G = tmp.getGreen() / 255f;
        ENEMY_B = tmp.getBlue() / 255f;

        // Neutral color
        tmp = Color.WHITE; //Global.getSettings().getColor("iconNeutralShipColor");
        NEUTRAL_R = tmp.getRed() / 255f;
        NEUTRAL_G = tmp.getGreen() / 255f;
        NEUTRAL_B = tmp.getBlue() / 255f;
    }

    public void renderBox()
    {
        // Draw the radar 'box'
        GL11.glColor4f(0f, 0f, 0f, RADAR_BOX_ALPHA);
        DrawUtils.drawCircle(renderPos.x, renderPos.y, renderRadius, 72, true);
        GL11.glColor4f(RADAR_R, RADAR_G, RADAR_B, RADAR_BOX_ALPHA);
        DrawUtils.drawCircle(renderPos.x, renderPos.y, renderRadius, 72, false);
        DrawUtils.drawCircle(renderPos.x, renderPos.y, renderRadius * .66f, 54, false);
        DrawUtils.drawCircle(renderPos.x, renderPos.y, renderRadius * .33f, 36, false);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(renderPos.x - renderRadius, renderPos.y);
        GL11.glVertex2f(renderPos.x + renderRadius, renderPos.y);
        GL11.glVertex2f(renderPos.x, renderPos.y + renderRadius);
        GL11.glVertex2f(renderPos.x, renderPos.y - renderRadius);
        GL11.glEnd();
    }

    public static void drawContact(Vector2f center, float size, float angle)
    {
        // Convert angles into radians for our calculations
        angle = (float) Math.toRadians(angle);

        // Precalculate the sine and cosine
        // Instead of recalculating sin/cos for each line segment,
        // this algorithm rotates the line around the center point
        float theta = 2f * 3.1415926f / 3f;
        float cos = (float) FastTrig.cos(theta);
        float sin = (float) FastTrig.sin(theta);
        float t;

        // Start at angle startAngle
        float x = (float) (size * FastTrig.cos(angle));
        float y = (float) (size * FastTrig.sin(angle));

        for (int i = 0; i < 3; i++)
        {
            // Output vertex
            GL11.glVertex2f(x + center.x, y + center.y);

            // Apply the rotation matrix
            t = x;
            x = (cos * x) - (sin * y);
            y = (sin * t) + (cos * y);
        }
        //GL11.glVertex2f(x + center.x, y + center.y);
    }

    private List getVisibleContacts(ShipAPI ship, float sightRadius)
    {
        sightRadius *= sightRadius;

        List visible = new ArrayList();
        ShipAPI contact;
        for (Iterator contacts = engine.getShips().iterator(); contacts.hasNext();)
        {
            contact = (ShipAPI) contacts.next();

            if (MathUtils.getDistanceSquared(ship, contact) > sightRadius)
            {
                continue;
            }

            visible.add(contact);
        }

        return visible;
    }

    private void renderContacts(ShipAPI player)
    {
        float sightRadius = player.getMutableStats().getSightRadiusMod()
                .computeEffective(1500f);
        float radarScaling = renderRadius / sightRadius;
        // Draw contacts
        ShipAPI contact;
        Vector2f loc = new Vector2f();
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (Iterator iter = getVisibleContacts(player, sightRadius).iterator(); iter.hasNext();)
        {
            contact = (ShipAPI) iter.next();

            // Allies
            if (contact.getOwner() == player.getOwner())
            {
                GL11.glColor4f(FRIENDLY_R, FRIENDLY_G, FRIENDLY_B, RADAR_CONTACT_ALPHA);
            }
            // Enemies
            else if (contact.getOwner() + player.getOwner() == 1)
            {
                GL11.glColor4f(ENEMY_R, ENEMY_G, ENEMY_B, RADAR_CONTACT_ALPHA);
            }
            // Neutrals
            else
            {
                GL11.glColor4f(NEUTRAL_R, NEUTRAL_G, NEUTRAL_B, RADAR_CONTACT_ALPHA);
            }

            Vector2f.sub(contact.getLocation(), player.getLocation(), loc);
            loc.scale(radarScaling);
            Vector2f.add(loc, renderPos, loc);
            drawContact(loc, 1.5f * (contact.getHullSize().ordinal() + 1), contact.getFacing());
        }
        GL11.glEnd();
    }

    @Override
    public void advance(float amount, List events)
    {
        /*if (engine.isPaused())
         {
         return;
         }*/

        ShipAPI player = engine.getPlayerShip();
        if (player == null || player.isHulk() || !engine.isEntityInPlay(player))
        {
            return;
        }

        // Set OpenGL flags
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glOrtho(0, Display.getWidth(), 0, Display.getHeight(), 1, -1);
        GL11.glLineWidth(1f);

        renderBox();
        renderContacts(player);
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
        renderRadius = Display.getHeight() / 10f;
        renderPos = new Vector2f(Display.getWidth() - (renderRadius * 1.5f),
                renderRadius * 1.5f);
    }
}

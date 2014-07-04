package org.lazywizard.radar.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.MathUtils;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.BaseCombatRenderer;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

// TODO: Switch to pre-calculated rotations for ships
// TODO: Use a narrower triangle for ships to better show facing
public class ShipRenderer implements BaseCombatRenderer
{
    private static boolean SHOW_SHIPS;
    private static boolean SHOW_SHIELDS;
    private static Color SHIELD_COLOR;
    private CombatRadar radar;

    @Override
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException
    {
        SHOW_SHIPS = settings.getBoolean("showShips");
        SHOW_SHIELDS = settings.getBoolean("showShields");
        SHIELD_COLOR = useVanillaColors ? Color.CYAN
                : JSONUtils.toColor(settings.getJSONArray("shieldColor"));
    }

    @Override
    public void init(CombatRadar radar)
    {
        this.radar = radar;
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
                ShipAPI contact, target = null;
                Vector2f radarLoc;
                float alphaMod;
                glLineWidth(1f);
                glBegin(GL_TRIANGLES);
                for (CombatEntityAPI entity : contacts)
                {
                    contact = (ShipAPI) entity;
                    alphaMod = (contact.getPhaseCloak() != null
                            && contact.getPhaseCloak().isOn()) ? .5f : 1f;

                    // Check for current ship target
                    if (player.getShipTarget() == contact)
                    {
                        target = contact;
                    }

                    // Hulks
                    if (contact.isHulk())
                    {
                        glColor(radar.getNeutralContactColor(),
                                radar.getContactAlpha(), false);
                    }
                    // Allies
                    else if (contact.getOwner() == player.getOwner())
                    {
                        glColor(radar.getFriendlyContactColor(),
                                radar.getContactAlpha() * alphaMod, false);
                    }
                    // Enemies
                    else if (contact.getOwner() + player.getOwner() == 1)
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
                            * radar.getZoomLevel();
                    for (Vector2f point : MathUtils.getPointsAlongCircumference(
                            radar.getPointOnRadar(contact.getLocation()),
                            size, 3, contact.getFacing()))
                    {
                        glVertex2f(point.x, point.y);
                    }
                }
                glEnd();

                // Draw shields
                if (SHOW_SHIELDS)
                {
                    ShieldAPI shield;
                    glColor(SHIELD_COLOR, radar.getContactAlpha(), false);
                    for (CombatEntityAPI entity : contacts)
                    {
                        contact = (ShipAPI) entity;
                        shield = contact.getShield();
                        if (shield != null && shield.isOn())
                        {
                            radarLoc = radar.getPointOnRadar(contact.getLocation());
                            float size = 1.75f * (contact.getHullSize().ordinal() + 1)
                                    * radar.getZoomLevel();
                            DrawUtils.drawArc(radarLoc.x, radarLoc.y, size,
                                    shield.getFacing() - (shield.getActiveArc() / 2f),
                                    shield.getActiveArc(),
                                    (int) (shield.getActiveArc() / 18f) + 1, false);
                        }
                    }
                }

                // Draw marker around current ship target
                if (target != null)
                {
                    // TODO: Add a color setting for this
                    float size = 1.8f * (target.getHullSize().ordinal() + 1);
                    radarLoc = radar.getPointOnRadar(target.getLocation());
                    float margin = size * .5f;
                    glColor4f(1f, 1f, 1f, radar.getContactAlpha());
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
            }
        }
    }
}

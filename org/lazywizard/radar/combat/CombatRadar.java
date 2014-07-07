package org.lazywizard.radar.combat;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public interface CombatRadar
{
    public void resetView();

    // THESE METHODS' RETURN VALUES STAY THE SAME

    /**
     *
     * @return The center of the radar circle, in screen coordinates.
     */
    public Vector2f getRenderCenter();
    public float getRenderRadius();

    public float getRadarAlpha();
    public float getContactAlpha();

    public Color getFriendlyContactColor();
    public Color getEnemyContactColor();
    public Color getNeutralContactColor();

    // THESE METHODS' RETURN VALUES CAN CHANGE OVER TIME!

    // Used for scaling radar elements
    public float getCurrentPixelsPerSU();
    public float getCurrentZoomLevel();

    public ShipAPI getPlayer();

    public Vector2f getPointOnRadar(Vector2f worldLoc);
    public List<? extends CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts);
}
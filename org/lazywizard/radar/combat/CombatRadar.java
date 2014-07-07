package org.lazywizard.radar.combat;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public interface CombatRadar
{
    public void resetView();

    /**
     *
     * @return The center of the radar circle, in screen coordinates.
     */
    public Vector2f getRenderCenter();
    public float getRenderRadius();
    public float getPixelsPerSU();
    public float getZoomLevel();

    public float getRadarAlpha();
    public float getContactAlpha();

    public Color getFriendlyContactColor();
    public Color getEnemyContactColor();
    public Color getNeutralContactColor();

    public ShipAPI getPlayer();

    public Vector2f getPointOnRadar(Vector2f worldLoc);
    public List<? extends CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts);
}
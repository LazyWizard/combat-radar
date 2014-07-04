package org.lazywizard.radar;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public interface RadarInfo
{
    public Vector2f getRenderCenter();
    public float getRenderRadius();

    public float getCenterAlpha();
    public float getEdgeAlpha();
    public float getContactAlpha();

    public Color getFriendlyContactColor();
    public Color getEnemyContactColor();
    public Color getNeutralContactColor();

    public ShipAPI getPlayer();

    public Vector2f getPointOnRadar(Vector2f worldCoords);
    public List<? extends CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> entities);
}

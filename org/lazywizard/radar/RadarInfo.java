package org.lazywizard.radar;

import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

public interface RadarInfo
{
    public Vector2f getRenderCenter();
    public float getRenderRadius();

    public float getCenterAlpha();
    public float getEdgeAlpha();

    public ShipAPI getPlayer();
}

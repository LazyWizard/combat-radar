package org.lazywizard.radar;

import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

// TODO: Javadoc this interface
interface BaseRadar<T>
{
    public void resetView();
    public void enableStencilTest();
    public void disableStencilTest();

    public Color getFriendlyContactColor();
    public Color getEnemyContactColor();
    public Color getNeutralContactColor();

    /**
     *
     * @return The center of the radar circle, in screen coordinates.
     */
    public Vector2f getRenderCenter();
    public float getRenderRadius();

    public float getRadarAlpha();
    public float getContactAlpha();

    // Used for scaling true-sized radar elements
    public float getCurrentPixelsPerSU();
    public float getCurrentZoomLevel();
    public float getCurrentSightRadius();
    public Vector2f getPointOnRadar(Vector2f worldLoc);
    public float[] getRawPointOnRadar(Vector2f worldLoc);
    public float[] getRawPointOnRadar(float worldX, float worldY);
    public float[] getRawPointsOnRadar(float[] worldCoords);

    public List filterVisible(List<? extends T> contacts, int maxContacts);
}

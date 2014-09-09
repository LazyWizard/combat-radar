package org.lazywizard.radar;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;

// TODO: Javadoc this interface
interface BaseRadar
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
    public Vector2f getPointOnRadar(Vector2f worldLoc);

    //public <T> List<T> filterVisible(List<T> contacts, int maxContacts);
}

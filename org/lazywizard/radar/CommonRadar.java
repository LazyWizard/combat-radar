package org.lazywizard.radar;

import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

/**
 * The master radar object. Provides information on the radar itself, as well as
 * convenience methods to access {@link RadarSettings} values or perform common
 * radar operations.
 * <p>
 * @author LazyWizard
 * @param <T> The <i>base</i> type of object this radar is responsible.
 * <p>
 * @since 2.0
 */
public interface CommonRadar<T>
{
    /**
     * Resets the glOrtho and glViewport to their pristine state. Call this at
     * the end of your rendering if you've called either of those methods.
     * <p>
     * @since 1.0
     */
    public void resetView();

    /**
     * Enables the radar stencil, which prevents anything from being rendered
     * outside of the radar circle. Use this to prevent shapes from protruding
     * outside of the radar.
     * <p>
     * @since 1.0
     */
    public void enableStencilTest();

    /**
     * Disables the radar stencil, allowing shapes to be rendered outside of the
     * radar circle. Call this at the end of your rendering if you've used
     * {@link CommonRadar#enableStencilTest()}, otherwise other renderers will
     * display graphical errors.
     * <p>
     * @since 1.0
     */
    public void disableStencilTest();

    /**
     * Returns the color of friendly contacts on the radar.
     * <p>
     * @return The default color of friendly contacts on the radar.
     * <p>
     * @since 1.0
     */
    public Color getFriendlyContactColor();

    /**
     * Returns the color of hostile contacts on the radar.
     * <p>
     * @return The default color of hostile contacts on the radar.
     * <p>
     * @since 1.0
     */
    public Color getEnemyContactColor();

    /**
     * Returns the color of neutral contacts on the radar.
     * <p>
     * @return The default color of neutral contacts on the radar.
     * <p>
     * @since 1.0
     */
    public Color getNeutralContactColor();

    /**
     * Returns the color of allied contacts on the radar.
     * <p>
     * @return The default color of allied contacts on the radar.
     * <p>
     * @since 2.2
     */
    public Color getAlliedContactColor();

    /**
     * Returns the center of the radar circle in screen coordinates.
     *
     * @return The center of the radar circle, in screen coordinates.
     * <p>
     * @since 1.0
     */
    public Vector2f getRenderCenter();

    /**
     * Returns the radius of the radar circle in screen coordinates.
     * <p>
     * @return The radius of the radar circle, in screen coordinates.
     * <p>
     * @since 1.0
     */
    public float getRenderRadius();

    /**
     * Returns the alpha modifier for radar UI elements.
     * <p>
     * @return The alpha modifier that should be applied to all radar UI
     *         elements.
     * <p>
     * @since 1.0
     */
    public float getRadarAlpha();

    /**
     * Returns the alpha modifier for radar contacts.
     * <p>
     * @return The alpha modifier that should be applied to all radar contacts.
     * <p>
     * @since 1.0
     */
    public float getContactAlpha();

    /**
     * Returns how many pixels are drawn per SU (Starsector's world unit). Used
     * for scaling true-sized radar elements. Equivalent to calling
     * <i>{@link CommonRadar#getRenderRadius()} /
     * {@link CommonRadar#getCurrentSightRadius()}</i>.
     *
     * @return How many pixels the radar uses per world unit. Will always be a
     *         decimal well below 1.
     * <p>
     * @since 1.0
     */
    public float getCurrentPixelsPerSU();

    /**
     * Returns the current zoom level of the radar.
     * <p>
     * @return The current zoom level of the radar. Will always be 1 or higher.
     * <p>
     * @since 1.0
     */
    public float getCurrentZoomLevel();

    /**
     * Returns how far the radar can see at its current zoom level.
     * <p>
     * @return How far the radar can see at its current zoom level, in SU
     *         (Starsector world units).
     * <p>
     * @since 2.0
     */
    public float getCurrentSightRadius();

    /**
     * Checks if a point in world space is visible on the radar.
     * <p>
     * @param worldLoc The point to check.
     * @param padding  Extra distance from {@code worldLoc} to include in the
     *                 check, usually an object's radius.
     * <p>
     * @return Whether {@code worldLoc} is within {@code padding} su of being
     *         visible on the radar.
     * <p>
     * @since 2.2
     */
    public boolean isPointOnRadar(Vector2f worldLoc, float padding);

    /**
     * Checks if a point in world space is visible on the radar.
     * <p>
     * @param worldLocX The X coordinate of the point to check.
     * @param worldLocY The Y coordinate of the point to check.
     * @param padding   Extra distance from {@code worldLoc} to include in the
     *                  check, usually an object's radius.
     * <p>
     * @return Whether the point at {{@code worldLocX},{@code wolrdLocY}} is
     *         within {@code padding} su of being visible on the radar.
     * <p>
     * @since 2.2
     */
    public boolean isPointOnRadar(float worldLocX, float worldLocY, float padding);

    /**
     * Converts a point from world space to radar space. Used to know where to
     * draw on the radar.
     * <p>
     * @param worldLoc The point in Starsector coordinates to translate.
     * <p>
     * @return {@code worldLoc} translated to radar coordinates.
     * <p>
     * @since 1.0
     */
    public Vector2f getPointOnRadar(Vector2f worldLoc);

    /**
     * Converts a point from world space to radar space. Used to know where to
     * draw on the radar.
     * <p>
     * @param worldLoc The point in Starsector coordinates to translate.
     * <p>
     * @return {@code worldLoc} translated to radar coordinates.
     * <p>
     * @since 2.0
     */
    public float[] getRawPointOnRadar(Vector2f worldLoc);

    /**
     * Converts a point from world space to radar space. Used to know where to
     * draw on the radar.
     * <p>
     * @param worldX The X coordinate of the point in Starsector coordinates to
     *               translate.
     * @param worldY The Y coordinate of the point in Starsector coordinates to
     *               translate.
     * <p>
     * @return The world location translated to radar coordinates.
     * <p>
     * @since 2.0
     */
    public float[] getRawPointOnRadar(float worldX, float worldY);

    /**
     * Converts multiple points from world space to radar space. Used to know
     * where to draw on the radar.
     * <p>
     * @param worldCoords The points in Starsector coordinates to translate, in
     *                    {x,y} pairs.
     * <p>
     * @return All points in {@code worldCoords} translated to radar
     *         coordinates, in {x,y} pairs.
     * <p>
     * @since 2.0
     */
    public float[] getRawPointsOnRadar(float[] worldCoords);

    /**
     * Filters out all contacts that aren't in radar range or are invisible to
     * the player (if {@link RadarSettings#isRespectingFogOfWar()} is
     * {@code true}).
     *
     * @param contacts    The list of contacts to be filtered.
     * @param maxContacts The maximum number of contacts to be retained. Use
     *                    this if your renderer has a setting to limit how many
     *                    contacts it will draw. Pass in {@code -1} for no
     *                    limit.
     * <p>
     * @return The filtered {@link List} of contacts.
     * <p>
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public List filterVisible(List<? extends T> contacts, int maxContacts);
}

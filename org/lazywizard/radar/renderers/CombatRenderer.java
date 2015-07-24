package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.radar.CommonRadar;

/**
 * The interface all combat radar plugins must implement.
 * <p>
 * @author LazyWizard
 * @since 1.0
 */
public interface CombatRenderer extends BaseRenderer<CombatEntityAPI>
{
    /**
     * Called every frame to tell your component to render. Rendering is done
     * using screen coordinates. If your code calls glOrtho() or glViewport(),
     * you should call {@link CommonRadar#resetView()} at the end of this
     * method.
     * <p>
     * @param player        The player's ship; also the center of the radar.
     *                      Will never be null.
     * @param amount        How long since the last frame, useful for animated
     *                      radar elements.
     * <p>
     * @param isUpdateFrame Whether the radar should update components this
     *                      frame, used so the radar can run at a different
     *                      framerate than Starsector.
     * <p>
     * @since 1.0
     */
    public void render(ShipAPI player, float amount, boolean isUpdateFrame);
}

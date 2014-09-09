package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.RadarModPlugin;

/**
 * The interface all combat radar plugins must implement.
 * <p>
 * @author LazyWizard
 * @since 1.0
 */
public interface CombatRenderer
{
    /**
     * Called when the game first loads or when
     * {@link RadarModPlugin#reloadSettings()} is called. You should set up
     * static variables such as colors here.
     * <p>
     * <b>IMPORTANT:</b> this method is called on a temporary object during
     * loading. Any variables you set in here must be static or they won't be
     * retained!
     * <p>
     * @param settings The contents of the settings file linked to in the radar
     *                 plugin CSV.
     * <p>
     * @throws JSONException
     * @since 1.0
     */
    public void reloadSettings(JSONObject settings) throws JSONException;

    /**
     * Called on the first frame of a new combat before rendering begins. You
     * should set up your component here.
     * <p>
     * @param radar The master radar object; you should keep track of this as
     *              many of its properties can change.
     * <p>
     * @since 1.0
     */
    public void init(CombatRadar radar);

    /**
     * Called every frame to tell your component to render. Rendering is done
     * using screen coordinates. If your code calls glOrtho() or glViewport(),
     * you should call {@link CombatRadar#resetView()} at the end of this
     * method.
     * <p>
     * @param player The player's ship; also the center of the radar. Will never
     *               be null.
     * @param amount How long since the last frame, useful for animated radar
     *               elements.
     * <p>
     * @since 1.0
     */
    public void render(ShipAPI player, float amount);
}

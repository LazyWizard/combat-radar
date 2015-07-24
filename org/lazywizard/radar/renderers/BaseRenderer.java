package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.plugins.RadarModPlugin;

/**
 * The basic interface all component renderers must implement. You shouldn't
 * implement this directly, instead use either the {@link CombatRenderer} or
 * {@link CampaignRenderer} convenience interface.
 *
 * @param <T> The <i>base</i> type of object the renderer will be drawing
 *            ({@link CombatEntityAPI} in combat or {@link SectorEntityToken} on
 *            the campaign map).
 * @param <P> The type of the player object ({@link ShipAPI} in combat or
 *            {@link CampaignFleetAPI} on the campaign map).
 * <p>
 * @author LazyWizard
 * @since 2.0
 */
public interface BaseRenderer<T, P>
{
    /**
     * {@link SectorEntityToken}s with this tag should be ignored by all
     * campaign renderers - not currently used in combat. If your renderer uses
     * {@link CommonRadar#filterVisible(java.util.List, int)}, entities with
     * this tag will already be filtered out for you.
     * <p>
     * @since 1.1e
     */
    public static final String NODRAW_TAG = "radar_nodraw";

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
     * Called on the first frame before rendering begins. You should set up your
     * renderer here.
     * <p>
     * @param radar The master radar object; you should keep track of this as
     *              many of its properties can change.
     * <p>
     * @since 1.0
     */
    public void init(CommonRadar<T> radar);

    /**
     * Called every frame to tell your component to render. Rendering is done
     * using screen coordinates. If your code calls glOrtho() or glViewport(),
     * you should call {@link CommonRadar#resetView()} at the end of this
     * method.
     * <p>
     * @param player        The player's object; also the center of the radar.
     *                      Will never be null.
     * @param amount        How long since the last frame, useful for animated
     *                      radar elements.
     * @param isUpdateFrame Whether the radar should update components this
     *                      frame, used so the radar can run at a lower
     *                      framerate than Starsector.
     * <p>
     * @since 1.0
     */
    public void render(P player, float amount, boolean isUpdateFrame);
}

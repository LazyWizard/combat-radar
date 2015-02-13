package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.CombatRadar;
import org.lazywizard.radar.RadarModPlugin;

public interface CampaignRenderer
{
    /**
     * {@link SectorEntityToken}s with this tag should be ignored by all
     * {@link CampaignRenderer}s. If your renderer uses
     * {@link CampaignRadar#filterVisible(java.util.List, int)}, entities with
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
     * component here.
     * <p>
     * @param radar The master radar object; you should keep track of this as
     *              many of its properties can change.
     * <p>
     * @since 1.0
     */
    public void init(CampaignRadar radar);

    /**
     * Called every frame to tell your component to render. Rendering is done
     * using screen coordinates. If your code calls glOrtho() or glViewport(),
     * you should call {@link CombatRadar#resetView()} at the end of this
     * method.
     * <p>
     * @param player The player's fleet; also the center of the radar. Will
     *               never be null.
     * @param amount How long since the last frame, useful for animated radar
     *               elements.
     * <p>
     * @since 1.0
     */
    public void render(CampaignFleetAPI player, float amount);
}

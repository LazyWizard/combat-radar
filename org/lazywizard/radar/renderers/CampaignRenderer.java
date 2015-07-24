package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lazywizard.radar.CampaignRadar;

public interface CampaignRenderer extends BaseRenderer
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
     * you should call {@link CampaignRadar#resetView()} at the end of this
     * method.
     * <p>
     * @param player        The player's fleet; also the center of the radar.
     *                      Will never be null.
     * @param amount        How long since the last frame, useful for animated
     *                      radar elements.
     * @param isUpdateFrame Whether the radar should update components this
     *                      frame, used so the radar can run at a different
     *                      framerate than Starsector.
     * <p>
     * @since 1.0
     */
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame);
}

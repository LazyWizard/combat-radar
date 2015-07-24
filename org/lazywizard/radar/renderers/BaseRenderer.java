package org.lazywizard.radar.renderers;

import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;

public interface BaseRenderer<T>
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
     * Called on the first frame before rendering begins. You should set up your
     * component here.
     * <p>
     * @param radar The master radar object; you should keep track of this as
     *              many of its properties can change.
     * <p>
     * @since 1.0
     */
    public void init(CommonRadar<T> radar);
}

package org.lazywizard.radar.renderers;

import org.json.JSONException;
import org.json.JSONObject;

public interface BaseRenderer
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
}

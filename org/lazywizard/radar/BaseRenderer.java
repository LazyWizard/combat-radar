package org.lazywizard.radar;

import org.json.JSONException;
import org.json.JSONObject;

public interface BaseRenderer
{
    public void reloadSettings(JSONObject settings) throws JSONException;

    public void init(RadarInfo radar);

    public void render(float amount);
}

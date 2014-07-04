package org.lazywizard.radar;

import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;

public interface BaseRenderer
{
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException;

    public void init(RadarInfo radar);

    public void render(ShipAPI player, float amount);
}

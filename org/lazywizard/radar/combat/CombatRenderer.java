package org.lazywizard.radar.combat;

import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;

public interface CombatRenderer
{
    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException;

    public void init(CombatRadar radar);

    public void render(ShipAPI player, float amount);
}

package org.lazywizard.radar.renderers.combat;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.renderers.BaseMemoryUsageRenderer;
import org.lazywizard.radar.renderers.CombatRenderer;

public class MemoryUsageRenderer extends BaseMemoryUsageRenderer implements CombatRenderer
{

    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
    }

    @Override
    public void init(CommonRadar<CombatEntityAPI> radar)
    {
        super.init(radar, 0f);
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
        super.render(isUpdateFrame);
    }
}

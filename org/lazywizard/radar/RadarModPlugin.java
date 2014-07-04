package org.lazywizard.radar;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import java.io.IOException;
import org.json.JSONException;

public class RadarModPlugin extends BaseModPlugin
{
    public static void reloadSettings() throws IOException, JSONException
    {
        CombatRadarPlugin.reloadSettings();
        //CampaignRadarPlugin.reloadSettings();
    }

    @Override
    public void onApplicationLoad() throws Exception
    {
        reloadSettings();
    }

    @Override
    public void onGameLoad()
    {
        //Global.getSector().addScript(new CampaignRadarPlugin());
    }
}

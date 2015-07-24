package org.lazywizard.radar.plugins;

import java.io.IOException;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.lazywizard.radar.RadarSettings;

// TODO: Unify combat and campaign codebases
// TODO: Add csv to prevent certain ships from being drawn
public class RadarModPlugin extends BaseModPlugin
{
    public static void reloadSettings() throws IOException, JSONException
    {
        RadarSettings.reloadSettings();
    }

    @Override
    public void onApplicationLoad() throws Exception
    {
        reloadSettings();
    }

    @Override
    public void onGameLoad()
    {
        Global.getSector().addTransientScript(new CampaignRadarPlugin());
    }
}
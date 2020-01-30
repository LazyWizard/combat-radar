package org.lazywizard.radar.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.lazywizard.radar.RadarSettings;

// TODO: Unify combat and campaign codebases
// TODO: Hyperspace range renderer
// TODO: Transponder status icon?
// TODO: Nearby patrol status icon?
// TODO: Make zoom level persistent between scenes
public class RadarModPlugin extends BaseModPlugin
{
    @Override
    public void onApplicationLoad() throws Exception
    {
        RadarSettings.reloadSettings();
    }

    @Override
    public void onGameLoad(boolean newGame)
    {
        if (!Global.getSettings().getBoolean("showCampaignRadar"))
        {
            Global.getSector().addTransientScript(new CampaignRadarPlugin());
        }
    }
}

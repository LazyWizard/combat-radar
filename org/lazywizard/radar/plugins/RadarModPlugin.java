package org.lazywizard.radar.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.lazywizard.radar.RadarSettings;

// TODO: Unify combat and campaign codebases
// TODO: Hyperspace range renderer
// TODO: Transponder status icon?
// TODO: Nearby patrol status icon?
// TODO: Add option to reverse direction of radar ring fade
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
        Global.getSector().addTransientScript(new CampaignRadarPlugin());
    }
}

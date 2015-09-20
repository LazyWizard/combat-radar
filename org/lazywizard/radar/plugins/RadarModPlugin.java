package org.lazywizard.radar.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.lazywizard.radar.RadarSettings;

// TODO: Unify combat and campaign codebases
public class RadarModPlugin extends BaseModPlugin
{
    @Override
    public void onApplicationLoad() throws Exception
    {
        RadarSettings.reloadSettings();
    }

    @Override
    public void onGameLoad()
    {
        Global.getSector().addTransientScript(new CampaignRadarPlugin());
    }
}

package org.lazywizard.radar.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.lazywizard.radar.RadarSettings;

// TODO: Unify combat and campaign codebases
// TODO: Hyperspace range renderer
// TODO: Add ally support to BattleProgressRenderer
// TODO: Transponder status icon?
// TODO: Nearby patrol status icon?
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

package org.lazywizard.radar;

import java.io.IOException;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.json.JSONException;

// TODO: Unify combat and campaign codebases
// TODO: Add csv to prevent certain ships from being drawn
public class RadarModPlugin extends BaseModPlugin
{
    public static void reloadSettings() throws IOException, JSONException
    {
        CombatRadarPlugin.reloadSettings();
        CampaignRadarPlugin.reloadSettings();
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

package org.lazywizard.radar;

import java.io.IOException;
import com.fs.starfarer.api.BaseModPlugin;
import org.json.JSONException;

// TODO: Unify combat and campaign codebases
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

    @Override
    public void beforeGameSave()
    {
        //Global.getSector().removeScriptsOfClass(CampaignRadarPlugin.class);
    }

    @Override
    public void afterGameSave()
    {
        //Global.getSector().addScript(new CampaignRadarPlugin());
    }
}

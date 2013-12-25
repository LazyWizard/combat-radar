package org.lazywizard.radar;

import com.fs.starfarer.api.BaseModPlugin;

public class RadarModPlugin extends BaseModPlugin
{
    @Override
    public void onApplicationLoad() throws Exception
    {
        CombatRadarPlugin.reloadSettings();
    }
}

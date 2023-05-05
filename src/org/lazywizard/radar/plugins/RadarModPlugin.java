package org.lazywizard.radar.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import org.lazywizard.radar.RadarSettings;

// TODO: Make zoom level persistent between battles
public class RadarModPlugin extends BaseModPlugin
{
    @Override
    public void onApplicationLoad() throws Exception
    {
        RadarSettings.reloadSettings();
    }
}

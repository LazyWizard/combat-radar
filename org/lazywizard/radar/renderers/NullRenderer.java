package org.lazywizard.radar.renderers;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CampaignRadar;
import org.lazywizard.radar.CombatRadar;

/**
 * A dummy implementation of CampaignRenderer and CombatRenderer.
 * Can be set as a renderer by a mod to completely disable a vanilla renderer.
 * For example: set ShipRenderer's script to NullRenderer if you are making your
 * own system to draw ships and don't want to use its ID for some reason.
 * <p>
 * @author LazyWizard
 * @since 2.0
 */
public class NullRenderer implements CampaignRenderer, CombatRenderer
{
    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
    }

    @Override
    public void init(CampaignRadar radar)
    {
    }

    @Override
    public void render(CampaignFleetAPI player, float amount, boolean isUpdateFrame)
    {
    }

    @Override
    public void init(CombatRadar radar)
    {
    }

    @Override
    public void render(ShipAPI player, float amount, boolean isUpdateFrame)
    {
    }
}

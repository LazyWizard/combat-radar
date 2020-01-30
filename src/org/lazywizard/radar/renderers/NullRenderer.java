package org.lazywizard.radar.renderers;

import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;

/**
 * A dummy implementation of BaseRenderer. Renderers assigned this script will
 * not actually be loaded by the radar.
 * <p>
 * Can be set as a renderer by a mod to completely disable a vanilla renderer.
 * For example: set ShipRenderer's script to NullRenderer if you are making your
 * own system to draw ships and don't want to use ShipRenderer as its ID for
 * some reason.
 * <p>
 * @author LazyWizard
 * @since 2.0
 */
public class NullRenderer implements BaseRenderer
{
    @Override
    public void reloadSettings(JSONObject settings) throws JSONException
    {
    }

    @Override
    public void init(CommonRadar radar)
    {
    }

    @Override
    public void render(Object player, float amount, boolean isUpdateFrame)
    {
    }
}

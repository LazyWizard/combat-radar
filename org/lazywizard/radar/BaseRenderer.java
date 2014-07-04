package org.lazywizard.radar;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

public interface BaseRenderer
{
    public interface RadarInfo
    {
        public Vector2f getRenderCenter();
        public float getRenderRadius();
        public float getScale();

        public float getRadarAlpha();
        public float getContactAlpha();

        public Color getFriendlyContactColor();
        public Color getEnemyContactColor();
        public Color getNeutralContactColor();

        public ShipAPI getPlayer();

        public Vector2f getPointOnRadar(Vector2f worldLoc);
        public List<? extends CombatEntityAPI> filterVisible(List<? extends CombatEntityAPI> contacts);
    }

    public void reloadSettings(JSONObject settings, boolean useVanillaColors) throws JSONException;

    public void init(RadarInfo radar);

    public void render(ShipAPI player, float amount);
}

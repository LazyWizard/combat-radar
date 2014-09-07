package org.lazywizard.radar;

import java.util.List;
import com.fs.starfarer.api.combat.CombatEntityAPI;

// TODO: Javadoc this interface
public interface CombatRadar extends BaseRadar
{
    public List<? extends CombatEntityAPI> filterVisible(
            List<? extends CombatEntityAPI> contacts, int maxContacts);
}
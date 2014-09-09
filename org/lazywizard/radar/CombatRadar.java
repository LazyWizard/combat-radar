package org.lazywizard.radar;

import java.util.List;
import com.fs.starfarer.api.combat.CombatEntityAPI;

public interface CombatRadar extends BaseRadar
{
    public List filterVisible(List<? extends CombatEntityAPI> contacts, int maxContacts);
}

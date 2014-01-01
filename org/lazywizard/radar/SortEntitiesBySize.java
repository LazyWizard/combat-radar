package org.lazywizard.radar;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import java.util.Comparator;

public class SortEntitiesBySize implements Comparator<CombatEntityAPI>
{
    @Override
    public int compare(CombatEntityAPI entity1, CombatEntityAPI entity2)
    {
        return Float.compare(entity1.getCollisionRadius(), entity2.getCollisionRadius());
    }
}

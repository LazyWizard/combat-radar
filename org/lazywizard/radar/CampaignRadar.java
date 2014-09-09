package org.lazywizard.radar;

import java.util.List;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface CampaignRadar extends BaseRadar
{
    public List filterVisible(List<? extends SectorEntityToken> contacts, int maxContacts);
}

package org.lazywizard.radar;

import java.util.List;
import com.fs.starfarer.api.campaign.SectorEntityToken;

// TODO: Javadoc this interface
public interface CampaignRadar extends BaseRadar
{
    public List<? extends SectorEntityToken> filterVisible(List<? extends SectorEntityToken> contacts, int maxContacts);
}
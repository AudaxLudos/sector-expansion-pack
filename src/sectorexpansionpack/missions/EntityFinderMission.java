package sectorexpansionpack.missions;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;

public class EntityFinderMission extends HubMissionWithSearch {
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        return false;
    }

    public void requireMarketCanUseSpecialItem(SpecialItemData specialItemData) {
        this.search.marketReqs.add(new ArtifactIncursionMission.MarketCanUseSpecialItemReq(specialItemData));
    }
}

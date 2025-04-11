package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;

public class SEPHubMission extends HubMissionWithBarEvent {
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        return false;
    }

    public void requireMarketHasInstalledItems() {
        this.search.marketReqs.add(new MarketRequirement() {
            @Override
            public boolean marketMatchesRequirement(MarketAPI market) {
                for (Industry ind : market.getIndustries()) {
                    if (!ind.getVisibleInstalledItems().isEmpty()) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void requireMarketMemoryFlagMissingOrFalse(String key) {
        this.search.marketReqs.add(new MarketRequirement() {
            @Override
            public boolean marketMatchesRequirement(MarketAPI market) {
                boolean val = market.getMemoryWithoutUpdate().getBoolean(key);
                return !val;
            }
        });
    }
}

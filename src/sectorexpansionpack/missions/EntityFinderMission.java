package sectorexpansionpack.missions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.hub.SEPHubMissionWithBarEvent;

import java.util.Random;

public class EntityFinderMission extends SEPHubMissionWithBarEvent {
    public EntityFinderMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        return false;
    }
}

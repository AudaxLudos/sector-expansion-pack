package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

public class GenericReturnStage extends GenericTravelStage {
    public GenericReturnStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
        super(raid, from, to, requireNearTarget);
    }
}

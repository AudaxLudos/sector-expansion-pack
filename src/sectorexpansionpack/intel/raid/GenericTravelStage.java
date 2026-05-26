package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GenericTravelStage extends TravelStage {
    protected final Object delegate;

    public GenericTravelStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
        super(raid, from, to, requireNearTarget);
        this.delegate = raid;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        if (this.delegate instanceof GenericOrganizeStage.ShowStageInfoDelegate delegate1) {
            delegate1.showStageInfo(info, this, this.status);
        } else {
            super.showStageInfo(info);
        }
    }

    public SectorEntityToken getFrom() {
        return this.from;
    }

    public SectorEntityToken getTo() {
        return this.to;
    }
}

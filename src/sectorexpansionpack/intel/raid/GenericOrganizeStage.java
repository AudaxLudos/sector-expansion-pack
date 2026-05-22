package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class GenericOrganizeStage extends OrganizeStage {
    protected Object delegate;

    public GenericOrganizeStage(RaidIntel raid, MarketAPI market, float durDays) {
        super(raid, market, durDays);
        this.delegate = raid;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        if (this.delegate instanceof ShowStageInfoDelegate delegate1) {
            delegate1.showStageInfo(info, this, this.status);
        } else {
            super.showStageInfo(info);
        }
    }

    public interface ShowStageInfoDelegate {
        void showStageInfo(TooltipMakerAPI info, RaidIntel.RaidStage raidStage, RaidIntel.RaidStageStatus status);
    }
}

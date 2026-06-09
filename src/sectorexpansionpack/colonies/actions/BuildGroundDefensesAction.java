package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildGroundDefensesAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.GROUNDDEFENSES;
    }
}

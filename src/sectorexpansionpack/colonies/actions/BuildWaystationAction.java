package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildWaystationAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.WAYSTATION;
    }

    @Override
    public float getPriority() {
        return 1f;
    }
}

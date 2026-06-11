package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildPatrolHQAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.PATROLHQ;
    }

    @Override
    public float getPriority() {
        return 2f;
    }
}

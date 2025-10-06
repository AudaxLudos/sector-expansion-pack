package sectorexpansionpack.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

public class LeakedArtifactLocationIntel extends BaseIntelPlugin {
    protected LeakedData leakedData;

    public LeakedArtifactLocationIntel(LeakedData leakedData) {
        this.leakedData = leakedData;

        setPostingRangeLY(leakedData.postingRangeLY, true);
        setPostingLocation(leakedData.postingLoc);

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    protected String getName() {
        return "Leaked Artifact Location";
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 15f;
    }

    public static class LeakedData {
        public String actionId;
        public SectorEntityToken postingLoc;
        public float postingRangeLY;
    }
}

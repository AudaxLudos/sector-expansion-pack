package sectorexpansionpack.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;

public class MatrixCatalyst extends BaseIndustry {
    @Override
    public void apply() {
        super.apply(false);
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSector().getPlayerFaction().knowsIndustry(getId());
    }

    public boolean showWhenUnavailable() {
        return Global.getSector().getPlayerFaction().knowsIndustry(getId());
    }
}

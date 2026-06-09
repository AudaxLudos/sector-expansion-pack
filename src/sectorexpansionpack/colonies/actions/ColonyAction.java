package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public abstract class ColonyAction {
    public abstract String name();

    public abstract float priority();

    public abstract boolean canPerform(MarketAPI market);

    public abstract void perform(MarketAPI market);
}

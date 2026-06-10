package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public abstract class ColonyAction {
    /**
     * Identifier for an action, will mostly be used for debugging
     *
     * @return returns a constructed string from id
     */
    public abstract String id();

    public abstract String name();

    public abstract float priority();

    public abstract boolean canPerform(MarketAPI market);

    public abstract void perform(MarketAPI market);
}

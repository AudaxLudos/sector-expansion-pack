package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

public abstract class ColonyAction {
    public static Logger log = Global.getLogger(ColonyAction.class);

    /**
     * Identifier for an action, will mostly be used for debugging
     *
     * @return returns a constructed string from id
     */
    public abstract String getId();

    public abstract String getName();

    /**
     * @return a value between 0 - 9, lower is more important higher is less
     */
    public float getPriority() {
        return 0f;
    }

    public abstract boolean canPerform(MarketAPI market);

    public abstract void perform(MarketAPI market);
}

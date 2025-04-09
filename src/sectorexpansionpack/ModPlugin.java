package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import sectorexpansionpack.intel.group.ExpeditionManager;
import sectorexpansionpack.intel.group.IncursionManager;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        // throw new Exception("Sector Expansion Pack mod is working");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        ExpeditionManager.register();
        IncursionManager.register();
    }
}

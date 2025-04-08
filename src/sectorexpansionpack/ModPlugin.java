package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import sectorexpansionpack.intel.group.ExpeditionManager;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        // throw new Exception("Sector Expansion Pack mod is working");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        ExpeditionManager.register();
    }
}

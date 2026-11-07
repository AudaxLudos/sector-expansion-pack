package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhostManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.colonies.FactionColonyAI;
import sectorexpansionpack.colonies.actions.*;
import sectorexpansionpack.ghosts.types.FleetEaterGhostCreator;
import sectorexpansionpack.ghosts.types.StormInducerGhostCreator;
import sectorexpansionpack.ghosts.types.StormPacifierGhostCreator;
import sectorexpansionpack.intel.ClearDebrisFieldsIntelCreator;
import sectorexpansionpack.intel.ConstructObjectiveIntelCreator;
import sectorexpansionpack.intel.raid.AcquisitionRaidManager;
import sectorexpansionpack.intel.raid.ExcavationRaidManager;
import sectorexpansionpack.listeners.MatrixCatalystBlueprintAdder;
import sectorexpansionpack.listeners.MatrixCatalystOptionProvider;
import sectorexpansionpack.loading.SpecStore;

public class ModPlugin extends BaseModPlugin {
    public static boolean STATIC_DATA_INITIALIZED = false;

    @Override
    public void onApplicationLoad() {
        SpecStore.LoadMissionScenarioSpecs();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        addScriptsIfNeeded();
    }

    public void addScriptsIfNeeded() {
        // Mod settings
        Utils.setRandom();
        Settings.load();

        // Scripts
        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ExcavationRaidManager.class)) {
            sector.addScript(new ExcavationRaidManager());
        }
        if (!sector.hasScript(AcquisitionRaidManager.class)) {
            sector.addScript(new AcquisitionRaidManager());
        }
        if (Settings.FACTION_COLONY_AIS_ENABLED) {
            for (FactionAPI faction : FactionColonyAI.getFactionsWithMarkets()) {
                FactionColonyAI currAI = FactionColonyAI.getFactionColonyAI(faction);
                if (currAI == null) {
                    float minInterval = 30f;
                    float maxInterval = 90f;
                    if (faction.getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
                        minInterval = 60f;
                        maxInterval = 120f;
                    }
                    FactionColonyAI newAI = new FactionColonyAI(faction, minInterval, maxInterval);
                    sector.addScript(newAI);
                    FactionColonyAI.getFactionColonyAIs().put(faction.getId(), newAI);
                }
            }
        } else {
            for (FactionColonyAI ai : FactionColonyAI.getFactionColonyAIs().values()) {
                sector.removeScript(ai);
                ai.endImmediately();
            }
            FactionColonyAI.getFactionColonyAIs().clear();
        }

        GenericMissionManager genericMissionManager = GenericMissionManager.getInstance();
        if (!genericMissionManager.hasMissionCreator(ClearDebrisFieldsIntelCreator.class)) {
            genericMissionManager.addMissionCreator(new ClearDebrisFieldsIntelCreator());
        }
        if (!genericMissionManager.hasMissionCreator(ConstructObjectiveIntelCreator.class)) {
            genericMissionManager.addMissionCreator(new ConstructObjectiveIntelCreator());
        }

        // Campaign input listeners
        ListenerManagerAPI listenerManager = Global.getSector().getListenerManager();
        if (!listenerManager.hasListenerOfClass(MatrixCatalystOptionProvider.class)) {
            listenerManager.addListener(new MatrixCatalystOptionProvider(), true);
        }

        // Campaign event listeners
        if (!Global.getSector().getPlayerFaction().knowsIndustry("sep_matrix_catalyst")) {
            Global.getSector().addTransientListener(new MatrixCatalystBlueprintAdder());
        }

        // Static variables that need initializing
        if (!STATIC_DATA_INITIALIZED) {
            SensorGhostManager.CREATORS.add(new StormPacifierGhostCreator());
            SensorGhostManager.CREATORS.add(new StormInducerGhostCreator());
            SensorGhostManager.CREATORS.add(new FleetEaterGhostCreator());

            // Colony AI build actions
            FactionColonyAI.COLONY_ACTIONS.add(new BuildFarmingAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildFuelProductionAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildGroundDefensesAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildHeavyIndustryAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildLightIndustryAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildMiningAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildOrbitalStationAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildPatrolHQAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildRefiningAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildSpaceportAction());
            FactionColonyAI.COLONY_ACTIONS.add(new BuildWaystationAction());
            String id = "temp_market_" + Misc.genUID();
            MarketAPI tempMarket = Global.getFactory().createMarket("sep_" + id, id, 6);
            tempMarket.setFactionId(Factions.NEUTRAL);
            // Colony AI upgrade actions
            for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()) {
                tempMarket.addIndustry(spec.getId());
                if (spec.getUpgrade() != null && !spec.getUpgrade().isBlank()) {
                    FactionColonyAI.COLONY_ACTIONS.add(new ColonyUpgradeAction(spec.getId()));
                }
            }
            // Colony AI improve actions
            for (Industry industry : tempMarket.getIndustries()) {
                if (industry.canImprove()) {
                    FactionColonyAI.COLONY_ACTIONS.add(new ColonyImproveAction(industry.getId()));
                }
            }
            STATIC_DATA_INITIALIZED = true;
        }
    }
}

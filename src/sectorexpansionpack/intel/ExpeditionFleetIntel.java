package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;
import sectorexpansionpack.intel.misc.ExpeditionFleetDepartureIntel;
import sectorexpansionpack.intel.misc.LeakedArtifactLocationIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Map;

// Won't be seen by the player
// Will be used to send out departure and leak intel
// Should replace with custom RouteFleetSpawner to make it less heavy but this works
public class ExpeditionFleetIntel extends FleetGroupIntel {
    public static final String EVENT_KEY = "$sep_efi_eventRef";
    public static final String FACTION_KEY = "$sep_efi_sourceFaction";
    public static final String MAIN_FLEET_KEY = "$sep_efi_mainFleet";
    public static final String SUPPLY_FLEET_KEY = "$sep_efi_supplyFleet";
    public static final String TARGET_KEY = "$sep_efi_target";
    public static Logger log = Global.getLogger(ExpeditionFleetIntel.class);
    public static String PREPARE_ACTION = "prepare_action";
    public static String GOTO_ACTION = "travel_action";
    public static String LOOT_ACTION = "loot_action";
    public static String RETURN_ACTION = "return_action";
    public static String DOCK_ACTION = "dock_action";

    protected float revealChance = 0.2f;
    protected boolean isLeaked = false;
    protected String factionId;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketAPI source;
    protected SectorEntityToken target;

    public ExpeditionFleetIntel() {
        setRandom(Utils.random);
        pickSpecialItem();
        if (isDone()) {
            log.info("Failed to get special item");
            return;
        }
        pickFactionId();
        if (isDone()) {
            log.info("Failed to find faction");
            return;
        }
        pickMarket();
        if (isDone()) {
            log.info("Failed to find source market");
            return;
        }
        pickTarget();
        if (isDone()) {
            log.info("Failed to find target entity");
            return;
        }

        setFaction(this.factionId);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "preparing for expedition"), PREPARE_ACTION);
        addAction(new FGTravelAction(this.source.getPrimaryEntity(), this.target.getStarSystem().getCenter()), GOTO_ACTION);
        addAction(new FGWaitAction(this.target, 30f, "exploring " + this.target.getName()), LOOT_ACTION);
        addAction(new FGTravelAction(this.target, this.source.getPrimaryEntity()), RETURN_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "Docking to " + this.source.getName()), DOCK_ACTION);

        createRoute(this.factionId, 10, 1, null);
        getRoute().setDelay((float) (3f + Math.random() * 6f));
        log.info(String.format("Creating expedition fleet %s %s in the %s that will goto %s",
                this.source.getOnOrAt(), this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getStarSystem().getNameWithLowercaseTypeShort()));

        // Mark source faction so it won't be reselected for future expeditions
        this.source.getFaction().getMemoryWithoutUpdate().set(FACTION_KEY, true);

        // Mark target so it won't be reselected for future expeditions
        Misc.makeImportant(this.target, "specialItemLocation");
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);
        Misc.setSalvageSpecial(this.target, new SEPHiddenItemSpecial.HiddenSpecialItemSpecialData(this.specialItemSpec.getId()));

        if (Utils.rollProbability(this.revealChance)) {
            new ExpeditionFleetDepartureIntel(getRoute(), this.source);
        } else {
            this.revealChance += 0.2f;
        }
    }

    public void pickSpecialItem() {
        WeightedRandomPicker<SpecialItemSpecAPI> specialItemPicker = new WeightedRandomPicker<>(getRandom());
        specialItemPicker.addAll(Global.getSettings().getAllSpecialItemSpecs());

        this.specialItemSpec = specialItemPicker.pick();
        if (this.specialItemSpec == null) {
            endImmediately();
        }
        this.specialItemData = new SpecialItemData(this.specialItemSpec.getId(), null);
    }

    public void pickFactionId() {
        WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(getRandom());
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction.getMemoryWithoutUpdate().getBoolean(ExpeditionFleetIntel.FACTION_KEY)) {
                continue;
            }
            if (!faction.isShowInIntelTab()) {
                continue;
            }
            factionPicker.add(faction.getId());
        }

        this.factionId = factionPicker.pick();
        if (this.factionId == null || this.factionId.isEmpty()) {
            endImmediately();
        }
    }

    public void pickMarket() {
        EntityFinderMission efm = new EntityFinderMission();
        efm.requireMarketFaction(this.factionId);
        efm.requireMarketNotHidden();
        efm.requireMarketFactionNotPlayer();
        efm.requireMarketStabilityAtLeast(8);
        efm.requireMarketCanUseSpecialItem(this.specialItemData);
        this.source = efm.pickMarket();
        if (this.source == null) {
            endImmediately();
        }
    }

    public void pickTarget() {
        EntityFinderMission efm = new EntityFinderMission();
        efm.requirePlanetNoMemoryFlag(ExpeditionFleetIntel.TARGET_KEY);
        efm.requirePlanetWithRuins();
        efm.requirePlanetUnexploredRuins();
        efm.preferPlanetInDirectionOfOtherMissions();
        this.target = efm.pickPlanet();
        if (this.target == null) {
            endImmediately();
        }
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        if (action == null) {
            return;
        }

        if (action.getId().equals(LOOT_ACTION)) {
            Misc.makeUnimportant(this.target, "specialItemLocation");
            this.target.getMemoryWithoutUpdate().unset(MemFlags.SALVAGE_SPECIAL_DATA);

            // TODO: Make fleet aggressive and defensive when they reach the location
            CampaignFleetAPI mainFleet = getMainFleet();
            if (mainFleet != null) {
                Misc.makeImportant(mainFleet, "hasSpecialItem");
                Misc.addDefeatTrigger(mainFleet, "SEPEFGIFleetDefeated");
            }
        } else if (action.getId().equals(DOCK_ACTION)) {
            EntityFinderMission efm = new EntityFinderMission();
            efm.requireMarketFaction(this.factionId);
            efm.requireMarketNotHidden();
            efm.requireMarketNotInHyperspace();
            efm.requireMarketFactionNotPlayer();
            efm.requireMarketCanUseSpecialItem(this.specialItemData);
            efm.preferMarketSizeAtMost(100);
            efm.preferMarketIs(this.source);
            MarketAPI market = efm.pickMarket();

            if (market == null) {
                log.info("Failed to find market to install special item");
                return;
            }

            // TODO: Delay installation by a few days
            Industry ind = pickIndustryToInstallItem(market, this.specialItemData);
            ind.setSpecialItem(this.specialItemData);
            new ArtifactInstallationIntel(market, ind, this.specialItemSpec);
            log.info(String.format("Installing %s to %s facility %s %s in the %s",
                    this.specialItemSpec.getName(), ind.getCurrentName(), market.getOnOrAt(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort()));
        }

        if (!this.isLeaked && !PREPARE_ACTION.equals(action.getId()) && !DOCK_ACTION.equals(action.getId())) {
            if (Utils.rollProbability(this.revealChance)) {
                this.isLeaked = true;

                new LeakedArtifactLocationIntel(action.getId(), this.source, this.target, this);
                log.info(String.format("Leaking expedition intel at %s in the %s",
                        this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort()));
            } else {
                this.revealChance += 0.2f;
            }
        }
    }

    public Industry pickIndustryToInstallItem(MarketAPI market, SpecialItemData specialItemData) {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : market.getIndustries()) {
            if (industry.wantsToUseSpecialItem(specialItemData)) {
                industryPicker.add(industry);
            }
        }
        return industryPicker.pick();
    }

    @Override
    protected void notifyEnded() {
        // Unset faction memory flags
        Global.getSector().getFaction(this.factionId).getMemoryWithoutUpdate().unset(FACTION_KEY);

        // Unset target memory flags
        if (this.target != null) {
            Misc.makeUnimportant(this.target, "specialItemLocation");
            this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
            this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
        }

        // Unset fleet memory flags
        CampaignFleetAPI mainFleet = getMainFleet();
        if (mainFleet != null) {
            Misc.makeUnimportant(mainFleet, "hasSpecialItem");
            mainFleet.getMemoryWithoutUpdate().unset(MAIN_FLEET_KEY);
            mainFleet.getMemoryWithoutUpdate().unset(EVENT_KEY);
        }
    }

    @Override
    protected boolean shouldAbort() {
        return isSpawnedFleets() && getMainFleet() == null;
    }

    @Override
    protected boolean isPlayerTargeted() {
        return false;
    }

    @Override
    protected void spawnFleets() {
        // TODO: Add supply fleets
        FleetCreatorMission fcm = new FleetCreatorMission(getRandom());

        fcm.beginFleet();
        fcm.setGenRandom(Utils.random);
        fcm.createFleet(FleetCreatorMission.FleetStyle.QUALITY, 10, this.factionId, this.source.getLocationInHyperspace());
        fcm.setFleetSource(this.source);
        fcm.triggerMakeLowRepImpact();
        fcm.triggerSetFleetFlag(MAIN_FLEET_KEY);
        fcm.triggerSetFleetMemoryValue(EVENT_KEY, this);
        fcm.triggerFleetSetName("Expedition Fleet");

        CampaignFleetAPI fleet = fcm.createFleet();
        if (fleet != null && this.route != null) {
            setLocationAndCoordinates(fleet, this.route.getCurrent());
            if (fleet.getMemoryWithoutUpdate().getBoolean(MAIN_FLEET_KEY)) {
                if (getAction(LOOT_ACTION) == null) {
                    // Ensure fleet is marked properly when it spawns midway
                    Misc.makeImportant(fleet, "hasSpecialItem");
                    Misc.addDefeatTrigger(fleet, "SEPEFGIFleetDefeated");
                }
            }
            this.fleets.add(fleet);
        }
    }

    @Override
    protected SectorEntityToken getSource() {
        return this.source.getPrimaryEntity();
    }

    @Override
    protected SectorEntityToken getDestination() {
        return this.target;
    }

    @Override
    protected String getBaseName() {
        return "Artifact Expedition";
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        // Won't be seen by the player
    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        // Won't be seen by the player
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("removeDefeatTrigger")) {
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    public CampaignFleetAPI getMainFleet() {
        if (isSpawnedFleets()) {
            for (CampaignFleetAPI fleet : getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean(MAIN_FLEET_KEY)) {
                    return fleet;
                }
            }
        }

        return null;
    }
}

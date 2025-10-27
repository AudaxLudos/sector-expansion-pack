package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.group.*;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;
import sectorexpansionpack.intel.misc.ExpeditionFleetDepartureIntel;
import sectorexpansionpack.intel.misc.LeakedArtifactLocationIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

// IDEA: Create a base expedition class then create a generic expedition intel
// Won't be seen by the player
// Will be used to send out departure and leak intel
// Should replace with custom RouteFleetSpawner to make it less heavy but this works
// Could make a BaseHubEvent with the same functionality as BaseHubMission but NO
public class ExpeditionFleetIntel extends FleetGroupIntel {
    public static final String PREPARE_ACTION = "prepare_action";
    public static final String GOTO_ACTION = "travel_action";
    public static final String LOOT_ACTION = "loot_action";
    public static final String RETURN_ACTION = "return_action";
    public static final String DOCK_ACTION = "dock_action";
    public static final String EVENT_KEY = "$sep_efi_ref";
    public static final String FACTION_KEY = "$sep_efi_sourceFaction";
    public static final String FLEET_KEY = "$sep_efi_fleet";
    public static final String MAIN_FLEET_KEY = "$sep_efi_mainFleet";
    public static final String TARGET_KEY = "$sep_efi_target";
    public static final String GUARDED_KEY = "$sep_efi_targetGuarded";
    public static final String HAS_ARTIFACT = "$sep_efi_hasArtifact";
    public static final float WRECK_CHANCE = 0.5f;
    public static Logger log = Global.getLogger(ExpeditionFleetIntel.class);

    protected GenericRaidFGI.GenericRaidParams params;
    protected Integer maxFleetSize = 0;
    protected HubMissionWithTriggers.FleetQuality maxFleetQuality = HubMissionWithTriggers.FleetQuality.DEFAULT;
    protected EntityFinderMission efm;
    protected float revealChance = 0.2f;
    protected boolean isLeaked = false;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketAPI source;
    protected SectorEntityToken target;

    public ExpeditionFleetIntel() {
        setRandom(new Random(Utils.random.nextLong()));
        this.efm = new EntityFinderMission();
        pickSpecialItem();
        if (isDone()) {
            log.info("Failed to get special item");
            return;
        }
        pickFaction();
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

        this.params = new GenericRaidFGI.GenericRaidParams(getRandom(), false);
        this.params.factionId = getFaction().getId();
        this.params.source = this.source;

        float baseDifficulty = 6f;
        if (this.source.getSize() <= 4) {
            this.maxFleetSize = 6;
        } else if (this.source.getSize() <= 6) {
            baseDifficulty = 8f;
            this.maxFleetSize = 8;
        } else {
            baseDifficulty = 12f;
            this.maxFleetSize = 10;
        }

        float difficultyMult = this.source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        if (difficultyMult < 1f) {
            difficultyMult = 1f;
        }

        float totalDifficulty = baseDifficulty * difficultyMult;

        this.params.fleetSizes.add(this.maxFleetSize);
        totalDifficulty -= this.maxFleetSize;

        while (totalDifficulty > 0) {
            int min = 3;
            int max = this.maxFleetSize - 2;
            int diff = min + getRandom().nextInt(max - min + 1);

            this.params.fleetSizes.add(diff);
            totalDifficulty -= diff;
        }

        initActions();

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

        log.info(String.format("Starting %s expedition at %s in the %s, targeting %s in the %s",
                this.source.getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), this.target.getStarSystem().getNameWithLowercaseTypeShort()));
    }

    protected void initActions() {
        setFaction(this.params.factionId);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "preparing for expedition"), PREPARE_ACTION);
        addAction(new FGTravelAction(this.source.getPrimaryEntity(), this.target.getStarSystem().getCenter()), GOTO_ACTION);
        addAction(new FGWaitAction(this.target, 30f, "exploring " + this.target.getName()), LOOT_ACTION);
        addAction(new FGTravelAction(this.target, this.source.getPrimaryEntity()), RETURN_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "Docking to " + this.source.getName()), DOCK_ACTION);

        int total = 0;
        for (Integer i : this.params.fleetSizes) {
            total += i;
        }

        createRoute(this.params.factionId, total, this.params.fleetSizes.size(), null, this.params);
        getRoute().setDelay((float) (3f + Math.random() * 6f));
    }

    public void pickSpecialItem() {
        WeightedRandomPicker<SpecialItemSpecAPI> specialItemPicker = new WeightedRandomPicker<>(getRandom());
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            // TODO: Filter modded colony items that is player use only or has demand effects
            if (Objects.equals(spec.getId(), Items.CORONAL_PORTAL)
                    || Objects.equals(spec.getId(), Items.ORBITAL_FUSION_LAMP)) {
                continue;
            }
            specialItemPicker.add(spec);
        }

        this.specialItemSpec = specialItemPicker.pick();
        if (this.specialItemSpec == null) {
            endImmediately();
        }
        this.specialItemData = new SpecialItemData(this.specialItemSpec.getId(), null);
    }

    public void pickFaction() {
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker<>(getRandom());
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction.getMemoryWithoutUpdate().getBoolean(ExpeditionFleetIntel.FACTION_KEY)) {
                continue;
            }
            if (!faction.isShowInIntelTab()) {
                continue;
            }
            factionPicker.add(faction);
        }

        setFaction(factionPicker.pick());
        if (getFaction() == null) {
            endImmediately();
        }
    }

    public void pickMarket() {
        this.efm.requireMarketFaction(getFaction().getId());
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketFactionNotPlayer();
        this.efm.requireMarketStabilityAtLeast(8);
        this.efm.requireMarketCanUseSpecialItem(this.specialItemData);
        this.source = this.efm.pickMarket();
        if (this.source == null) {
            endImmediately();
        }
    }

    public void pickTarget() {
        if (this.efm.rollProbability(WRECK_CHANCE)) {
            this.efm.requireEntityNoMemoryFlag(ExpeditionFleetIntel.TARGET_KEY);
            this.efm.requireEntityNoSpecialSalvage();
            this.efm.requireEntityType(Entities.WRECK);
            this.efm.preferEntityInDirectionOfOtherMissions();
            this.efm.preferEntityUndiscovered();
            this.target = this.efm.pickEntity();
        } else {
            this.efm.requirePlanetNoMemoryFlag(ExpeditionFleetIntel.TARGET_KEY);
            this.efm.requirePlanetWithRuins();
            this.efm.requirePlanetUnexploredRuins();
            this.efm.preferPlanetInDirectionOfOtherMissions();
            this.target = this.efm.pickPlanet();
        }

        if (this.target == null) {
            endImmediately();
        }
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        if (action == null) {
            return;
        }

        if (GOTO_ACTION.equals(action.getId())) {
            this.target.getMemoryWithoutUpdate().set(GUARDED_KEY, true);
        } else if (LOOT_ACTION.equals(action.getId())) {
            Misc.makeUnimportant(this.target, "specialItemLocation");
            this.target.getMemoryWithoutUpdate().unset(MemFlags.SALVAGE_SPECIAL_DATA);
            this.target.getMemoryWithoutUpdate().unset(GUARDED_KEY);

            CampaignFleetAPI mainFleet = getMainFleet();
            if (mainFleet != null) {
                Misc.makeImportant(mainFleet, "hasSpecialItem");
                Misc.addDefeatTrigger(mainFleet, "SEPEFGIFleetDefeated");
                mainFleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
            }
        } else if (DOCK_ACTION.equals(action.getId())) {
            this.efm.resetSearch();
            this.efm.requireMarketFaction(getFaction().getId());
            this.efm.requireMarketNotHidden();
            this.efm.requireMarketNotInHyperspace();
            this.efm.requireMarketFactionNotPlayer();
            this.efm.requireMarketCanUseSpecialItem(this.specialItemData);
            this.efm.preferMarketSizeAtMost(100);
            this.efm.preferMarketIs(this.source);
            MarketAPI market = this.efm.pickMarket();

            if (market == null) {
                log.info("Failed to find market to install special item");
                return;
            }

            // IDEA: Create a courier fleet that transfers the colony item to another faction market if the source market can't use it
            // TODO: Delay installation by a few days
            Industry ind = Utils.pickIndustryToInstallItem(market, this.specialItemData);
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
                log.info(String.format("Leaking %s expedition intel at %s in the %s",
                        getFaction().getDisplayName(), this.source.getName(),
                        this.source.getStarSystem().getNameWithLowercaseTypeShort()));
            } else {
                this.revealChance += 0.2f;
            }
        }
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        unsetEventMemoryFlags();

        if (this.endingTimeRemaining > 0f) {
            log.info(String.format("Ending %s expedition event", this.source.getFaction().getDisplayName()));
        }
    }

    public void unsetEventMemoryFlags() {
        // Unset faction memory flags
        if (getFaction() != null) {
            getFaction().getMemoryWithoutUpdate().unset(FACTION_KEY);
        }

        // Unset target memory flags
        if (this.target != null) {
            Misc.makeUnimportant(this.target, "specialItemLocation");
            this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
            this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
        }

        // Unset fleets memory flags
        for (CampaignFleetAPI fleet : this.fleets) {
            Misc.makeUnimportant(fleet, "hasSpecialItem");
            fleet.getMemoryWithoutUpdate().unset(FLEET_KEY);
            fleet.getMemoryWithoutUpdate().unset(MAIN_FLEET_KEY);
            fleet.getMemoryWithoutUpdate().unset(HAS_ARTIFACT);
            fleet.getMemoryWithoutUpdate().unset(EVENT_KEY);
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
        Float damage = null;
        if (this.route != null && this.route.getExtra() != null) {
            damage = this.route.getExtra().damage;
        }
        if (damage == null) {
            damage = 0f;
        }

        WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>(getRandom());
        picker.addAll(this.params.fleetSizes);

        int total = 0;
        for (Integer i : this.params.fleetSizes) {
            total += i;
        }

        float spawnsToSkip = total * damage * 0.5f;
        float skipped = 0f;

        while (!picker.isEmpty()) {
            Integer size = picker.pickAndRemove();
            if (skipped < spawnsToSkip && getRandom().nextFloat() < damage) {
                skipped += size;
                continue;
            }

            CampaignFleetAPI fleet = createFleet(size, damage);

            if (fleet != null && this.route != null) {
                setLocationAndCoordinates(fleet, this.route.getCurrent());
                this.fleets.add(fleet);
            }
        }
    }

    protected CampaignFleetAPI createFleet(int size, float damage) {
        Vector2f loc = this.params.source.getLocationInHyperspace();
        boolean pirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);

        FleetCreatorMission m = new FleetCreatorMission(getRandom());

        preConfigureFleet(size, m);

        m.beginFleet();

        String factionId = getFleetCreationFactionOverride(size);
        if (factionId == null) {
            factionId = this.params.factionId;
        }

        m.createFleet(this.params.style, size, factionId, loc);
        m.triggerSetFleetFaction(this.params.factionId);

        m.setFleetSource(this.params.source);
        setFleetCreatorQualityFromRoute(m);
        m.setFleetDamageTaken(damage);

        if (pirate) {
            m.triggerSetPirateFleet();
        } else {
            m.triggerSetWarFleet();
        }

        if (Factions.LUDDIC_PATH.equals(getFaction().getId())) {
            m.triggerFleetPatherNoDefaultTithe();
        }

        /*if (params.makeFleetsHostile) {
            for (MarketAPI market : params.raidParams.allowedTargets) {
                m.triggerMakeHostileToFaction(market.getFactionId());
            }
            m.triggerMakeHostile();
            if (Factions.LUDDIC_PATH.equals(faction.getId())) {
                m.triggerFleetPatherNoDefaultTithe();
            }
        }

        if (params.repImpact == ComplicationRepImpact.LOW || params.repImpact == null) {
            m.triggerMakeLowRepImpact();
        } else if (params.repImpact == ComplicationRepImpact.NONE) {
            m.triggerMakeNoRepImpact();
        }

        if (params.repImpact != ComplicationRepImpact.FULL) {
            m.triggerMakeAlwaysSpreadTOffHostility();
        }*/

        configureFleet(size, m);

        CampaignFleetAPI fleet = m.createFleet();
        if (fleet != null) {
            configureFleet(size, fleet);
        }

        return fleet;
    }

    protected String getFleetCreationFactionOverride(int size) {
        return null;
    }

    protected void preConfigureFleet(int size, FleetCreatorMission m) {
    }

    protected void configureFleet(int size, FleetCreatorMission m) {
        m.triggerSetFleetOfficers(HubMissionWithTriggers.OfficerNum.DEFAULT, HubMissionWithTriggers.OfficerQuality.DEFAULT);
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.DEFAULT);
        m.triggerSetFleetFlag(FLEET_KEY);

        // IDEA: Scale fleet quality base on source market ship quality
        if (size == this.maxFleetSize) { // Main Fleet
            m.triggerSetFleetFlag(MAIN_FLEET_KEY);
            m.triggerSetFleetMemoryValue(EVENT_KEY, this);
        }

        boolean lightDetachment = size <= 5;
        if (lightDetachment) {
            m.triggerSetFleetMaxShipSize(3);
        }
    }

    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        if (size == this.maxFleetSize) { // Main Fleet
            fleet.setName("Expedition Command Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
            setNeverStraggler(fleet);
            if (isCurrent(RETURN_ACTION) || isCurrent(DOCK_ACTION)) {
                // Ensure fleet is marked properly when it spawns midway
                Misc.makeImportant(fleet, "hasSpecialItem");
                Misc.addDefeatTrigger(fleet, "SEPEFGIFleetDefeated");
                fleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
            }
        } else {
            fleet.setName("Expedition Supply Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
        }
    }

    public void setFleetCreatorQualityFromRoute(FleetCreatorMission m) {
        if (m == null || this.route == null || this.route.getExtra() == null || this.route.getExtra().quality == null) {
            return;
        }
        m.getPreviousCreateFleetAction().qualityOverride = this.route.getExtra().quality;
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

        if ("endEvent".equals(action)) {
            unsetEventMemoryFlags();
            finish(true);
            return true;
        } else if ("giveArtifact".equals(action)) {
            Global.getSector().getPlayerFleet().getCargo().addSpecial(this.specialItemData, 1f);
            AddRemoveCommodity.addItemGainText(this.specialItemData, 1, dialog.getTextPanel());
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

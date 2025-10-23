package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.Random;

public class IncursionFleetIntel extends GenericRaidFGI {
    public static final String EVENT_KEY = "$sep_ifi_ref";
    public static final String FACTION_KEY = "$sep_ifi_sourceFaction";
    public static final String FLEET_KEY = "$sep_ifi_fleet";
    public static final String MAIN_FLEET_KEY = "$sep_ifi_mainFleet";
    public static final String TARGET_KEY = "$sep_efi_target";
    public static final String HAS_ARTIFACT = "$sep_efi_hasArtifact";
    public static Logger log = Global.getLogger(IncursionFleetIntel.class);

    protected EntityFinderMission efm;
    protected MarketAPI source;
    protected MarketAPI target;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;

    public IncursionFleetIntel() {
        super(null);
        this.efm = new EntityFinderMission();
        setRandom(new Random(Utils.random.nextLong()));
        pickSource();
        if (isDone()) {
            log.info("Failed to find source market");
            return;
        }
        pickTarget();
        if (isDone()) {
            log.info("Failed to find target market");
            return;
        }
        pickSpecialItem();
        if (isDone()) {
            log.info("Failed to find special item to raid");
            return;
        }

        GenericRaidParams params = new GenericRaidParams(getRandom(), true);
        params.makeFleetsHostile = false; // will be made hostile when they arrive, not before
        params.source = this.source;
        params.prepDays = 21f + getRandom().nextFloat() * 7f;
        params.payloadDays = 27f + 7f * getRandom().nextFloat();

        params.raidParams.where = this.target.getStarSystem();
        params.raidParams.type = FGRaidAction.FGRaidType.SEQUENTIAL;
        params.raidParams.allowedTargets.add(this.target);
        params.raidParams.allowNonHostileTargets = false;
        params.raidParams.raidsPerColony = 3;

        params.factionId = this.source.getFactionId();
        params.style = FleetCreatorMission.FleetStyle.QUALITY;
        params.repImpact = HubMissionWithTriggers.ComplicationRepImpact.FULL;
        params.noun = "incursion";
        params.forcesNoun = "incursion forces";

        params.fleetSizes.add(10);
        params.fleetSizes.add(8);
        params.fleetSizes.add(8);

        this.params = params;
        initActions();

        Global.getSector().getIntelManager().queueIntel(this);

        log.info(String.format("Starting %s incursion at %s in the %s, targeting %s in the %s",
                this.source.getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), this.target.getStarSystem().getNameWithLowercaseTypeShort()));
    }

    public void pickSource() {
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketFactionNotPlayer();
        this.efm.requireMarketStabilityAtLeast(8);
        this.efm.preferMarketHasSpaceport();
        this.efm.preferMarketMilitary();
        this.source = this.efm.pickMarket();
        if (this.source == null) {
            endImmediately();
        }
    }

    public void pickTarget() {
        this.efm.requireMarketFactionHostileTo(this.source.getFactionId());
        this.efm.requireMarketFactionNot(this.source.getFactionId());
        this.efm.requireMarketFactionNotPlayer();
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketUsesSpecialItems();
        this.efm.requireMarketHasCompatibleSpecialItemsWithOther(this.source);
        this.target = this.efm.pickMarket();
        if (this.target == null) {
            endImmediately();
        }
    }

    public void pickSpecialItem() {
        WeightedRandomPicker<SpecialItemData> picker = new WeightedRandomPicker<>(getRandom());
        for (Industry targetInd : this.target.getIndustries()) {
            SpecialItemData otherData = targetInd.getSpecialItem();
            if (otherData != null) {
                for (Industry ind : this.source.getIndustries()) {
                    if (ind.wantsToUseSpecialItem(otherData)) {
                        picker.add(otherData);
                    }
                }
            }
        }

        this.specialItemData = picker.pick();
        if (this.specialItemData == null) {
            endImmediately();
            return;
        }

        this.specialItemSpec = Global.getSettings().getSpecialItemSpec(this.specialItemData.getId());
        if (this.specialItemSpec == null) {
            endImmediately();
            return;
        }
    }

    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        m.triggerSetFleetFlag(FLEET_KEY);

        if (size == 10) {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_3);;
            m.triggerSetFleetFlag(MAIN_FLEET_KEY);
        } else if (getRandom().nextFloat() < 0.5f) {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_1);
        } else {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_2);
        }

        boolean lightDetachment = size <= 5;
        if (lightDetachment) {
            m.triggerSetFleetMaxShipSize(3);
        }
    }

    @Override
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        boolean hasCombatCapital = false;
        boolean hasCivCapital = false;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isCapital()) {
                hasCombatCapital |= !member.isCivilian();
                hasCivCapital |= member.isCivilian();
            }
        }

        if (size == 10) {
            fleet.setName("Incursion Command Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
            setNeverStraggler(fleet);
        } else if (hasCombatCapital) {
            fleet.setName("Incursion Assault Detachment");
            fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
        } else if (hasCivCapital) {
            fleet.setName("Incursion Support Detachment");
            fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
        } else {
            fleet.setName("Incursion Light Detachment");
            fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
        }
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        if (action == null) {
            return;
        }

        super.notifyActionFinished(action);

        if (PAYLOAD_ACTION.equals(action.getId())) {
            CampaignFleetAPI mainFleet = getMainFleet();
            if (mainFleet != null) {
                Misc.makeImportant(mainFleet, "hasSpecialItem");
                Misc.addDefeatTrigger(mainFleet, "SEPIFGIFleetDefeated");
                mainFleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
            }
        } else if (RETURN_ACTION.equals(action.getId())) {
            this.efm.requireMarketFaction(this.source.getFactionId());
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

            // TODO: Delay installation by a few days
            Industry ind = pickIndustryToInstallItem(market, this.specialItemData);
            ind.setSpecialItem(this.specialItemData);
            new ArtifactInstallationIntel(market, ind, this.specialItemSpec);
            log.info(String.format("Installing %s to %s facility %s %s in the %s",
                    this.specialItemSpec.getName(), ind.getCurrentName(), market.getOnOrAt(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort()));
        }
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
        super.notifyEnded();
        unsetEventMemoryFlags();
    }

    public void unsetEventMemoryFlags() {
        // Unset faction memory flags
        if (this.faction != null) {
            this.faction.getMemoryWithoutUpdate().unset(FACTION_KEY);
        }

        // Unset target memory flags
        if (this.target != null) {
            this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
            this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
        }

        // Unset fleet memory flags
        CampaignFleetAPI mainFleet = getMainFleet();
        if (mainFleet != null) {
            Misc.makeUnimportant(mainFleet, "hasSpecialItem");
            mainFleet.getMemoryWithoutUpdate().unset(MAIN_FLEET_KEY);
            mainFleet.getMemoryWithoutUpdate().unset(HAS_ARTIFACT);
            mainFleet.getMemoryWithoutUpdate().unset(EVENT_KEY);
        }
    }

    @Override
    public boolean isSucceeded() {
        return this.returnAction.isActionFinished() && super.isSucceeded();
    }

    @Override
    public boolean isFailed() {
        return super.isFailed();
    }
}

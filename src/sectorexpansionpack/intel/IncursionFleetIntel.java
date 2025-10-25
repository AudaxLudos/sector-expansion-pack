package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Random;

// TODO: Add custom dialogs to quick reaction force fleet
// TODO: Add checks for special items that are player used only or that has commodity demand affects
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

        this.params = new GenericRaidParams(getRandom(), true);
        this.params.makeFleetsHostile = false; // will be made hostile when they arrive, not before
        this.params.source = this.source;
        this.params.prepDays = 21f + getRandom().nextFloat() * 7f;
        this.params.payloadDays = 27f + 7f * getRandom().nextFloat();

        this.params.raidParams.where = this.target.getStarSystem();
        this.params.raidParams.type = FGRaidAction.FGRaidType.SEQUENTIAL;
        this.params.raidParams.allowedTargets.add(this.target);
        this.params.raidParams.allowNonHostileTargets = false;
        this.params.raidParams.raidsPerColony = 3;

        this.params.factionId = this.source.getFactionId();
        this.params.style = FleetCreatorMission.FleetStyle.QUALITY;
        this.params.repImpact = HubMissionWithTriggers.ComplicationRepImpact.FULL;
        this.params.noun = "incursion";
        this.params.forcesNoun = "incursion forces";

        // TODO: Scale fleets based on target faction system presence
        this.params.fleetSizes.add(10);
        this.params.fleetSizes.add(8);
        this.params.fleetSizes.add(8);

        initActions();

        // Mark source faction so it won't be reselected for future expeditions
        this.source.getFaction().getMemoryWithoutUpdate().set(FACTION_KEY, true);

        // Mark target so it won't be reselected for future expeditions
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);

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
        }
    }

    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        m.triggerSetFleetFlag(FLEET_KEY);

        // TODO: Scale fleet quality based on source size, is military, has heavy industry
        if (size == 10) {
            m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_3);
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

        if (isCurrent(TRAVEL_ACTION) && this.raidAction.getSuccessFraction() > 0f && this.raidAction.isActionFinished()
                && fleet.getMemoryWithoutUpdate().getBoolean(MAIN_FLEET_KEY)) {
            // Ensure fleet is marked properly when it spawns midway
            Misc.makeImportant(fleet, "hasSpecialItem");
            Misc.addDefeatTrigger(fleet, "SEPEFGIFleetDefeated");
            fleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
        }
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        Color h = Misc.getHighlightColor();
        Color s = this.raidAction.getSystemNameHighlightColor();
        FGAction curr = getCurrentAction();
        StarSystemAPI system = this.raidAction.getWhere();
        String forces = getForcesNoun();

        float untilDeployment = getETAUntil(PREPARE_ACTION);
        float untilDeparture = getETAUntil(TRAVEL_ACTION);
        float untilRaid = getETAUntil(PAYLOAD_ACTION);
        float untilReturn = getETAUntil(RETURN_ACTION, true);
        if (!isEnding()) {
            if (mode == ListInfoMode.MESSAGES || getElapsed() <= 0f) { // initial notification only, not updates
                addTargetingBulletPoint(info, tc, param, mode, initPad);
                initPad = 0f;
            }
            if (untilDeployment > 0) {
                addETABulletPoints(null, null, false, untilDeployment, ETAType.DEPLOYMENT, info, tc, initPad);
                initPad = 0f;
            } else if (untilDeparture > 0) {
                addETABulletPoints(null, null, false, untilDeparture, ETAType.DEPARTURE, info, tc, initPad);
                initPad = 0f;
            }
            if (untilRaid > 0 && getSource().getContainingLocation() != system) {
                addETABulletPoints(system.getNameWithLowercaseTypeShort(), s, false, untilRaid, ETAType.ARRIVING,
                        info, tc, initPad);
                initPad = 0f;
            }
            if (untilReturn > 0 && RETURN_ACTION.equals(curr.getId()) && getSource().getContainingLocation() != system) {
                StarSystemAPI from = getSource().getStarSystem();

                addETABulletPoints(from.getNameWithLowercaseTypeShort(), null, false, untilReturn, ETAType.RETURNING,
                        info, tc, initPad);
                initPad = 0f;
            }
            if ((mode == ListInfoMode.INTEL || mode == ListInfoMode.MAP_TOOLTIP)
                    && curr != null && curr.getId().equals(PAYLOAD_ACTION)) {
                LabelAPI label = info.addPara("Operating in the " + system.getNameWithLowercaseTypeShort(), tc, initPad);
                label.setHighlightColors(s);
                label.setHighlight(system.getNameWithNoType());
                initPad = 0f;
            }
        }

        if (mode != ListInfoMode.IN_DESC && isEnding()) {
            if (!isSucceeded()) {
                if (!isAborted() && !isFailed()) {
                    info.addPara("The " + forces + " have failed to achieve their objective", tc, initPad);
                } else {
                    if (isFailedButNotDefeated()) {
                        info.addPara("The " + forces + " have failed to achieve their objective", tc, initPad);
                    } else {
                        info.addPara("The " + forces + " have been defeated and scatter", tc, initPad);
                    }
                }
            }
        }
    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        StarSystemAPI system = this.raidAction.getWhere();
        String forces = getForcesNoun();
        String noun = getNoun();
        Color s = this.raidAction.getSystemNameHighlightColor();

        if (ABORT_UPDATE.equals(param)) {
            if (isInPreLaunchDelay()) {
                info.addPara("The " + noun + " was aborted in the planning stages", tc, initPad);
            } else {
                info.addPara("The " + forces + " have been defeated and scatter", tc, initPad);
            }
        } else if (FLEET_LAUNCH_UPDATE.equals(param)) {
            float untilDeparture = getETAUntil(TRAVEL_ACTION);
            float untilRaid = getETAUntil(PAYLOAD_ACTION);
            info.addPara("Fleet deployment in progress", tc, initPad);
            initPad = 0f;
            if (untilDeparture > 0) {
                addETABulletPoints(null, null, false, untilDeparture, ETAType.DEPARTURE, info, tc, initPad);
            }
            if (untilRaid > 0 && getSource().getContainingLocation() != system) {
                addETABulletPoints(system.getNameWithLowercaseTypeShort(), s, false, untilRaid, ETAType.ARRIVING,
                        info, tc, initPad);
            }
        } else if (PREPARE_ACTION.equals(param)) {
            float untilRaid = getETAUntil(PAYLOAD_ACTION);
            addETABulletPoints(system.getNameWithLowercaseTypeShort(), s, true, untilRaid, ETAType.ARRIVING,
                    info, tc, initPad);
        } else if (TRAVEL_ACTION.equals(param)) {
            addArrivedBulletPoint(system.getNameWithLowercaseTypeShort(), s, info, tc, initPad);
        } else if (PAYLOAD_ACTION.equals(param)) {
            if (isSucceeded()) {
                info.addPara("The " + forces + " are withdrawing", tc, initPad);
            } else {
                if (isAborted()) {
                    info.addPara("The " + forces + " have been defeated and scatter", tc, initPad);
                } else {
                    info.addPara("The " + forces + " have failed to achieve their objective", tc, initPad);
                }
            }
        } else if (RETURN_ACTION.equals(param)) {
            float untilReturn = getETAUntil(RETURN_ACTION);
            if (untilReturn > 0 && getSource().getContainingLocation() != system) {
                addETABulletPoints(system.getNameWithLowercaseTypeShort(), s, false, untilReturn, ETAType.RETURNING,
                        info, tc, initPad);
            }
        }
    }

    @Override
    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
        Color h = Misc.getHighlightColor();
        Color tc = Misc.getTextColor();
        FactionAPI faction = getFaction();
        List<MarketAPI> targets = this.params.raidParams.allowedTargets;
        FGAction action = getCurrentAction();
        String noun = getNoun();
        String forcesNoun = getForcesNoun();
        StarSystemAPI system = this.raidAction.getWhere();

        if (!isEnding() && !isSucceeded() && !isFailed()) {
            float raidStr = getRoute().getExtra().getStrengthModifiedByDamage();
            info.addSectionHeading("Assessment", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);

            if (action != null && RETURN_ACTION.equals(action.getId())) {
                String strDesc = Misc.getStrengthDesc(raidStr);
                int numFleets = getApproximateNumberOfFleets();
                String fleets = "fleets";
                if (numFleets == 1) {
                    fleets = "fleet";
                }

                info.addPara("The %s are projected to be %s and likely comprised of %s %s. " +
                                "The command fleet is likely carrying the raided colony item", opad,
                        new Color[]{tc, h, tc, h},
                        forcesNoun, strDesc, numFleets + "", fleets);
            } else {
                if (targets.isEmpty()) {
                    info.addPara("There are no colonies for the " + noun + " to target in the system.", opad);
                } else {
                    boolean potentialDanger = addStrengthDesc(info, opad, system, forcesNoun,
                            "the " + noun + " is unlikely to find success",
                            "the outcome of the " + noun + " is uncertain",
                            "the " + noun + " is likely to find success");

                    if (potentialDanger) {
                        String safe = "should be safe from the " + noun;
                        String risk = "are at risk of being raided and losing stability:";
                        String highlight = "losing stability:";
                        if (this.params.raidParams.bombardment == MarketCMD.BombardType.SATURATION) {
                            risk = "are at risk of suffering a saturation bombardment resulting in catastrophic damage:";
                            highlight = "catastrophic damage";
                        } else if (this.params.raidParams.bombardment == MarketCMD.BombardType.TACTICAL) {
                            risk = "are at risk of suffering a tactical bombardment and having their military infrastructure disrupted:";
                            highlight = "military infrastructure disrupted";
                        } else if (!this.params.raidParams.disrupt.isEmpty()) {
                            risk = "are at risk of being raided and having their operations severely disrupted";
                            highlight = "operations severely disrupted";
                        }
                        if (getAssessmentRiskStringOverride() != null) {
                            risk = getAssessmentRiskStringOverride();
                        }
                        if (getAssessmentRiskStringHighlightOverride() != null) {
                            highlight = getAssessmentRiskStringHighlightOverride();
                        }
                        showMarketsInDanger(info, opad, width, system, targets,
                                safe, risk, highlight);
                    }
                }
                addPostAssessmentSection(info, width, height, opad);
            }
        }
    }

    @Override
    protected String getAssessmentRiskStringOverride() {
        return "are at risk of being raided and losing one of its used colony items:";
    }

    @Override
    protected String getAssessmentRiskStringHighlightOverride() {
        return "losing its used colony items";
    }

    @Override
    protected void addStatusSection(TooltipMakerAPI info, float width, float height, float opad) {
        FGAction curr = getCurrentAction();

        if (curr != null || isEnding() || isSucceeded()) {
            String noun = getNoun();
            String forces = getForcesNoun();
            info.addSectionHeading("Status",
                    this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);
            if (isEnding() && !isSucceeded()) {
                if (isFailed() || isAborted()) {
                    if (isFailedButNotDefeated()) {
                        info.addPara("The " + forces + " are withdrawing.", opad);
                    } else {
                        info.addPara("The " + forces + " have been defeated and any "
                                + "remaining ships are retreating in disarray.", opad);
                    }
                } else {
                    info.addPara("The " + forces + " are withdrawing.", opad);
                }
            } else if (isEnding() || isSucceeded()) {
                info.addPara("The " + noun + " was successful and the " + forces + " are withdrawing.", opad);
            } else if (curr != null) {
                StarSystemAPI to = this.raidAction.getWhere();
                if (isInPreLaunchDelay()) {
                    if (getSource().getMarket() != null) {
                        BaseHubMission.addStandardMarketDesc("The " + noun + " is in the planning stages on",
                                getSource().getMarket(), info, opad);
                        boolean mil = isSourceFunctionalMilitaryMarket();
                        if (mil) {
                            info.addPara("Disrupting the military facilities " + getSource().getMarket().getOnOrAt() +
                                    " " + getSource().getMarket().getName() + " will abort the " + noun + ".", opad);
                        }
                    }
                } else if (PREPARE_ACTION.equals(curr.getId())) {
                    if (getSource().getMarket() != null) {
                        BaseHubMission.addStandardMarketDesc("Making preparations in orbit around",
                                getSource().getMarket(), info, opad);
                    } else {
                        info.addPara("Making preparations in orbit around " + getSource().getName() + ".", opad);
                    }
                } else if (TRAVEL_ACTION.equals(curr.getId())) {
                    if (getSource().getMarket() == null) {
                        info.addPara("Traveling to the " +
                                to.getNameWithLowercaseTypeShort() + ".", opad);
                    } else {
                        info.addPara("Traveling from " + getSource().getMarket().getName() + " to the " +
                                to.getNameWithLowercaseTypeShort() + ".", opad);
                    }
                } else if (PAYLOAD_ACTION.equals(curr.getId())) {
                    addPayloadActionStatus(info, width, height, opad);
                } else if (RETURN_ACTION.equals(curr.getId())) {
                    if (getSource().getMarket() == null) {
                        info.addPara("Returning to their port of origin.", opad);
                    } else {
                        info.addPara("Returning to " + getSource().getMarket().getName() + " in the " +
                                this.origin.getContainingLocation().getNameWithLowercaseTypeShort() + ".", opad);
                    }
                }
            }
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
            Industry ind = Utils.pickIndustryToInstallItem(market, this.specialItemData);
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

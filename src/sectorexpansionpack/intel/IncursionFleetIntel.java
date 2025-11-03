package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
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
import java.util.Map;
import java.util.Random;

public class IncursionFleetIntel extends GenericRaidFGI {
    public static final String EVENT_KEY = "$sep_ifi_ref";
    public static final String FACTION_KEY = "$sep_ifi_sourceFaction";
    public static final String FLEET_KEY = "$sep_ifi_fleet";
    public static final String MAIN_FLEET_KEY = "$sep_ifi_mainFleet";
    public static final String TARGET_KEY = "$sep_ifi_target";
    public static final String HAS_ARTIFACT = "$sep_ifi_hasArtifact";
    public static Logger log = Global.getLogger(IncursionFleetIntel.class);

    protected Integer maxFleetSize = 10;
    protected EntityFinderMission efm;
    protected MarketAPI source;
    protected MarketAPI target;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;

    public IncursionFleetIntel() {
        super(null);
        setRandom(new Random(Utils.random.nextLong()));
        this.efm = new EntityFinderMission();
        pickFaction();
        if (isDone()) {
            log.info("Failed to find faction");
            return;
        }
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
        this.params.raidParams.tryToCaptureObjectives = false;
        this.params.raidParams.raidsPerColony = 3;
        this.params.raidParams.allowedTargets.add(this.target);

        this.params.factionId = this.source.getFactionId();
        this.params.style = FleetCreatorMission.FleetStyle.QUALITY;
        this.params.repImpact = HubMissionWithTriggers.ComplicationRepImpact.FULL;
        this.params.noun = "incursion";
        this.params.forcesNoun = "incursion forces";

        // here might be a better way of scaling difficulty like using fleet points instead but this works for now
        float baseDifficulty = getMarketPresenceDifficulty(this.target.getStarSystem(), this.target.getFactionId());
        // A bit of variance so that fleets aren't always strong against target market
        float variance = 0.5f + (getRandom().nextFloat() * 0.75f);
        int totalDifficulty = Math.round(baseDifficulty * variance);
        log.info(String.format("Total fleet difficulty spawned at %s is %s", this.source.getName(), totalDifficulty));

        if (totalDifficulty - 10 < 0) {
            this.maxFleetSize = totalDifficulty;
        }

        this.params.fleetSizes.add(this.maxFleetSize);
        totalDifficulty -= this.maxFleetSize;

        while (totalDifficulty > 0 || this.params.fleetSizes.size() < 9) {
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
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        Global.getSector().getIntelManager().queueIntel(this);

        log.info(String.format("Starting %s incursion at %s in the %s, targeting %s in the %s",
                this.source.getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), this.target.getStarSystem().getNameWithLowercaseTypeShort()));
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

    public void pickSource() {
        this.efm.requireMarketFaction(getFaction().getId());
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketFactionNotPlayer();
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
        this.efm.requireMarketNoMemoryFlag(TARGET_KEY);
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

    public float getMarketPresenceDifficulty(StarSystemAPI system, String factionId) {
        float difficulty = 0f;
        for (MarketAPI market : Misc.getMarketsInLocation(system, factionId)) {
            difficulty += estimateMarketDifficulty(market);
        }

        return difficulty;
    }

    public int estimateMarketDifficulty(MarketAPI market) {
        float difficulty = 0f;

        int maxLight = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).computeEffective(0);
        int maxMedium = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).computeEffective(0);
        int maxHeavy = (int) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).computeEffective(0);
        float fleetSizeMult = market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        float fleetQualityMult = Misc.getShipQuality(this.target);

        for (int i = 0; i < maxLight; i++) {
            difficulty += 1 * fleetSizeMult * fleetQualityMult;
        }
        for (int i = 0; i < maxMedium; i++) {
            difficulty += 2 * fleetSizeMult * fleetQualityMult;
        }
        for (int i = 0; i < maxHeavy; i++) {
            difficulty += 4 * fleetSizeMult * fleetQualityMult;
        }

        return Math.round(difficulty);
    }

    @Override
    protected void configureFleet(int size, FleetCreatorMission m) {
        m.triggerSetFleetOfficers(HubMissionWithTriggers.OfficerNum.DEFAULT, HubMissionWithTriggers.OfficerQuality.DEFAULT);
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.DEFAULT);
        m.triggerSetFleetFlag(FLEET_KEY);

        if (size == this.maxFleetSize) {
            m.triggerSetFleetFlag(MAIN_FLEET_KEY);
            m.triggerSetFleetMemoryValue(EVENT_KEY, this);
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

        if (size == this.maxFleetSize) {
            fleet.setName("Incursion Command Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
            setNeverStraggler(fleet);
            if (isCurrent(RETURN_ACTION) && this.raidAction.getSuccessFraction() > 0f && this.raidAction.isActionFinished()) {
                // Ensure fleet is marked properly when it spawns midway
                Misc.makeImportant(fleet, "hasSpecialItem");
                Misc.addDefeatTrigger(fleet, "SEPIFGIFleetDefeated");
                fleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
            }
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
        return "losing one of its used colony items";
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
            if (this.raidAction.getSuccessFraction() > 0) {
                CampaignFleetAPI mainFleet = getMainFleet();
                if (mainFleet != null) {
                    Misc.makeImportant(mainFleet, "hasSpecialItem");
                    Misc.addDefeatTrigger(mainFleet, "SEPIFGIFleetDefeated");
                    mainFleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT, true);
                }

                for (Industry ind : this.target.getIndustries()) {
                    if (ind.getSpecialItem() != null && ind.getSpecialItem() == this.specialItemData) {
                        log.info(String.format("Removing %s from %s at %s in the %s due to an incursion",
                                this.specialItemSpec.getName(), ind.getCurrentName(), this.target.getName(),
                                this.target.getStarSystem().getNameWithLowercaseTypeShort()));
                        ind.setSpecialItem(null);
                        break;
                    }
                }

                log.info(String.format("The %s incursion is successful at %s in the %s",
                        this.faction.getDisplayName(), this.target.getName(),
                        this.target.getStarSystem().getNameWithLowercaseTypeShort()));
            }
        } else if (RETURN_ACTION.equals(action.getId())) {
            this.efm.resetSearch();
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

            // IDEA: Create a courier fleet that transfers the colony item to another faction market if the source market can't use it
            // TODO: Delay installation by a few days
            Industry ind = Utils.pickIndustryToInstallItem(market, this.specialItemData);
            ind.setSpecialItem(this.specialItemData);
            new ArtifactInstallationIntel(market, ind, this.specialItemSpec);
            log.info(String.format("Installing %s to %s facility %s %s in the %s",
                    this.specialItemSpec.getName(), ind.getCurrentName(), market.getOnOrAt(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort()));
        }
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        unsetEventMemoryFlags();

        if (this.endingTimeRemaining > 0f) {
            log.info(String.format("Ending %s incursion event", this.source.getFaction().getDisplayName()));
        }
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

    @Override
    public boolean isSucceeded() {
        return this.returnAction.isActionFinished() && super.isSucceeded();
    }
}

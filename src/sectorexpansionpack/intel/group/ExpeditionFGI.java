package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.missions.hub.SEPHubMission;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class ExpeditionFGI extends FleetGroupIntel {
    public static Logger log = Global.getLogger(ExpeditionFGI.class);

    public static String PREPARE_ACTION = "prepare_action";
    public static String TRAVEL_ACTION = "travel_action";
    public static String PAYLOAD_ACTION = "payload_action";
    public static String RETURN_ACTION = "return_action";

    protected float lootMult = 0f;
    protected ExpeditionParams params;
    protected FGWaitAction waitAction;
    protected FGTravelAction travelAction;
    protected FGWaitAction payloadAction;
    protected FGTravelAction returnAction;

    public ExpeditionFGI() {
        this.params = new ExpeditionParams(Misc.random);
        pickSource();
        if (isDone()) {
            return;
        }
        pickTarget();
        if (isDone()) {
            return;
        }

        float fleetSizeMult = this.params.source.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
        float maxTotalDifficulty = fleetSizeMult * 5f;
        float totalDifficulty = maxTotalDifficulty;
        if (totalDifficulty > 100f) {
            totalDifficulty = 100f;
        }
        if (totalDifficulty >= 10f) {
            this.params.fleetSizes.add(10);
            totalDifficulty -= 10f;
        } else {
            this.params.fleetSizes.add(Math.round(totalDifficulty));
            totalDifficulty = 0f;
        }
        while (totalDifficulty > 0f) {
            int min = 4;
            int max = 8;
            int diff = min + this.random.nextInt(max - min + 1);

            this.params.fleetSizes.add(diff);
            totalDifficulty -= diff;
        }

        float daysLootMult = Math.max(0.2f, Math.min(2f, this.lootMult));
        this.params.prepDays = 14f + 14f * daysLootMult;
        daysLootMult = Math.max(0.5f, Math.min(2f, this.lootMult));
        this.params.payloadDays = 28f + 14f * daysLootMult;
        setRandom(this.params.random);
        setPostingLocation(getSource());
        initAction();
        Global.getSector().getIntelManager().queueIntel(this);
        log.info(String.format("Starting expedition by %s in the %s with a total difficulty value of %s",
                Misc.ucFirst(getFaction().getDisplayName()), this.params.source.getStarSystem().getNameWithLowercaseTypeShort(),
                maxTotalDifficulty));
    }

    protected void pickSource() {
        SEPHubMission picker = new SEPHubMission();
        picker.setGenRandom(this.params.random);
        picker.requireMarketNotHidden();
        picker.requireMarketHasSpaceport();
        picker.requireMarketFactionNotPlayer();
        picker.requireMarketIndustries(ReqMode.ANY, Industries.MILITARYBASE, Industries.HIGHCOMMAND, Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS);

        MarketAPI picked = picker.pickMarket();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.params.source = picked;
        this.params.factionId = picked.getFactionId();
    }

    protected void pickTarget() {
        SEPHubMission picker = new SEPHubMission();
        picker.setGenRandom(this.params.random);
        picker.requireSystemInterestingAndNotCore();
        picker.preferSystemInInnerSector();
        picker.preferSystemUnexplored();

        StarSystemAPI picked = picker.pickSystem();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.params.target = picked;
        this.lootMult = getLootMultiplier(picked);
    }

    protected float getLootMultiplier(StarSystemAPI system) {
        float mult = 0f;

        for (PlanetAPI p : system.getPlanets()) {
            if (p.hasCondition(Conditions.RUINS_SCATTERED)) {
                mult += 0.10f;
            } else if (p.hasCondition(Conditions.RUINS_WIDESPREAD)) {
                mult += 0.20f;
            } else if (p.hasCondition(Conditions.RUINS_EXTENSIVE)) {
                mult += 0.30f;
            } else if (p.hasCondition(Conditions.RUINS_VAST)) {
                mult += 0.50f;
            }
        }

        for (SectorEntityToken e : system.getEntitiesWithTag(Tags.SALVAGEABLE)) {
            String type = e.getCustomEntityType();
            switch (type) {
                case Entities.DERELICT_MOTHERSHIP:
                case Entities.STATION_RESEARCH:
                    mult += 0.50f;
                    break;
                case Entities.DERELICT_SURVEY_SHIP:
                    mult += 0.30f;
                    break;
                case Entities.STATION_MINING:
                case Entities.ORBITAL_HABITAT:
                    mult += 0.20f;
                    break;
                case Entities.EQUIPMENT_CACHE:
                    mult += 0.10f;
                    break;
                case Entities.EQUIPMENT_CACHE_SMALL:
                    mult += 0.05f;
                    break;
                default:
                    mult += 0.025f;
                    break;
            }
        }

        return mult;
    }

    public void initAction() {
        setFaction(this.params.factionId);
        this.waitAction = new FGWaitAction(getSource(), this.params.prepDays, "preparing for departure");
        addAction(this.waitAction, PREPARE_ACTION);

        this.travelAction = new FGTravelAction(getSource(), this.params.target.getCenter());
        addAction(this.travelAction, TRAVEL_ACTION);

        this.payloadAction = new FGWaitAction(this.params.target.getCenter(), this.params.payloadDays, "exploring the system");
        addAction(this.payloadAction, PAYLOAD_ACTION);

        this.returnAction = new FGTravelAction(this.params.target.getCenter(), getSource());
        addAction(this.returnAction, RETURN_ACTION);

        int total = 0;
        for (Integer i : this.params.fleetSizes) {
            total += i;
        }
        createRoute(this.params.factionId, total, this.params.fleetSizes.size(), null);
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

    public CampaignFleetAPI createFleet(int size, float damage) {
        Vector2f loc = getSource().getLocationInHyperspace();
        boolean pirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);
        String factionId = this.params.factionId;

        FleetCreatorMission m = new FleetCreatorMission(getRandom());

        m.beginFleet();
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

        if (this.params.makeFleetsHostile) {
            m.triggerMakeHostile();
            if (Factions.LUDDIC_PATH.equals(this.faction.getId())) {
                m.triggerFleetPatherNoDefaultTithe();
            }
        }

        if (this.params.repImpact == HubMissionWithTriggers.ComplicationRepImpact.LOW || this.params.repImpact == null) {
            m.triggerMakeLowRepImpact();
        } else if (this.params.repImpact == HubMissionWithTriggers.ComplicationRepImpact.NONE) {
            m.triggerMakeNoRepImpact();
        }

        if (this.params.repImpact != HubMissionWithTriggers.ComplicationRepImpact.FULL) {
            m.triggerMakeAlwaysSpreadTOffHostility();
        }

        CampaignFleetAPI fleet = m.createFleet();
        if (fleet != null) {
            configureFleet(size, fleet);
        }
        return fleet;
    }

    public void setFleetCreatorQualityFromRoute(FleetCreatorMission m) {
        if (m == null || this.route == null || this.route.getExtra() == null || this.route.getExtra().quality == null) {
            return;
        }
        m.getPreviousCreateFleetAction().qualityOverride = this.route.getExtra().quality;
    }

    public void configureFleet(int size, CampaignFleetAPI fleet) {
        fleet.setNoFactionInName(false);
        if (this.params.fleetSizes.size() == 1) {
            fleet.setName("Expedition Fleet");
            fleet.getMemoryWithoutUpdate().set("$sep_expeditionFleet", true);
            setPostingLocation(fleet);
        } else {
            Integer maxSize = this.params.fleetSizes.stream().reduce(Integer.MIN_VALUE, Integer::max);
            if (size == maxSize) {
                fleet.setName("Expedition Fleet");
                fleet.getMemoryWithoutUpdate().set("$sep_expeditionFleet", true);
                setPostingLocation(fleet);
            } else {
                fleet.setName("Supply Fleet");
            }
        }
    }

    @Override
    protected SectorEntityToken getSource() {
        return this.params.source.getPrimaryEntity();
    }

    @Override
    protected SectorEntityToken getDestination() {
        return this.params.target.getHyperspaceAnchor();
    }

    @Override
    protected String getBaseName() {
        return Misc.ucFirst(getFaction().getPersonNamePrefix()) + " " + Misc.ucFirst(this.params.noun);
    }

    @Override
    protected void addBasicDescription(TooltipMakerAPI info, float width, float height, float oPad) {
        info.addImage(getFaction().getLogo(), width, 128, oPad);
        StarSystemAPI system = this.params.target;
        String noun = this.params.noun;

        info.addPara(Misc.ucFirst(this.faction.getPersonNamePrefixAOrAn()) + " %s " + noun + " is going to explore "
                        + "the " + system.getNameWithLowercaseTypeShort() + ".", oPad,
                this.faction.getBaseUIColor(), this.faction.getPersonNamePrefix());
    }

    @Override
    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height, float oPad) {
        FGAction currentAction = getCurrentAction();
        FactionAPI faction = getFaction();
        String forces = this.params.forcesNoun;
        String systemName = this.params.target.getNameWithLowercaseTypeShort();
        String systemHighlight = getNameWithNoType(systemName);
        Color h = Misc.getHighlightColor();

        if (currentAction != null) {
            if (!isEnding() && !isSucceeded() && !isFailed() && !RETURN_ACTION.equals(getCurrentAction().getId())) {
                info.addSectionHeading("Assessment", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, oPad);

                float raidStr = getRoute().getExtra().getStrengthModifiedByDamage();
                String strDesc = Misc.getStrengthDesc(raidStr);
                int numFleets = getApproximateNumberOfFleets();
                String fleets = "fleets";
                if (numFleets == 1) {
                    fleets = "fleet";
                }

                info.addPara("The " + forces + " are projected to be %s and likely comprised of %s " + fleets + ".",
                        oPad, h, strDesc, "" + numFleets);

                String lootDesc = getLootDescription(this.lootMult, true);
                String lootHighlight = getLootDescription(this.lootMult, false);
                Color lootColor = getLootDescColor(this.lootMult);

                LabelAPI label = info.addPara("Preliminary info on the " + systemName +
                        " suggests " + lootDesc + " of yielding valuable salvage.", oPad);
                label.setHighlightColors(h, lootColor);
                label.setHighlight(systemHighlight, lootHighlight);
            }
        }
    }

    @Override
    protected void addStatusSection(TooltipMakerAPI info, float width, float height, float oPad) {
        FGAction currentAction = getCurrentAction();
        StarSystemAPI target = this.params.target;
        StarSystemAPI source = this.params.source.getStarSystem();
        String forces = this.params.forcesNoun;
        String noun = this.params.noun;

        info.addSectionHeading("Status", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, oPad);
        if (currentAction != null && !isFailed()) {
            if (isInPreLaunchDelay()) {
                if (getSource().getMarket() != null) {
                    BaseHubMission.addStandardMarketDesc("The " + noun + " is in the planning stages on",
                            getSource().getMarket(), info, oPad);
                    boolean mil = isSourceFunctionalMilitaryMarket();
                    if (mil) {
                        info.addPara("Disrupting the military facilities " + getSource().getMarket().getOnOrAt() +
                                " " + getSource().getMarket().getName() + " will abort the " + noun + ".", oPad);
                    }
                }
            } else if (Objects.equals(PREPARE_ACTION, currentAction.getId())) {
                if (getSource().getMarket() != null) {
                    BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", getSource().getMarket(), info, oPad);
                } else {
                    info.addPara("Making preparations in orbit around " + getSource().getName() + ".", oPad);
                }
            } else if (Objects.equals(TRAVEL_ACTION, currentAction.getId())) {
                if (getSource().getMarket() == null) {
                    info.addPara("Traveling to the " + target.getNameWithLowercaseTypeShort() + ".", oPad);
                } else {
                    info.addPara("Traveling from " + getSource().getMarket().getName() + " to the " +
                            target.getNameWithLowercaseTypeShort() + ".", oPad);
                }
            } else if (Objects.equals(PAYLOAD_ACTION, currentAction.getId())) {
                info.addPara("Exploring the " + target.getNameWithLowercaseTypeShort(), oPad);
            } else if (Objects.equals(RETURN_ACTION, currentAction.getId())) {
                if (getSource().getMarket() == null) {
                    info.addPara("Returning to their port of origin.", oPad);
                } else {
                    String lootDesc = getLootDescription(this.lootMult, true);
                    String lootHighlight = getLootDescription(this.lootMult, false);
                    Color lootColor = getLootDescColor(this.lootMult);

                    LabelAPI label = info.addPara("Returning to " + getSource().getMarket().getName() + " in the "
                            + source.getNameWithLowercaseTypeShort() + ". Has " + lootDesc
                            + " of carrying valuable salvage.", oPad);
                    label.setHighlightColors(getFaction().getBaseUIColor(), Misc.getTextColor(), lootColor);
                    label.setHighlight(getSource().getMarket().getName(), getNameWithNoType(source.getNameWithLowercaseTypeShort()), lootHighlight);
                }
            }
        } else if (isSucceeded()) {
            info.addPara("The " + forces + " have returned from the " + target.getNameWithLowercaseTypeShort()
                    + ". Any valuable salvage they recovered will most likely be used and distributed.", oPad);
        } else if (isFailed()) {
            boolean prepareFailed = this.waitAction.isActionFinished() && isAborted();
            boolean travelFailed = this.travelAction.isActionFinished() && isAborted() && prepareFailed;
            boolean payloadFailed = this.payloadAction.isActionFinished() && isAborted() && travelFailed;
            boolean returnFailed = this.returnAction.isActionFinished() && isAborted() && payloadFailed;

            if (returnFailed) {
                info.addPara("Failed to return to the " + source.getNameWithLowercaseTypeShort(), oPad);
            } else if (payloadFailed) {
                info.addPara("Failed to explore the " + target.getNameWithLowercaseTypeShort(), oPad);
            } else if (travelFailed) {
                info.addPara("Failed to reach the " + target.getNameWithLowercaseTypeShort(), oPad);
            } else if (prepareFailed) {
                info.addPara("Failed to depart from the " + source.getNameWithLowercaseTypeShort(), oPad);
            } else {
                info.addPara("Failed to complete their objectives", oPad);
            }
        }
    }

    protected String getLootDescription(float lootMult, boolean hasAOrAn) {
        String result = "a very low chance";
        if (lootMult >= 1f) {
            result = "a guaranteed chance";
        } else if (lootMult >= 0.75f) {
            result = "a very high chance";
        } else if (lootMult >= 0.50f) {
            result = "a high chance";
        } else if (lootMult >= 0.25f) {
            result = "a low chance";
        } else if (lootMult >= 0.05f) {
            result = "a very low chance";
        }

        if (!hasAOrAn) {
            result = result.replace("a ", "");
            result = result.replace("an ", "");
        }

        return result;
    }

    protected Color getLootDescColor(float lootMult) {
        if (lootMult >= 0.75f) {
            return Misc.getStoryOptionColor();
        } else if (lootMult >= 0.50f) {
            return Misc.getHighlightColor();
        }

        return Misc.getGrayColor();
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        FGAction currentAction = getCurrentAction();
        Color s = Misc.getHighlightColor();
        StarSystemAPI target = this.params.target;
        StarSystemAPI source = this.params.source.getStarSystem();

        float untilDeployment = getETAUntil(PREPARE_ACTION);
        float untilDeparture = getETAUntil(TRAVEL_ACTION);
        float untilArrival = getETAUntil(PAYLOAD_ACTION);
        float untilReturn = getETAUntil(RETURN_ACTION, true);

        if (currentAction != null) {
            if (Objects.equals(PREPARE_ACTION, currentAction.getId())) {
                if (!isEnding()) {
                    if (untilDeployment > 0) {
                        if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                            info.addPara("Deploying in the %s", initPad, tc, s, source.getNameWithLowercaseTypeShort());
                            initPad = 0f;
                        }
                        addETABulletPoints(null, null, false, untilDeployment, ETAType.DEPLOYMENT, info, tc, initPad);
                    } else if (untilDeparture > 0) {
                        if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                            info.addPara("Preparing in the %s", initPad, tc, s, source.getNameWithLowercaseTypeShort());
                            initPad = 0f;
                        }
                        addETABulletPoints(null, null, false, untilDeparture, ETAType.DEPARTURE, info, tc, initPad);
                    }
                }
            } else if (Objects.equals(TRAVEL_ACTION, currentAction.getId())) {
                if (!isEnding()) {
                    if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                        info.addPara("Traveling to the %s", initPad, tc, s, target.getNameWithLowercaseTypeShort());
                        initPad = 0f;
                    }
                    addETABulletPoints(target.getNameWithLowercaseTypeShort(), s, true, untilArrival, ETAType.ARRIVING, info, tc, initPad);
                }
            } else if (Objects.equals(PAYLOAD_ACTION, currentAction.getId())) {
                if (!isEnding()) {
                    if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                        LabelAPI label = info.addPara("Exploring the " + target.getNameWithLowercaseTypeShort(), initPad, tc, s, target.getNameWithNoType());
                        label.setHighlightColors(s);
                        label.setHighlight(target.getNameWithNoType());
                        initPad = 0f;
                    }
                }
            } else if (Objects.equals(RETURN_ACTION, currentAction.getId())) {
                if (!isEnding()) {
                    if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                        LabelAPI label = info.addPara("Returning to the " + getSource().getStarSystem().getNameWithLowercaseTypeShort(), tc, initPad);
                        label.setHighlightColors(s);
                        label.setHighlight(getSource().getStarSystem().getNameWithNoType());
                        initPad = 0f;
                    }
                    addETABulletPoints(source.getNameWithLowercaseTypeShort(), s, false, untilReturn, ETAType.RETURNING, info, tc, initPad);
                }
            }
        }
    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        addNonUpdateBulletPoints(info, tc, param, mode, initPad);
    }

    @Override
    protected void addETABulletPoints(String destName, Color destHL, boolean withDepartedText, float eta, ETAType type, TooltipMakerAPI info, Color tc, float initPad) {
        Color h = Misc.getHighlightColor();
        String hl = getNameWithNoType(destName);
        LabelAPI label;

        if (type == ETAType.DEPLOYMENT) {
            if ((int) eta <= 0) {
                info.addPara("Fleet deployment imminent", tc, initPad);
            } else {
                String days = (int) eta == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until fleet deployment",
                        initPad, tc, h, "" + (int) eta);
            }
            return;
        }

        if (type == ETAType.DEPARTURE) {
            if ((int) eta <= 0) {
                info.addPara("Departure imminent", tc, initPad);
            } else {
                String days = (int) eta == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until departure",
                        initPad, tc, h, "" + (int) eta);
            }
            return;
        }

        if ((int) eta > 0) {
            String days = (int) eta == 1 ? "day" : "days";
            String post = " until arrival";
            if (type == ETAType.RETURNING) {
                post = " until return";
            }
            if (!withDepartedText) {
                if (type == ETAType.RETURNING) {
                    post += " to " + destName;
                } else if (type == ETAType.ARRIVING) {
                    post += " at " + destName;
                }
            }
            label = info.addPara("Estimated %s " + days + post, initPad, tc, h, "" + (int) eta);

            if (!withDepartedText && destHL != null && label != null) {
                label.setHighlightColors(h, destHL);
                label.setHighlight("" + (int) eta, hl);
            }
        } else {
            String pre = "Arrival at ";
            if (type == ETAType.RETURNING) {
                pre = "Return to ";
            }

            // REVIEW: Remove destination name as its already mentioned in the intel
            label = info.addPara(pre + destName + " is imminent", tc, initPad);

            if (destHL != null && label != null) {
                label.setHighlightColor(destHL);
                label.highlightLast(hl);
            }
        }
    }

    @Override
    public boolean isSucceeded() {
        return this.returnAction.isActionFinished() && !isAborted();
    }

    @Override
    public boolean isFailed() {
        return isAborted() && shouldAbort();
    }

    @Override
    protected boolean shouldAbort() {
        return isSpawnedFleets() && !isSpawning() && getMainFleet() == null;
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        super.notifyActionFinished(action);

        if (Objects.equals(PREPARE_ACTION, action.getId())) {
            this.waitAction.setActionFinished(true);
        } else if (Objects.equals(TRAVEL_ACTION, action.getId())) {
            this.travelAction.setActionFinished(true);
        } else if (Objects.equals(PAYLOAD_ACTION, action.getId())) {
            this.payloadAction.setActionFinished(true);
            List<Float> probabilities = new ArrayList<>();
            float totalLootMult = this.lootMult;
            for (float i = totalLootMult; i >= 0f; i--) {
                probabilities.add(Math.min(i, 1f));
            }

            CargoAPI loot = Global.getFactory().createCargo(true);
            for (float probability : probabilities) {
                if (getRandom().nextFloat() < probability) {
                    String id = pickSpecialItem();
                    loot.addSpecial(new SpecialItemData(id, null), 1);
                }
            }

            BaseSalvageSpecial.addExtraSalvage(getMainFleet(), loot);
        } else if (Objects.equals(RETURN_ACTION, action.getId())) {
            this.returnAction.setActionFinished(true);
            // TODO: Upon completion install any special items usable by the market
            // TODO: Any unused special items will be sent to other same faction markets that can use them
        }
    }

    protected String pickSpecialItem() {
        WeightedRandomPicker<SpecialItemSpecAPI> specialItemPicker = new WeightedRandomPicker<>(getRandom());
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (!spec.hasTag("hist3t") || Objects.equals(spec.getId(), Items.CORONAL_PORTAL) || Objects.equals(spec.getId(), Items.ORBITAL_FUSION_LAMP)) {
                continue;
            }
            float weight = 10f;
            for (Industry i : getSource().getMarket().getIndustries()) {
                if (i.wantsToUseSpecialItem(new SpecialItemData(spec.getId(), null))) {
                    weight += 3f; // Increase weight if source market can use special item
                }
            }
            if (Objects.equals(spec.getId(), Items.PRISTINE_NANOFORGE)) {
                weight = 3f;
            } else if (Objects.equals(spec.getId(), Items.CORRUPTED_NANOFORGE)) {
                weight = 7f;
            }
            specialItemPicker.add(spec, weight);
        }
        return specialItemPicker.pick().getId();
    }


    public CampaignFleetAPI getMainFleet() {
        return getFleets().stream()
                .filter(fleet -> fleet.getMemoryWithoutUpdate().getBoolean("$sep_expeditionFleet"))
                .findFirst()
                .orElse(null);
    }

    public static class ExpeditionParams {
        public Random random;
        public MarketAPI source;
        public StarSystemAPI target;
        public String factionId;
        public List<Integer> fleetSizes = new ArrayList<>();
        public FleetCreatorMission.FleetStyle style = FleetCreatorMission.FleetStyle.STANDARD;
        public float prepDays = 5f;
        public float payloadDays = 15f;
        public boolean makeFleetsHostile = false;
        public HubMissionWithTriggers.ComplicationRepImpact repImpact = HubMissionWithTriggers.ComplicationRepImpact.FULL;
        public String noun = "expedition";
        public String forcesNoun = "expedition forces";

        public ExpeditionParams(Random random) {
            this.random = random;
        }
    }
}

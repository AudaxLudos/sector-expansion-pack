package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.hub.SEPHubMission;

import java.awt.*;
import java.util.Objects;

public class IncursionFGI extends GenericRaidFGI {
    public static Logger log = Global.getLogger(IncursionFGI.class);

    protected Industry industry;
    protected SpecialItemData specialItem;

    public IncursionFGI() {
        super(null);
        this.params = new GenericRaidParams(Misc.random, false);

        pickSource();
        if (isDone()) {
            return;
        }

        pickTarget();
        if (isDone()) {
            return;
        }

        pickIndustry();
        if (isDone()) {
            return;
        }

        pickSpecialItem();
        if (isDone()) {
            return;
        }

        float totalDifficulty = 26f;
        this.params.fleetSizes.add(10);
        this.params.fleetSizes.add(8);
        this.params.fleetSizes.add(8);

        this.params.makeFleetsHostile = false;
        this.params.repImpact = HubMissionWithTriggers.ComplicationRepImpact.FULL;
        this.params.noun = "incursion";
        this.params.forcesNoun = "incursion forces";
        setRandom(this.random);
        setPostingLocation(this.params.source.getPrimaryEntity());
        initActions();
        Global.getSector().getIntelManager().queueIntel(this);
        log.info(String.format("Starting incursion by %s at %s in the %s with a total difficulty value of %s",
                getFaction().getDisplayName(), getSource().getMarket().getName(),
                getSource().getStarSystem().getNameWithLowercaseTypeShort(), totalDifficulty));
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
        picker.requireMarketNotHidden();
        picker.requireMarketFactionNotPlayer();
        picker.requireMarketHasInstalledItems();

        MarketAPI picked = picker.pickMarket();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.params.raidParams.where = picked.getStarSystem();
        this.params.raidParams.allowedTargets.add(picked);
    }

    protected void pickIndustry() {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>(this.params.random);
        MarketAPI targetMarket = getTargetMarket();

        if (targetMarket == null) {
            endImmediately();
            return;
        }

        for (Industry i : targetMarket.getIndustries()) {
            if (!i.getVisibleInstalledItems().isEmpty()) {
                industryPicker.add(i);
            }
        }

        Industry picked = industryPicker.pick();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.industry = picked;
    }

    protected void pickSpecialItem() {
        SpecialItemData picked = this.industry.getSpecialItem();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.specialItem = picked;
    }

    protected MarketAPI getTargetMarket() {
        if (this.params.raidParams.allowedTargets.isEmpty()) {
            return null;
        }
        return this.params.raidParams.allowedTargets.get(0);
    }

    @Override
    protected void addBasicDescription(TooltipMakerAPI info, float width, float height, float oPad) {
        FactionAPI faction = getFaction();
        StarSystemAPI system = this.raidAction.getWhere();
        String noun = getNoun();

        info.addImage(faction.getLogo(), width, 128, oPad);
        info.addPara(Misc.ucFirst(faction.getPersonNamePrefixAOrAn()) + " %s " + noun + " is targeting a colony in the "
                        + system.getNameWithLowercaseTypeShort() + ".", oPad,
                faction.getBaseUIColor(), faction.getPersonNamePrefix());
    }

    @Override
    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height, float opad) {
        FactionAPI faction = getFaction();
        MarketAPI targetMarket = getTargetMarket();
        String noun = getNoun();

        if (!isEnding() && !isSucceeded() && !isFailed()) {
            info.addSectionHeading("Assessment", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
            if (targetMarket == null) {
                info.addPara("There are no colonies for the " + noun + " to target in the system.", opad);
            } else {
                StarSystemAPI system = this.raidAction.getWhere();
                String forces = getForcesNoun();

                boolean potentialDanger = addStrengthDesc(info, opad, system, forces,
                        "the " + noun + " is unlikely to find success",
                        "the outcome of the " + noun + " is uncertain",
                        "the " + noun + " is likely to find success");

                if (potentialDanger) {
                    String safe = "should be safe from the " + noun;
                    String risk = "is at risk of losing special items installed on its structures.";
                    String highlight = "losing special items";

                    float raidStr = getRoute().getExtra().getStrengthModifiedByDamage();
                    float defenderStr = WarSimScript.getEnemyStrength(getFaction(), system, isPlayerTargeted());
                    float defensiveStr = defenderStr + WarSimScript.getStationStrength(targetMarket.getFaction(), system, targetMarket.getPrimaryEntity());
                    boolean isSafe = defensiveStr > raidStr * 1.25f;

                    if (isSafe) {
                        info.addPara("However, all colonies " + safe + ", owing to their orbital defenses.", opad);
                    } else {
                        info.addPara("A colony " + risk, opad, Misc.getNegativeHighlightColor(), highlight);
                    }
                }
            }
        }
    }

    @Override
    protected void addStatusSection(TooltipMakerAPI info, float width, float height, float oPad) {
        FGAction currentAction = getCurrentAction();
        StarSystemAPI target = this.raidAction.getWhere();
        StarSystemAPI source = this.params.source.getStarSystem();
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
                    info.addPara("Traveling from " + getSource().getMarket().getName() + " to the " + target.getNameWithLowercaseTypeShort() + ".", oPad);
                }
            } else if (Objects.equals(PAYLOAD_ACTION, currentAction.getId())) {
                addPayloadActionStatus(info, width, height, oPad);
            } else if (Objects.equals(RETURN_ACTION, currentAction.getId())) {
                if (getSource().getMarket() == null) {
                    info.addPara("Returning to their port of origin.", oPad);
                } else {
                    info.addPara("Returning to " + getSource().getMarket().getName() + " in the " +
                            this.origin.getContainingLocation().getNameWithLowercaseTypeShort() + ".", oPad);
                }
            }
        } else if (isSucceeded()) {
            info.addPara("Successfully completed there objective and have returned to " + getSource().getMarket().getName()
                    + ". Any valuable item taken will most likely be used and distributed.", oPad);
        } else if (isFailed()) {
            boolean prepareFailed = this.waitAction.isActionFinished() && isAborted();
            boolean travelFailed = this.travelAction.isActionFinished() && isAborted() && prepareFailed;
            boolean payloadFailed = this.raidAction.isActionFinished() && isAborted() && travelFailed;
            boolean returnFailed = this.returnAction.isActionFinished() && isAborted() && payloadFailed;

            if (returnFailed) {
                info.addPara("Failed to return to the " + source.getNameWithLowercaseTypeShort(), oPad);
            } else if (payloadFailed) {
                info.addPara("Failed to attack the target colony", oPad);
            } else if (travelFailed) {
                info.addPara("Failed to reach the target colony", oPad);
            } else if (prepareFailed) {
                info.addPara("Failed to depart from the " + source.getNameWithLowercaseTypeShort(), oPad);
            } else {
                info.addPara("Failed to complete their objectives", oPad);
            }
        }
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        FGAction currentAction = getCurrentAction();
        Color s = Misc.getHighlightColor();
        StarSystemAPI target = getTargetSystem();
        StarSystemAPI source = getSource().getStarSystem();
        MarketAPI targetMarket = getTargetMarket();

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
                    addETABulletPoints(target.getNameWithLowercaseTypeShort(), s, false, untilArrival, ETAType.ARRIVING, info, tc, initPad);
                }
            } else if (Objects.equals(PAYLOAD_ACTION, currentAction.getId())) {
                if (!isEnding()) {
                    if (mode == ListInfoMode.INTEL || mode == ListInfoMode.MESSAGES) {
                        LabelAPI label = info.addPara("Targeting " + targetMarket.getName() + " in the "
                                + target.getNameWithLowercaseTypeShort(), initPad, tc, s, target.getNameWithNoType());
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
    public boolean isSucceeded() {
        return this.returnAction.isActionFinished() && !this.isAborted();
    }
}

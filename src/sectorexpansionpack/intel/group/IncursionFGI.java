package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.hub.SEPHubMission;

import java.awt.*;
import java.util.List;

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

                    float raidStr  = getRoute().getExtra().getStrengthModifiedByDamage();
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
}

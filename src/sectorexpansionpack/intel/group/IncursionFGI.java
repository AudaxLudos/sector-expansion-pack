package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.hub.SEPHubMission;

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
}

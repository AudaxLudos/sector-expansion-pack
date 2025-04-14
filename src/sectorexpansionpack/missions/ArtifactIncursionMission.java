package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.GroundRaidObjectivesListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.graid.SpecialItemRaidObjectivePluginImpl;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArtifactIncursionMission extends HubMissionWithBarEvent implements GroundRaidObjectivesListener {
    public static float MISSION_DURATION = 120f;

    public enum Stage {
        RAID_ARTIFACT,
        DELIVER_ARTIFACT,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }

    protected PersonAPI person;
    protected MarketAPI market;
    protected Industry industry;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketCMD.RaidDangerLevel danger;
    protected SpecialItemRaidObjectivePluginImpl objectivePlugin;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.person = getPerson();
        if (!setPersonMissionRef(this.person, "$sep_aim_ref")) {
            return false;
        }

        requireMarketFactionNot(this.person.getFaction().getId());
        requireMarketFactionHostileTo(this.person.getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketFactionNotPlayer();
        requireMarketHasItemsInstalled();
        preferMarketAnyItemCompatibleWithOtherMarket(this.person.getMarket());
        preferMarketFactionHostileTo(this.person.getFaction().getId());
        this.market = pickMarket();

        if (!setMarketMissionRef(this.market, "$sep_aim_ref")) {
            return false;
        }

        pickIndustryWithItem();
        if (this.industry == null) {
            return false;
        }

        pickItemFromIndustry();
        if (this.specialItemSpec == null || this.specialItemData == null) {
            return false;
        }

        makeImportant(this.market, "$sep_aim_raidHere", Stage.RAID_ARTIFACT);
        makeImportant(this.person, "$sep_aim_deliverHere", Stage.DELIVER_ARTIFACT);

        setStartingStage(Stage.RAID_ARTIFACT);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithMemoryFlag(Stage.RAID_ARTIFACT, Stage.DELIVER_ARTIFACT, this.market, "$sep_aim_deliverArtifact");
        setStageOnMemoryFlag(Stage.COMPLETED, this.person, "$sep_aim_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RAID_ARTIFACT, Stage.FAILED_DECIV, this.market);
        connectWithMarketDecivilized(Stage.DELIVER_ARTIFACT, Stage.FAILED_DECIV, this.person.getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, this.market);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        setTimeLimit(Stage.FAILED, MISSION_DURATION, null);

        this.danger = getDangerLevel();
        int bonus = getRewardBonusForMarines(getMarinesRequiredForCustomObjective(this.market, getDangerLevel()));
        bonus += Math.round(this.specialItemSpec.getBasePrice() * 0.5f);
        setCreditRewardWithBonus(CreditReward.VERY_HIGH, bonus);
        setRepChanges(0.1f, 0.2f, 0.1f, 0.2f);

        return true;
    }

    public void pickIndustryWithItem() {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : this.market.getIndustries()) {
            for (SpecialItemData ignored : industry.getVisibleInstalledItems()) {
                industryPicker.add(industry);
            }
        }

        this.industry = industryPicker.pick();
    }

    public void pickItemFromIndustry() {
        WeightedRandomPicker<SpecialItemData> specialItemPicker = new WeightedRandomPicker<>();
        for (Industry industry : this.market.getIndustries()) {
            for (SpecialItemData data : industry.getVisibleInstalledItems()) {
                specialItemPicker.add(data);
            }
        }

        SpecialItemData picked = specialItemPicker.pick();
        if (picked == null) {
            return;
        }
        this.specialItemData = picked;
        this.specialItemSpec = Global.getSettings().getSpecialItemSpec(picked.getId());
    }

    public MarketCMD.RaidDangerLevel getDangerLevel() {
        MarketCMD.RaidDangerLevel level = this.specialItemSpec.getBaseDanger();
        if (this.industry != null) {
            level = this.industry.adjustItemDangerLevel(this.specialItemData.getId(), this.specialItemData.getData(), level);
        }
        return level;
    }

    @Override
    public String getBaseName() {
        return "Artifact Incursion";
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == Stage.RAID_ARTIFACT) {
            info.addPara("Take the %s being used on %s in the %s", pad, tc, tc,
                    this.specialItemSpec.getName(), this.market.getName(), this.market.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (this.currentStage == Stage.DELIVER_ARTIFACT) {
            info.addPara("Deliver the %s to %s at %s in the %s", pad, tc, tc,
                    this.specialItemSpec.getName(), this.person.getName().getFullName(), this.person.getMarket().getName(),
                    this.person.getMarket().getStarSystem().getNameWithLowercaseTypeShort());
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;
        Color textColor = Misc.getTextColor();
        Color boldColor = Misc.getHighlightColor();
        Color giverColor = this.person.getFaction().getBaseUIColor();
        Color targetColor = this.market.getFaction().getBaseUIColor();
        Color marketColor = this.person.getMarket().getTextColorForFactionOrPlanet();
        if (this.currentStage == Stage.RAID_ARTIFACT) {
            info.addPara("Take the %s being used on %s a size %s colony, in the %s controlled by the %s.", oPad,
                    new Color[]{boldColor, targetColor, boldColor, textColor, targetColor},
                    this.specialItemSpec.getName(), this.market.getName(), this.market.getSize() + "",
                    this.market.getStarSystem().getNameWithLowercaseTypeShort(), this.market.getFaction().getDisplayName());
            addCustomRaidInfo(this.market, this.danger, info, oPad);
        } else if (this.currentStage == Stage.DELIVER_ARTIFACT) {
            info.addPara("Deliver the %s to %s at %s a size %s colony, in the %s controlled by the %s.", oPad,
                    new Color[]{boldColor, giverColor, marketColor, boldColor, textColor, marketColor},
                    this.specialItemSpec.getName(), this.person.getName().getFullName(), this.person.getMarket().getName(),
                    this.person.getMarket().getSize() + "", this.person.getMarket().getStarSystem().getNameWithLowercaseTypeShort(),
                    this.person.getMarket().getFaction().getDisplayName());
        }
    }

    @Override
    public String getStageDescriptionText() {
        if (this.currentStage == Stage.FAILED_DECIV) {
            if (this.person.getMarket().isPlanetConditionMarketOnly()) {
                return "The " + getMissionTypeNoun() + " has failed due to " + this.person.getMarket().getName() + " becoming decivilized. No reputation penalty will be applied for this outcome.";
            } else if (this.market.isPlanetConditionMarketOnly()) {
                return "The " + getMissionTypeNoun() + " has failed due to " + this.market.getName() + " becoming decivilized. No reputation penalty will be applied for this outcome.";
            }
        }

        return null;
    }

    @Override
    protected void addBulletPointsPost(TooltipMakerAPI info, Color tc, float initPad, ListInfoMode mode) {
        Color boldColor = Misc.getHighlightColor();
        if (mode == ListInfoMode.IN_DESC && isSucceeded()) {
            if (isSucceeded() && this.creditReward <= 0) {
                info.addPara("%s artifact", initPad, tc, boldColor, this.specialItemSpec.getName());
            }
        }
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_aim_missionDuration", Misc.getWithDGS(MISSION_DURATION) + " days");
        set("$sep_aim_reward", Misc.getDGSCredits(getCreditsReward()));
        set("$sep_aim_marketName", this.market.getName());
        set("$sep_aim_marines", getMarinesRequiredForCustomObjective(this.market, this.danger));
        set("$sep_aim_distance", getDistanceLY(this.market));
        set("$sep_aim_artifactId", this.specialItemSpec.getId());
        set("$sep_aim_artifactName", this.specialItemSpec.getName());
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (this.creditReward > 0) {
            InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(this.specialItemData.getId());
            MarketAPI market = this.person.getMarket();
            if (Objects.equals(market.getFactionId(), this.person.getFaction().getId())) {
                for (Industry industry : market.getIndustries()) {
                    if (!industry.wantsToUseSpecialItem(this.specialItemData) || effect == null) {
                        continue;
                    }
                    List<String> unmet = effect.getUnmetRequirements(industry);
                    if (unmet != null && !unmet.isEmpty()) {
                        continue;
                    }

                    // OPTION: Send intel message when special item is installed
                    industry.setSpecialItem(this.specialItemData);
                    break;
                }
            }
        }
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("noCreditReward")) {
            setCreditReward(0);
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    public void modifyRaidObjectives(MarketAPI market, SectorEntityToken entity, List<GroundRaidObjectivePlugin> objectives, MarketCMD.RaidType type, int marineTokens, int priority) {
        if (priority != 1) {
            return;
        }

        if (market == this.market && entity == this.market.getPrimaryEntity()) {
            for (GroundRaidObjectivePlugin obj : objectives) {
                if (obj instanceof SpecialItemRaidObjectivePluginImpl) {
                    SpecialItemRaidObjectivePluginImpl plugin = (SpecialItemRaidObjectivePluginImpl) obj;
                    if (plugin.getItemSpec() == this.specialItemSpec && obj.getSource() == this.industry) {
                        this.objectivePlugin = plugin;
                        obj.setNameOverride(plugin.getItemSpec().getName() + " (Mission Target)");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void reportRaidObjectivesAchieved(RaidResultData data, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        boolean found = false;
        for (GroundRaidObjectivePlugin obj : data.objectives) {
            if (obj instanceof SpecialItemRaidObjectivePluginImpl) {
                if (obj == this.objectivePlugin) {
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            advance(0.1f); // triggers removal of objective
            dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$raidMarinesLost", data.marinesLost, 0);
            FireAll.fire(null, dialog, memoryMap, "SEPAIMRaidFinished");
            Global.getSector().getListenerManager().removeListener(this);
        }
    }

    public void preferMarketAnyItemCompatibleWithOtherMarket(MarketAPI other) {
        this.search.marketPrefs.add(new MarketAnyItemCompatibleWithOtherMarketReq(other));
    }

    public static class MarketAnyItemCompatibleWithOtherMarketReq implements MarketRequirement {
        MarketAPI other;

        public MarketAnyItemCompatibleWithOtherMarketReq(MarketAPI other) {
            this.other = other;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            List<SpecialItemData> installedItems = new ArrayList<>();
            for (Industry ind : market.getIndustries()) {
                installedItems.addAll(ind.getVisibleInstalledItems());
            }

            for (Industry ind : this.other.getIndustries()) {
                for (SpecialItemData data : installedItems) {
                    InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(data.getId());
                    if (!ind.wantsToUseSpecialItem(data) || effect == null) {
                        continue;
                    }
                    List<String> unmet = effect.getUnmetRequirements(ind);
                    if (unmet != null && !unmet.isEmpty()) {
                        continue;
                    }

                    return true;
                }
            }

            return false;
        }
    }

    public void requireMarketHasItemsInstalled() {
        this.search.marketReqs.add(new MarketHasItemsInstalledReq());
    }

    public static class MarketHasItemsInstalledReq implements MarketRequirement {
        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry industry : market.getIndustries()) {
                if (!industry.getVisibleInstalledItems().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }
}

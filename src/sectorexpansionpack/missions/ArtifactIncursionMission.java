package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.GroundRaidObjectivesListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.graid.SpecialItemRaidObjectivePluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// TODO: Transfer previous special item if selected market industry has one
// TODO: Delay special item installation by some days
// TODO: Improve special item installation message
// TODO: Add chance to get a military contact from bar event
// TODO: Add custom dialogs to quick reaction force fleet
public class ArtifactIncursionMission extends HubMissionWithBarEvent implements GroundRaidObjectivesListener {
    public static Logger log = Global.getLogger(ArtifactIncursionMission.class);
    public static float MILITARY_CONTACT_CHANCE = 0.5f;
    public static float MISSION_DURATION = 120f;
    protected MarketAPI market;
    protected Industry industry;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketCMD.RaidDangerLevel danger;
    protected SpecialItemRaidObjectivePluginImpl objectivePlugin;

    public ArtifactIncursionMission() {
        super();
        setGenRandom(Utils.random);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            if (rollProbability(MILITARY_CONTACT_CHANCE)) {
                List<String> posts = new ArrayList<>();
                posts.add(Ranks.POST_AGENT);
                if (Misc.isMilitary(createdAt)) {
                    posts.add(Ranks.POST_BASE_COMMANDER);
                }
                if (Misc.hasOrbitalStation(createdAt)) {
                    posts.add(Ranks.POST_STATION_COMMANDER);
                }
                String post = pickOne(posts);

                setGiverRank(pickOne(Ranks.GROUND_CAPTAIN, Ranks.GROUND_COLONEL, Ranks.GROUND_MAJOR,
                        Ranks.SPACE_COMMANDER, Ranks.SPACE_CAPTAIN, Ranks.SPACE_ADMIRAL));
                setGiverTags(Tags.CONTACT_MILITARY);
                setGiverPost(post);
                setGiverImportance(pickHighImportance());
                findOrCreateGiver(createdAt, true, false);
            } else {
                setGiverRank(Ranks.CITIZEN);
                setGiverPost(pickOne(Ranks.POST_AGENT, Ranks.POST_SMUGGLER, Ranks.POST_ARMS_DEALER));
                setGiverImportance(pickHighImportance());
                setGiverFaction(Factions.PIRATES);
                setGiverTags(Tags.CONTACT_UNDERWORLD);
                findOrCreateGiver(createdAt, true, false);
            }
        }

        if (!setPersonMissionRef(getPerson(), "$sep_aim_ref")) {
            log.info("Failed find or create mission giver");
            return false;
        }

        PersonImportance importance = getPerson().getImportance();
        int minMarketSize = 3;
        int maxMarketSize = switch (importance) {
            case VERY_LOW, LOW -> 4;
            case MEDIUM -> 5;
            case HIGH -> 6;
            case VERY_HIGH -> 8;
        };

        requireMarketFactionNot(getPerson().getFaction().getId());
        requireMarketFactionHostileTo(getPerson().getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketFactionNotPlayer();
        requireMarketHasSpecialItemsInstalled();
        preferMarketSizeAtLeast(minMarketSize);
        preferMarketSizeAtMost(maxMarketSize);
        this.market = pickMarket();

        if (!setMarketMissionRef(this.market, "$sep_aim_ref")) {
            log.info("Failed find to find target market");
            return false;
        }

        pickIndustryWithItem();
        if (this.industry == null) {
            log.info("Failed find to find industry with artifact");
            return false;
        }

        pickItemFromIndustry();
        if (this.specialItemSpec == null || this.specialItemData == null) {
            log.info("Failed find to find item to raid");
            return false;
        }

        makeImportant(this.market, "$sep_aim_targetMarket", Stage.RAID_ARTIFACT);
        makeImportant(getPerson(), "$sep_aim_returnPerson", Stage.DELIVER_ARTIFACT);

        setStartingStage(Stage.RAID_ARTIFACT);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithMemoryFlag(Stage.RAID_ARTIFACT, Stage.DELIVER_ARTIFACT, this.market, "$sep_aim_deliverArtifact");
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_aim_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RAID_ARTIFACT, Stage.FAILED_DECIV, this.market);
        connectWithMarketDecivilized(Stage.DELIVER_ARTIFACT, Stage.FAILED_DECIV, getPerson().getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, this.market);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        addNoPenaltyFailureStages(Stage.FAILED_MARKET_FACTION_CHANGED);
        connectWithMarketFactionChanged(Stage.RAID_ARTIFACT, Stage.FAILED_MARKET_FACTION_CHANGED, this.market);
        setStageOnMarketFactionChanged(Stage.FAILED_MARKET_FACTION_CHANGED, this.market);

        setTimeLimit(Stage.FAILED, MISSION_DURATION, null);

        this.danger = getDangerLevel();
        int bonus = getRewardBonusForMarines(getMarinesRequiredForCustomObjective(this.market, this.danger));
        bonus += Math.round(this.specialItemSpec.getBasePrice() * 0.5f);
        setCreditRewardWithBonus(CreditReward.VERY_HIGH, bonus);
        setRepChanges(0.1f, 0.2f, 0.1f, 0.2f);

        if (this.market.getSize() <= 4) {
            triggerCreateMediumPatrolAroundMarket(this.market, Stage.RAID_ARTIFACT, 0f);
        } else if (this.market.getSize() <= 6) {
            triggerCreateLargePatrolAroundMarket(this.market, Stage.RAID_ARTIFACT, 0f);
        } else {
            triggerCreateMediumPatrolAroundMarket(this.market, Stage.RAID_ARTIFACT, 0f);
            triggerCreateLargePatrolAroundMarket(this.market, Stage.RAID_ARTIFACT, 0f);
        }

        triggerComplicationBegin(Stage.DELIVER_ARTIFACT, ComplicationSpawn.EXITING_SYSTEM,
                this.market.getStarSystem(), this.market.getFactionId(),
                "the " + this.specialItemSpec.getName(), "it",
                "the " + this.specialItemSpec.getName() + " you stole from " + this.market.getName(),
                0,
                true, ComplicationRepImpact.FULL, null);
        if (this.market.getSize() <= 4) {
            triggerSetFleetSize(FleetSize.LARGE);
        } else if (this.market.getSize() <= 6) {
            triggerSetFleetSize(FleetSize.LARGER);
            triggerSetFleetQuality(FleetQuality.HIGHER);
            triggerSetFleetType(FleetTypes.PATROL_LARGE);
        } else {
            triggerSetFleetSize(FleetSize.VERY_LARGE);
            triggerSetFleetQuality(FleetQuality.VERY_HIGH);
            triggerSetFleetType(FleetTypes.PATROL_LARGE);
        }
        triggerFleetSetName("Quick Reaction Force");
        triggerComplicationEnd(false);

        return true;
    }

    @Override
    public String getBaseName() {
        return "Artifact Incursion";
    }

    @Override
    public String getIcon() {
        if (this.specialItemSpec != null) {
            return this.specialItemSpec.getIconName();
        }
        return super.getIcon();
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getListenerManager().removeListener(this);

        if (this.result != null && this.result.reward <= 0) {
            return;
        }

        requireMarketFaction(getPerson().getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketFactionNotPlayer();
        requireMarketCanUseSpecialItem(this.specialItemData);
        preferMarketSizeAtMost(100);
        preferMarketIs(getPerson().getMarket());
        MarketAPI market = pickMarket();

        if (market == null) {
            log.info("Failed to find market to install special item");
            return;
        }

        // TODO: Delay installation by a few days
        Industry ind = pickIndustryToInstallItem(market, this.specialItemData);
        ind.setSpecialItem(this.specialItemData);
        ArtifactInstallationIntel intel = new ArtifactInstallationIntel(market, ind, this.specialItemSpec);
        Global.getSector().getIntelManager().queueIntel(intel);
        log.info("Installing special item in " + ind.getCurrentName() + " on " + market.getName() + " within the " + market.getStarSystem().getNameWithLowercaseTypeShort());
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
    protected void endFailureImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    protected void endAbandonImpl() {
        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_aim_artifactId", this.specialItemSpec.getId());
        set("$sep_aim_artifactName", this.specialItemSpec.getName());
        set("$sep_aim_industryName", this.industry.getCurrentName());
        set("$sep_aim_industryAOrAn", Misc.ucFirst(Misc.getAOrAnFor(this.industry.getCurrentName())));
        set("$sep_aim_marketFactionColor", this.market.getFaction().getBaseUIColor());
        set("$sep_aim_marketFactionName", this.market.getFaction().getDisplayName());
        set("$sep_aim_marketName", this.market.getName());
        set("$sep_aim_marketSystem", this.market.getStarSystem().getNameWithLowercaseType());
        set("$sep_aim_marines", Misc.getWithDGS(getMarinesRequiredForCustomObjective(this.market, this.danger)));
        set("$sep_aim_missionDuration", Misc.getWithDGS(MISSION_DURATION) + " days");
        set("$sep_aim_reward", Misc.getDGSCredits(getCreditsReward()));
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == Stage.RAID_ARTIFACT) {
            info.addPara("Take the artifact installed on %s %s facility at %s, in the %s.", pad, tc, tc,
                    Misc.getAOrAnFor(this.industry.getCurrentName()), this.industry.getCurrentName(),
                    this.market.getName(), this.market.getStarSystem().getNameWithLowercaseType());
        } else if (this.currentStage == Stage.DELIVER_ARTIFACT) {
            info.addPara("Deliver the artifact to %s at %s in the %s.", pad, tc, tc, getPerson().getName().getFullName(),
                    getPerson().getMarket().getName(), getPerson().getMarket().getStarSystem().getNameWithLowercaseType());
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;
        Color textColor = Misc.getTextColor();
        Color boldColor = Misc.getHighlightColor();
        Color giverColor = getPerson().getFaction().getBaseUIColor();
        Color targetColor = this.market.getFaction().getBaseUIColor();
        if (this.currentStage == Stage.RAID_ARTIFACT) {
            info.addPara("Take the artifact installed on %s %s facility at %s a size %s colony, in the %s controlled by the %s.", oPad,
                    new Color[]{textColor, boldColor, targetColor, boldColor, textColor, targetColor},
                    Misc.getAOrAnFor(this.industry.getCurrentName()), this.industry.getCurrentName(),
                    this.market.getName(), this.market.getSize() + "", this.market.getStarSystem().getNameWithLowercaseType(),
                    this.market.getFaction().getDisplayName());
            addCustomRaidInfo(this.market, this.danger, info, oPad);
        } else if (this.currentStage == Stage.DELIVER_ARTIFACT) {
            info.addPara("Deliver the artifact to %s at %s, in the %s.", oPad,
                    new Color[]{boldColor, giverColor, textColor},
                    getPerson().getName().getFullName(), getPerson().getMarket().getName(),
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseType());
        }
    }

    @Override
    public String getStageDescriptionText() {
        if (this.currentStage == Stage.FAILED_DECIV) {
            if (getPerson().getMarket().isPlanetConditionMarketOnly()) {
                return "The " + getMissionTypeNoun() + " has failed due to " + getPerson().getMarket().getName() + " becoming decivilized. No reputation penalty will be applied for this outcome.";
            } else if (this.market.isPlanetConditionMarketOnly()) {
                return "The " + getMissionTypeNoun() + " has failed due to " + this.market.getName() + " becoming decivilized. No reputation penalty will be applied for this outcome.";
            }
        } else if (this.currentStage == Stage.FAILED_MARKET_FACTION_CHANGED) {
            return "The " + getMissionTypeNoun() + " has failed due to " + this.market.getName() + " changing ownership. No reputation penalty will be applied for this outcome.";
        }

        return null;
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("setZeroCreditReward")) {
            setCreditReward(0);
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrow = new ArrowData(Global.getSector().getPlayerFleet(), getMapLocation(map));
        arrow.width = 14f;
        result.add(arrow);

        return result;
    }

    @Override
    public void modifyRaidObjectives(MarketAPI market, SectorEntityToken entity, List<GroundRaidObjectivePlugin> objectives, MarketCMD.RaidType type, int marineTokens, int priority) {
        if (priority != 1) {
            return;
        }
        if (market == this.market && entity == this.market.getPrimaryEntity()) {
            for (GroundRaidObjectivePlugin obj : objectives) {
                if (obj instanceof SpecialItemRaidObjectivePluginImpl plugin) {
                    if (plugin.getItemSpec() == this.specialItemSpec && plugin.getSource() == this.industry) {
                        this.objectivePlugin = plugin;
                        plugin.setNameOverride("Take " + plugin.getItemSpec().getName() + " for " + getPerson().getNameString());
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

    public MarketCMD.RaidDangerLevel getDangerLevel() {
        MarketCMD.RaidDangerLevel level = this.specialItemSpec.getBaseDanger();
        if (this.industry != null) {
            level = this.industry.adjustItemDangerLevel(this.specialItemData.getId(), this.specialItemData.getData(), level);
        }
        return level;
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

        this.specialItemData = specialItemPicker.pick();
        if (this.specialItemData == null) {
            return;
        }
        this.specialItemSpec = Global.getSettings().getSpecialItemSpec(this.specialItemData.getId());
    }

    public void requireMarketHasSpecialItemsInstalled() {
        this.search.marketReqs.add(new MarketHasSpecialItemsInstalledReq());
    }

    public void requireMarketCanUseSpecialItem(SpecialItemData specialItemData) {
        this.search.marketReqs.add(new MarketCanUseSpecialItemReq(specialItemData));
    }

    public void connectWithMarketFactionChanged(Object from, Object to, MarketAPI market) {
        this.connections.add(new StageConnection(from, to, new MarketFactionChangedChecker(market)));
    }

    public void setStageOnMarketFactionChanged(Object to, MarketAPI market) {
        this.connections.add(new StageConnection(null, to, new MarketFactionChangedChecker(market)));
    }

    public enum Stage {
        RAID_ARTIFACT,
        DELIVER_ARTIFACT,
        COMPLETED,
        FAILED,
        FAILED_DECIV,
        FAILED_MARKET_FACTION_CHANGED
    }

    public static class MarketHasSpecialItemsInstalledReq implements MarketRequirement {
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

    public static class MarketCanUseSpecialItemReq implements MarketRequirement {
        public SpecialItemData specialItemData;

        public MarketCanUseSpecialItemReq(SpecialItemData specialItemData) {
            this.specialItemData = specialItemData;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry industry : market.getIndustries()) {
                if (industry.wantsToUseSpecialItem(this.specialItemData)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class MarketFactionChangedChecker implements ConditionChecker {
        public String prevFactionId;
        public MarketAPI market;

        public MarketFactionChangedChecker(MarketAPI market) {
            this.prevFactionId = market.getFactionId();
            this.market = market;
        }

        @Override
        public boolean conditionsMet() {
            return !Objects.equals(this.market.getFactionId(), this.prevFactionId);
        }
    }
}

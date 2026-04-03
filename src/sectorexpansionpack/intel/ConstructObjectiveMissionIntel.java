package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Set;

public class ConstructObjectiveMissionIntel extends BaseMissionIntel {
    public static Logger log = Global.getLogger(ClearDebrisFieldsMissionIntel.class);
    protected MarketAPI market;
    protected StarSystemAPI system;
    protected String objectiveType;
    protected FactionAPI faction;
    protected int reward;
    protected IntervalUtil timer = new IntervalUtil(0.1f, 0.3f);

    public ConstructObjectiveMissionIntel(StarSystemAPI system) {
        this.system = system;
        if (this.system == null) {
            endImmediately();
            return;
        }

        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isHidden()) {
                continue;
            }
            if (market.getFaction().isPlayerFaction()) {
                continue;
            }
            marketPicker.add(market, market.getSize());
        }

        this.market = marketPicker.pick();
        if (this.market == null) {
            endImmediately();
            return;
        }

        this.faction = this.market.getFaction();
        if (this.faction == null) {
            endImmediately();
            return;
        }

        WeightedRandomPicker<String> objectivePicker = new WeightedRandomPicker<>();
        objectivePicker.add(Tags.COMM_RELAY);
        objectivePicker.add(Tags.SENSOR_ARRAY);
        objectivePicker.add(Tags.NAV_BUOY);
        for (SectorEntityToken entity : this.system.getEntitiesWithTag(Tags.OBJECTIVE)) {
            if (entity.hasTag(Tags.COMM_RELAY)) {
                objectivePicker.remove(Entities.COMM_RELAY);
            }
            if (entity.hasTag(Tags.SENSOR_ARRAY)) {
                objectivePicker.remove(Tags.SENSOR_ARRAY);
            }
            if (entity.hasTag(Tags.NAV_BUOY)) {
                objectivePicker.remove(Tags.NAV_BUOY);
            }
        }

        this.objectiveType = objectivePicker.pick();
        if (this.objectiveType == null || this.objectiveType.isEmpty()) {
            endImmediately();
            return;
        }

        setDuration(120f);

        this.reward = (int) Misc.getDistance(new Vector2f(), this.market.getLocationInHyperspace());
        this.reward = 20000 + (this.reward / 10000) * 10000;
        if (this.reward < 10000) {
            this.reward = 10000;
        }

        log.info("Created ConstructObjectiveMissionIntel: faction: " + this.faction.getDisplayName());

        initRandomCancel();
        setPostingLocation(this.market.getPrimaryEntity());

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public void advanceMission(float amount) {
        this.timer.advance(amount);
        if (this.timer.intervalElapsed()) {
            if (this.system.getEntitiesWithTag(this.objectiveType) != null && !this.system.getEntitiesWithTag(this.objectiveType).isEmpty()) {
                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                CargoAPI cargo = playerFleet.getCargo();
                cargo.getCredits().add(this.reward);

                CoreReputationPlugin.MissionCompletionRep rep = new CoreReputationPlugin.MissionCompletionRep(CoreReputationPlugin.RepRewards.HIGH, RepLevel.WELCOMING,
                        -CoreReputationPlugin.RepRewards.TINY, RepLevel.INHOSPITABLE);
                ReputationActionResponsePlugin.ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                        new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.MISSION_SUCCESS, rep,
                                null, null, true, false),
                        this.faction.getId());
                setMissionResult(new MissionResult(this.reward, result));
                setMissionState(MissionState.COMPLETED);
                endMission();
                sendUpdateIfPlayerHasIntel(this.missionResult, false);
            }
        }
    }

    @Override
    public void missionAccepted() {
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public void endMission() {
        Global.getSector().getListenerManager().removeListener(this);
        endAfterDelay();
    }

    @Override
    protected MissionResult createAbandonedResult(boolean withPenalty) {
        if (withPenalty) {
            CoreReputationPlugin.MissionCompletionRep rep = new CoreReputationPlugin.MissionCompletionRep(
                    CoreReputationPlugin.RepRewards.HIGH, RepLevel.WELCOMING,
                    -CoreReputationPlugin.RepRewards.TINY, RepLevel.INHOSPITABLE);
            ReputationActionResponsePlugin.ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.MISSION_FAILURE, rep,
                            null, null, true, false),
                    this.faction.getId());
            return new MissionResult(0, result);
        }
        return new MissionResult();
    }

    @Override
    protected MissionResult createTimeRanOutFailedResult() {
        return createAbandonedResult(true);
    }

    @Override
    protected String getName() {
        CustomEntitySpecAPI objSpec = Global.getSettings().getCustomEntitySpec(this.objectiveType);
        return "Construct Makeshift " + objSpec.getDefaultName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return this.faction;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        CustomEntitySpecAPI objSpec = Global.getSettings().getCustomEntitySpec(this.objectiveType);

        info.addImage(this.faction.getLogo(), width, 128, opad);

        info.addPara("%s authorities have posted a reward for constructing " + objSpec.getAOrAn() + " makeshift "
                        + objSpec.getNameInText() + " in the " + this.market.getStarSystem().getNameWithLowercaseType() + ".",
                opad, this.faction.getBaseUIColor(), Misc.ucFirst(this.faction.getPersonNamePrefix()));

        if (isPosted() || isAccepted()) {
            addBulletPoints(info, ListInfoMode.IN_DESC);

            info.addPara("The following resources are required to construct " + objSpec.getAOrAn()
                    + " makeshift " + objSpec.getNameInText() + ":", opad);
            StatBonus statBonus = new StatBonus();
            CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(Commodities.HEAVY_MACHINERY);
            statBonus.modifyFlat("1", 15, Misc.ucFirst(commoditySpec.getLowerCaseName()));
            commoditySpec = Global.getSettings().getCommoditySpec(Commodities.METALS);
            statBonus.modifyFlat("2", 30, Misc.ucFirst(commoditySpec.getLowerCaseName()));
            commoditySpec = Global.getSettings().getCommoditySpec(Commodities.RARE_METALS);
            statBonus.modifyFlat("3", 5, Misc.ucFirst(commoditySpec.getLowerCaseName()));
            info.setLowGridRowHeight();
            info.addStatModGrid(200f, 50f, opad, opad, statBonus, new TooltipMakerAPI.StatModValueGetter() {
                @Override
                public String getFlatValue(MutableStat.StatMod mod) {
                    return (int) mod.value + "";
                }

                @Override
                public String getPercentValue(MutableStat.StatMod mod) {
                    return null;
                }

                @Override
                public String getMultValue(MutableStat.StatMod mod) {
                    return null;
                }

                @Override
                public Color getModColor(MutableStat.StatMod mod) {
                    return Misc.getHighlightColor();
                }
            });

            addGenericMissionState(info);

            addAcceptOrAbandonButton(info, width, "Accept", "Abandon");
        } else {
            addGenericMissionState(info);

            addBulletPoints(info, ListInfoMode.IN_DESC);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        boolean isUpdate = getListInfoParam() != null;

        if (isUpdate) {
            // 3 possible updates: de-posted/expired, failed, completed
            if (isFailed() || isCancelled()) {
                return;
            } else if (isCompleted()) {
                if (this.missionResult.payment > 0) {
                    info.addPara("%s received", initPad, tc, h, Misc.getDGSCredits(this.missionResult.payment));
                }
                CoreReputationPlugin.addAdjustmentMessage(this.missionResult.rep1.delta, this.faction, null,
                        null, null, info, tc, true, 0f);
            }
        } else {
            // either in small description, or in tooltip/intel list
            if (this.missionResult != null) {
                if (this.missionResult.payment > 0) {
                    info.addPara("%s received", initPad, tc, h, Misc.getDGSCredits(this.missionResult.payment));
                    initPad = 0f;
                }

                if (this.missionResult.rep1 != null) {
                    CoreReputationPlugin.addAdjustmentMessage(this.missionResult.rep1.delta, this.faction, null,
                            null, null, info, tc, isUpdate, initPad);
                    initPad = 0f;
                }
            } else {
                float betweenPad = 0f;
                if (mode != ListInfoMode.IN_DESC) {
                    info.addPara("Faction: " + this.faction.getDisplayName(), initPad, tc,
                            this.faction.getBaseUIColor(),
                            this.faction.getDisplayName());
                    initPad = betweenPad;
                } else {
                    betweenPad = 0f;
                }

                info.addPara("%s reward", initPad, tc, h, Misc.getDGSCredits(this.reward));
                addDays(info, "to complete", this.duration - this.elapsedDays, tc, betweenPad);
            }
        }

        unindent(info);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("campaignMissions", "survey_planet");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(this.faction.getId());
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.system.getCenter();
    }

    public StarSystemAPI getSystem() {
        return this.system;
    }
}

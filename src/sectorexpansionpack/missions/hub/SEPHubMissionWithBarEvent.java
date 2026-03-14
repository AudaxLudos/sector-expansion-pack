package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetAutoDespawn;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionTrigger;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.shared.PersonBountyEventData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.skills.OfficerTraining;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Settings;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.FleetEscortMission;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public abstract class SEPHubMissionWithBarEvent extends HubMissionWithBarEvent {
    public static void makeFleetInterceptOther(CampaignFleetAPI fleet, SectorEntityToken other, float interceptDays) {
        if (fleet.getAI() == null) {
            fleet.setAI(Global.getFactory().createFleetAI(fleet));
            fleet.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        }

        fleet.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, new Vector2f(other.getLocation()), interceptDays);

        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setTarget(other);
        }

        fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, other, interceptDays, null);
    }

    public void connectWithMarketFactionChanged(Object from, Object to, MarketAPI market) {
        this.connections.add(new StageConnection(from, to, new MarketFactionChangedChecker(market)));
    }

    public void setStageOnMarketFactionChanged(Object to, MarketAPI market) {
        this.connections.add(new StageConnection(null, to, new MarketFactionChangedChecker(market)));
    }

    public void requirePlanetNoSpecialSalvage() {
        this.search.planetReqs.add(new PlanetNoSpecialSalvage());
    }

    public void requireEntityNoSpecialSalvage() {
        this.search.entityReqs.add(new EntityNoSpecialSalvage());
    }

    public void preferMarketHasCommodityDemands() {
        this.search.marketPrefs.add(new MarketHasCommodityDemands());
    }

    public void requireMarketCanUseSpecialItem(SpecialItemData specialItemData) {
        this.search.marketReqs.add(new MarketCanUseSpecialItemReq(specialItemData));
    }

    public void requireMarketUsesSpecialItems() {
        this.search.marketReqs.add(new MarketUsesSpecialItems());
    }

    public void requireMarketHasCompatibleSpecialItemsWithOther(MarketAPI other) {
        this.search.marketReqs.add(new MarketHasCompatibleSpecialItemsWithOther(other));
    }

    public void requireEntityNoMemoryFlag(String flag) {
        this.search.entityReqs.add(new EntityBooleanMemoryFlag(flag, true));
    }

    public void requireMarketNoMemoryFlag(String flag) {
        this.search.marketReqs.add(new MarketBooleanMemoryFlag(flag, true));
    }

    public void requirePlanetNoMemoryFlag(String flag) {
        this.search.planetReqs.add(new PlanetBooleanMemoryFlag(flag, true));
    }

    public void requireMarketHasSpecialItemsInstalled() {
        this.search.marketReqs.add(new MarketUsesSpecialItems());
    }

    public void preferMarketHasCompatibleSpecialItemsWithOther(MarketAPI other) {
        this.search.marketPrefs.add(new MarketHasCompatibleSpecialItemsWithOther(other));
    }

    public void triggerSetFleetToStandardFleet(int difficulty) {
        FleetSize size;
        FleetQuality quality;
        String type;
        OfficerQuality oQuality;
        OfficerNum oNum;

        if (difficulty <= 0) {
            size = FleetSize.TINY;
            quality = FleetQuality.VERY_LOW;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 1) {
            size = FleetSize.VERY_SMALL;
            quality = FleetQuality.VERY_LOW;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 2) {
            size = FleetSize.SMALL;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FEWER;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 3) {
            size = FleetSize.SMALL;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 4) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 5) {
            size = FleetSize.LARGE;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 6) {
            size = FleetSize.LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 7) {
            size = FleetSize.LARGER;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 8) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 9) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else { // difficulty >= 10
            size = FleetSize.HUGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            // oNum = OfficerNum.ALL_SHIPS;
            type = FleetTypes.PATROL_LARGE;
        }

        triggerSetFleetSizeAndQuality(size, quality, type);
        triggerSetFleetOfficers(oNum, oQuality);
    }

    public void triggerSetFleetToQualityFleet(int difficulty) {
        FleetSize size;
        FleetQuality quality;
        String type;
        OfficerQuality oQuality;
        OfficerNum oNum;

        if (difficulty <= 0) {
            size = FleetSize.TINY;
            quality = FleetQuality.VERY_HIGH;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 1) {
            size = FleetSize.VERY_SMALL;
            quality = FleetQuality.VERY_HIGH;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 2) {
            size = FleetSize.VERY_SMALL;
            quality = FleetQuality.VERY_HIGH;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 3) {
            size = FleetSize.SMALL;
            quality = FleetQuality.VERY_HIGH;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 4) {
            size = FleetSize.SMALL;
            quality = FleetQuality.VERY_HIGH;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 5) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.SMOD_1;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 6) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.SMOD_1;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 7) {
            size = FleetSize.LARGE;
            quality = FleetQuality.SMOD_1;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 8) {
            size = FleetSize.LARGE;
            quality = FleetQuality.SMOD_1;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 9) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.SMOD_1;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else { // difficulty >= 10
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.SMOD_2;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        }

        triggerSetFleetSizeAndQuality(size, quality, type);
        triggerSetFleetOfficers(oNum, oQuality);
    }


    public void triggerSetFleetToQuantityFleet(int difficulty) {
        FleetSize size;
        FleetQuality quality;
        String type;
        OfficerQuality oQuality;
        OfficerNum oNum;

        if (difficulty <= 0) {
            size = FleetSize.SMALL;
            quality = FleetQuality.LOWER;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 1) {
            size = FleetSize.SMALL;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 2) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.LOWER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 3) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 4) {
            size = FleetSize.LARGE;
            quality = FleetQuality.LOWER;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 5) {
            size = FleetSize.LARGE;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 6) {
            size = FleetSize.LARGER;
            quality = FleetQuality.LOWER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 7) {
            size = FleetSize.LARGER;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 8) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 9) {
            size = FleetSize.HUGE;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else { // difficulty >= 10
            size = FleetSize.MAXIMUM;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        }

        triggerSetFleetSizeAndQuality(size, quality, type);
        triggerSetFleetOfficers(oNum, oQuality);
    }

    public void triggerScaleFleetToPlayerCapabilities(FleetStrengthType type) {
        SharedData sharedData = SharedData.getData();
        if (sharedData == null) {
            return;
        }
        PersonBountyEventData bountyData = sharedData.getPersonBountyEventData();
        if (bountyData == null) {
            return;
        }
        int level = bountyData.getLevel();
        if (type == FleetStrengthType.QUANTITY) {
            triggerSetFleetToQuantityFleet(level);
        } else if (type == FleetStrengthType.QUALITY) {
            triggerSetFleetToQuantityFleet(level);
        } else {
            triggerSetFleetToStandardFleet(level);
        }
    }

    public void triggerSEPCreateFleet(FleetSize size, FleetQuality quality, String factionId, String type, Vector2f locInHyper) {
        triggerCustomAction(new SEPCreateFleetAction(type, locInHyper, size, quality, factionId));
    }

    public void triggerFleetSetFreighterData(float points, float mult, boolean includeCombatPoints) {
        CreateFleetAction cfa = getPreviousCreateFleetAction();
        cfa.freighterMult = mult;
        if (getPreviousCreateFleetAction() instanceof SEPCreateFleetAction cfa1) {
            cfa1.transportIncludeCombatPts = includeCombatPoints;
            cfa1.freighterPts = points;
        }
    }

    public void triggerFleetSetTankerData(float points, float mult, boolean includeCombatPoints) {
        CreateFleetAction cfa = getPreviousCreateFleetAction();
        cfa.tankerMult = mult;
        if (getPreviousCreateFleetAction() instanceof SEPCreateFleetAction cfa1) {
            cfa1.tankerIncludeCombatPts = includeCombatPoints;
            cfa1.tankerPts = points;
        }
    }

    public void triggerFleetFollowPlayerWithinRange(float maxRange, Object... stages) {
        triggerCustomAction(new OrderFleetFollowNearbyPlayerInStage(this, maxRange, stages));
    }

    public void triggerOrderFleetInterceptOther(SectorEntityToken other) {
        triggerCustomAction(new OrderFleetInterceptOtherAction(other));
    }

    public void connectWithOnDaysElapsed(FleetEscortMission.Stage from, FleetEscortMission.Stage to, float days) {
        this.connections.add(new StageConnection(from, to, new DaysElapsedChecker(days, this)));
    }

    public void connectWithEntityNearbyOther(Object from, Object to, SectorEntityToken entity, SectorEntityToken other, float maxRange) {
        this.connections.add(new StageConnection(from, to, new EntityNearbyOtherChecker(entity, other, maxRange)));
    }

    public void setStageOnEntityNearbyOther(Object to, SectorEntityToken entity, SectorEntityToken other, float maxRange) {
        this.connections.add(new StageConnection(null, to, new EntityNearbyOtherChecker(entity, other, maxRange)));
    }

    public void connectWithFactionTurnedHostile(Object from, Object to, FactionAPI faction) {
        this.connections.add(new StageConnection(from, to, new FactionTurnedHostileChecker(faction)));
    }

    public void setStageOnFactionTurnedHostile(Object to, FactionAPI faction) {
        this.connections.add(new StageConnection(null, to, new FactionTurnedHostileChecker(faction)));
    }

    public void setStageOnFleetWeakened(Object to, CampaignFleetAPI fleet, float damageThreshold) {
        this.connections.add(new StageConnection(null, to, new FleetWeakenedChecker(fleet, damageThreshold)));
    }

    public enum FleetStrengthType {
        STANDARD,
        QUALITY,
        QUANTITY
    }

    public static class PlanetNoSpecialSalvage implements PlanetRequirement {
        @Override
        public boolean planetMatchesRequirement(PlanetAPI planet) {
            return Misc.getSalvageSpecial(planet) == null;
        }
    }

    public static class EntityNoSpecialSalvage implements EntityRequirement {
        @Override
        public boolean entityMatchesRequirement(SectorEntityToken entity) {
            return Misc.getSalvageSpecial(entity) == null;
        }
    }

    public static class EntityBooleanMemoryFlag implements EntityRequirement {
        final String flag;
        final boolean negate;

        public EntityBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean entityMatchesRequirement(SectorEntityToken entity) {
            boolean memVal = entity.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }

    public static class MarketBooleanMemoryFlag implements MarketRequirement {
        final String flag;
        final boolean negate;

        public MarketBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            boolean memVal = market.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }

    public static class MarketUsesSpecialItems implements MarketRequirement {
        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry i : market.getIndustries()) {
                if (i.getSpecialItem() == null) {
                    continue;
                }
                if (!Settings.COLONY_ITEM_WHITELIST.contains(i.getSpecialItem().getId())) {
                    continue;
                }
                return true;
            }
            return false;
        }
    }

    public static class MarketHasCompatibleSpecialItemsWithOther implements MarketRequirement {
        final MarketAPI other;

        public MarketHasCompatibleSpecialItemsWithOther(MarketAPI other) {
            this.other = other;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry ind : market.getIndustries()) {
                SpecialItemData otherData = ind.getSpecialItem();
                if (otherData == null) {
                    continue;
                }
                if (!Settings.COLONY_ITEM_WHITELIST.contains(otherData.getId())) {
                    continue;
                }
                for (Industry otherInd : this.other.getIndustries()) {
                    if (!otherInd.wantsToUseSpecialItem(otherData)) {
                        continue;
                    }
                    if (Utils.canSpecialItemBeInstalled(otherData.getId(), otherInd)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public record MarketCanUseSpecialItemReq(SpecialItemData specialItemData) implements MarketRequirement {

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry industry : market.getIndustries()) {
                if (!industry.wantsToUseSpecialItem(this.specialItemData)) {
                    continue;
                }
                if (Utils.canSpecialItemBeInstalled(this.specialItemData.getId(), industry)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class MarketFactionChangedChecker implements ConditionChecker {
        public final String prevFactionId;
        public final MarketAPI market;

        public MarketFactionChangedChecker(MarketAPI market) {
            this.prevFactionId = market.getFactionId();
            this.market = market;
        }

        @Override
        public boolean conditionsMet() {
            return !Objects.equals(this.market.getFactionId(), this.prevFactionId);
        }
    }

    public record FactionTurnedHostileChecker(FactionAPI faction) implements ConditionChecker {

        @Override
        public boolean conditionsMet() {
            return this.faction.getRelToPlayer().isHostile();
        }
    }

    public static class PlanetBooleanMemoryFlag implements PlanetRequirement {
        final String flag;
        final boolean negate;

        public PlanetBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean planetMatchesRequirement(PlanetAPI planet) {
            boolean memVal = planet.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }

    public static class OrderFleetInterceptOtherAction implements MissionTrigger.TriggerAction {
        protected final SectorEntityToken other;

        public OrderFleetInterceptOtherAction(SectorEntityToken other) {
            this.other = other;
        }

        public void doAction(MissionTrigger.TriggerActionContext context) {
            makeFleetInterceptOther(context.fleet, this.other, 1000f);
            if (!context.fleet.hasScriptOfClass(MissionFleetAutoDespawn.class)) {
                context.fleet.addScript(new MissionFleetAutoDespawn(context.mission, context.fleet));
            }
        }
    }

    public static class OrderFleetFollowNearbyPlayerInStage implements MissionTrigger.TriggerAction {
        protected final List<Object> stages;
        protected final BaseHubMission mission;
        protected final float maxRange;

        public OrderFleetFollowNearbyPlayerInStage(BaseHubMission mission, float maxRange, Object... stages) {
            this.mission = mission;
            this.maxRange = maxRange;
            this.stages = Arrays.asList(stages);
        }

        public void doAction(MissionTrigger.TriggerActionContext context) {
            context.fleet.addScript(new MissionFleetFollowPlayerIfNearby(context.fleet, this.mission, this.maxRange, this.stages));
        }
    }

    public static class EntityNearbyOtherChecker implements ConditionChecker {
        protected final SectorEntityToken entity;
        protected final SectorEntityToken other;
        protected final float range;

        public EntityNearbyOtherChecker(SectorEntityToken entity, SectorEntityToken other, float range) {
            this.entity = entity;
            this.other = other;
            this.range = range;
        }

        @Override
        public boolean conditionsMet() {
            return this.entity.getContainingLocation() == this.other.getContainingLocation() &&
                    Misc.getDistance(this.entity, this.other) < this.range;
        }
    }

    public static class FleetWeakenedChecker implements ConditionChecker {
        public final CampaignFleetAPI fleet;
        public final float fleetPoints;
        public final float damageThreshold;

        /**
         * @param damageThreshold from 0.1f to 0.8f only
         *
         */
        public FleetWeakenedChecker(CampaignFleetAPI fleet, float damageThreshold) {
            this.fleet = fleet;
            this.fleetPoints = fleet.getFleetPoints();
            this.damageThreshold = Math.max(0.1f, Math.min(damageThreshold, 0.8f));
        }

        public boolean conditionsMet() {
            return this.fleetPoints * this.damageThreshold > this.fleet.getFleetPoints();
        }
    }

    public static class MarketHasCommodityDemands implements MarketRequirement {
        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            return market != null && market.getDemandData() != null && !market.getDemandData().getDemandList().isEmpty();
        }
    }

    public static class SEPCreateFleetAction extends CreateFleetAction {
        public final boolean freighterIncludeCombatPts = false;
        public final boolean linerIncludeCombatPts = false;
        public final boolean utilityIncludeCombatPts = false;
        public Float freighterPts = null;
        public Float tankerPts = null;
        public boolean tankerIncludeCombatPts = false;
        public Float linerPts = null;
        public Float transportPts = null;
        public boolean transportIncludeCombatPts = false;
        public Float utilityPts = null;

        public SEPCreateFleetAction(String type, Vector2f locInHyper, FleetSize fSize, FleetQuality fQuality, String factionId) {
            super(type, locInHyper, fSize, fQuality, factionId);
        }

        @Override
        public void doAction(MissionTrigger.TriggerActionContext context) {
            Random random;
            if (context.mission != null) {
                random = ((BaseHubMission) context.mission).getGenRandom();
            } else {
                random = Misc.random;
            }
            FactionAPI faction = Global.getSector().getFaction(this.params.factionId);
            float maxPoints = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
            float min = this.fSize.maxFPFraction - (this.fSize.maxFPFraction - this.fSize.prev().maxFPFraction) / 2f;
            float max = this.fSize.maxFPFraction + (this.fSize.next().maxFPFraction - this.fSize.maxFPFraction) / 2f;
            float fraction = min + (max - min) * random.nextFloat();
            float excess = 0;

            if (this.fSizeOverride != null) {
                fraction = this.fSizeOverride * (0.95f + random.nextFloat() * 0.1f);
            } else {
                int numShipsDoctrine = 1;
                if (this.params.doctrineOverride != null) {
                    numShipsDoctrine = this.params.doctrineOverride.getNumShips();
                } else {
                    numShipsDoctrine = faction.getDoctrine().getNumShips();
                }
                float doctrineMult = FleetFactoryV3.getDoctrineNumShipsMult(numShipsDoctrine);
                fraction *= 0.75f * doctrineMult;
                if (fraction > FleetSize.MAXIMUM.maxFPFraction) {
                    excess = fraction - FleetSize.MAXIMUM.maxFPFraction;
                    fraction = FleetSize.MAXIMUM.maxFPFraction;
                }
            }

            float combatPoints = fraction * maxPoints;
            if (this.combatFleetPointsOverride != null) {
                combatPoints = this.combatFleetPointsOverride;
            }

            FactionDoctrineAPI doctrine = this.params.doctrineOverride;
            if (excess > 0) {
                if (doctrine == null) {
                    doctrine = faction.getDoctrine().clone();
                }
                int added = Math.round(excess / 0.1f);
                if (added > 0) {
                    doctrine.setOfficerQuality(Math.min(5, doctrine.getOfficerQuality() + added));
                    doctrine.setShipQuality(doctrine.getShipQuality() + added);
                }
            }

            if (this.freighterPts == null) {
                this.freighterPts = 0f;
            }
            if (this.tankerPts == null) {
                this.tankerPts = 0f;
            }
            if (this.transportPts == null) {
                this.transportPts = 0f;
            }
            if (this.linerPts == null) {
                this.linerPts = 0f;
            }
            if (this.utilityPts == null) {
                this.utilityPts = 0f;
            }

            if (this.freighterIncludeCombatPts) {
                this.freighterPts += combatPoints;
            }
            if (this.tankerIncludeCombatPts) {
                this.tankerPts += combatPoints;
            }
            if (this.transportIncludeCombatPts) {
                this.transportPts += combatPoints;
            }
            if (this.linerIncludeCombatPts) {
                this.linerPts += combatPoints;
            }
            if (this.utilityIncludeCombatPts) {
                this.utilityPts += combatPoints;
            }

            if (this.freighterMult == null) {
                this.freighterMult = 0f;
            }
            if (this.tankerMult == null) {
                this.tankerMult = 0f;
            }
            if (this.linerMult == null) {
                this.linerMult = 0f;
            }
            if (this.transportMult == null) {
                this.transportMult = 0f;
            }
            if (this.utilityMult == null) {
                this.utilityMult = 0f;
            }
            if (this.qualityMod == null) {
                this.qualityMod = 0f;
            }

            this.params.combatPts = combatPoints;
            this.params.freighterPts = this.freighterPts * this.freighterMult;
            this.params.tankerPts = this.tankerPts * this.tankerMult;
            this.params.transportPts = this.transportPts * this.transportMult;
            this.params.linerPts = this.linerPts * this.linerMult;
            this.params.utilityPts = this.utilityPts * this.utilityMult;
            this.params.qualityMod = this.qualityMod;
            this.params.doctrineOverride = doctrine;
            this.params.random = random;


            if (this.fQuality != null) {
                switch (this.fQuality) {
                    case VERY_LOW:
                        if (this.fQualityMod != null) {
                            this.params.qualityMod += this.fQuality.qualityMod;
                        } else {
                            this.params.qualityOverride = 0f;
                        }
                        break;
                    case LOWER, HIGHER, DEFAULT, VERY_HIGH:
                        this.params.qualityMod += this.fQuality.qualityMod;
                        break;
                    case SMOD_1, SMOD_2, SMOD_3:
                        this.params.qualityMod += this.fQuality.qualityMod;
                        this.params.averageSMods = this.fQuality.numSMods;
                        break;
                }
            }
            if (this.fQualityMod != null) {
                this.params.qualityMod += this.fQualityMod;
            }
            if (this.fQualitySMods != null) {
                this.params.averageSMods = this.fQualitySMods;
            }

            if (this.oNum != null) {
                switch (this.oNum) {
                    case NONE:
                        this.params.withOfficers = false;
                        break;
                    case FC_ONLY:
                        this.params.officerNumberMult = 0f;
                        break;
                    case FEWER:
                        this.params.officerNumberMult = 0.5f;
                        break;
                    case DEFAULT:
                        break;
                    case MORE:
                        this.params.officerNumberMult = 1.5f;
                        break;
                    case ALL_SHIPS:
                        this.params.officerNumberBonus = Global.getSettings().getInt("maxShipsInAIFleet");
                        break;
                }
            }

            if (this.oQuality != null) {
                switch (this.oQuality) {
                    case LOWER:
                        this.params.officerLevelBonus = -3;
                        this.params.officerLevelLimit = Global.getSettings().getInt("officerMaxLevel") - 1;
                        this.params.commanderLevelLimit = Global.getSettings().getInt("maxAIFleetCommanderLevel") - 2;
                        if (this.params.commanderLevelLimit < this.params.officerLevelLimit) {
                            this.params.commanderLevelLimit = this.params.officerLevelLimit;
                        }
                        break;
                    case DEFAULT:
                        break;
                    case HIGHER:
                        this.params.officerLevelBonus = 2;
                        this.params.officerLevelLimit = Global.getSettings().getInt("officerMaxLevel") + (int) OfficerTraining.MAX_LEVEL_BONUS;
                        break;
                    case UNUSUALLY_HIGH:
                        this.params.officerLevelBonus = 4;
                        this.params.officerLevelLimit = SalvageSpecialAssigner.EXCEPTIONAL_PODS_OFFICER_LEVEL;
                        break;
                    case AI_GAMMA:
                    case AI_BETA:
                    case AI_BETA_OR_GAMMA:
                    case AI_ALPHA:
                    case AI_MIXED:
                    case AI_OMEGA:
                        this.params.aiCores = this.oQuality;
                        break;
                }
                if (this.doNotIntegrateAICores != null) {
                    this.params.doNotIntegrateAICores = this.doNotIntegrateAICores;
                }
            }

            if (this.shipPickMode != null) {
                this.params.modeOverride = this.shipPickMode;
            }

            this.params.updateQualityAndProducerFromSourceMarket();
            if (this.qualityOverride != null) {
                this.params.qualityOverride = this.qualityOverride + this.params.qualityMod;
            }
            context.fleet = FleetFactoryV3.createFleet(this.params);
            context.fleet.setFacing(random.nextFloat() * 360f);

            if (this.faction != null) {
                context.fleet.setFaction(this.faction, true);
            }

            if (this.nameOverride != null) {
                context.fleet.setName(this.nameOverride);
            }
            if (this.noFactionInName != null && this.noFactionInName) {
                context.fleet.setNoFactionInName(true);
            }

            if (this.removeInflater != null && this.removeInflater) {
                context.fleet.setInflater(null);
            } else {
                if (context.fleet.getInflater() instanceof DefaultFleetInflater inflater) {
                    if (inflater.getParams() instanceof DefaultFleetInflaterParams p) {
                        if (this.allWeapons != null) {
                            p.allWeapons = this.allWeapons;
                        }
                        if (this.shipPickMode != null) {
                            p.mode = this.shipPickMode;
                        }
                    }
                }
            }

            context.fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true);
            context.allFleets.add(context.fleet);

            if (!context.fleet.hasScriptOfClass(MissionFleetAutoDespawn.class)) {
                context.fleet.addScript(new MissionFleetAutoDespawn(context.mission, context.fleet));
            }

            if (this.damage != null) {
                FleetFactoryV3.applyDamageToFleet(context.fleet, this.damage, false, random);
            }
        }
    }
}

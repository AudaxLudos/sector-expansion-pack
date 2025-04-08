package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExpeditionFGI extends FleetGroupIntel {
    public static Logger log = Global.getLogger(ExpeditionFGI.class);

    public static String PREPARE_ACTION = "prepare_action";
    public static String TRAVEL_ACTION = "travel_action";
    public static String PAYLOAD_ACTION = "payload_action";
    public static String RETURN_ACTION = "return_action";

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

        float maxTotalDifficulty = 10f;
        this.params.fleetSizes.add(Math.round(maxTotalDifficulty));

        setRandom(this.params.random);
        setPostingLocation(getSource());
        initAction();
        Global.getSector().getIntelManager().queueIntel(this);
        log.info(String.format("Starting expedition by %s in the %s with a total difficulty value of %s",
                Misc.ucFirst(getFaction().getDisplayName()), this.params.source.getStarSystem().getNameWithLowercaseTypeShort(),
                maxTotalDifficulty));
    }

    protected void pickSource() {
        HubMissionWithSearch picker = new HubMissionWithSearch() {
            @Override
            protected boolean create(MarketAPI createdAt, boolean barEvent) {
                return false;
            }
        };
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
        HubMissionWithSearch picker = new HubMissionWithSearch() {
            @Override
            protected boolean create(MarketAPI createdAt, boolean barEvent) {
                return false;
            }
        };
        picker.requireSystemInterestingAndNotCore();
        picker.preferSystemInInnerSector();
        picker.preferSystemUnexplored();

        StarSystemAPI picked = picker.pickSystem();
        if (picked == null) {
            endImmediately();
            return;
        }

        this.params.target = picked;
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

        return m.createFleet();
    }

    public void setFleetCreatorQualityFromRoute(FleetCreatorMission m) {
        if (m == null || this.route == null || this.route.getExtra() == null || this.route.getExtra().quality == null) {
            return;
        }
        m.getPreviousCreateFleetAction().qualityOverride = this.route.getExtra().quality;
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
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {

    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {

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

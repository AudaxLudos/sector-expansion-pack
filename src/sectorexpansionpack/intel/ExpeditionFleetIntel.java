package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.awt.*;

public class ExpeditionFleetIntel extends FleetGroupIntel {
    public static Logger log = Global.getLogger(ExpeditionFleetIntel.class);
    public static String PREPARE_ACTION = "prepare_action";
    public static String GOTO_ACTION = "travel_action";
    public static String LOOT_ACTION = "loot_action";
    public static String RETURN_ACTION = "return_action";
    public static String DOCK_ACTION = "dock_action";

    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketAPI source;
    protected SectorEntityToken target;

    public ExpeditionFleetIntel(SpecialItemSpecAPI specialItemSpec, MarketAPI source, SectorEntityToken target) {
        this.specialItemSpec = specialItemSpec;
        this.specialItemData = new SpecialItemData(specialItemSpec.getId(), null);
        this.source = source;
        this.target = target;

        setFaction(source.getFactionId());
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "preparing for expedition"), PREPARE_ACTION);
        addAction(new FGTravelAction(this.source.getPrimaryEntity(), this.target), GOTO_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 30f, "exploring and looting " + source.getName()), LOOT_ACTION);
        addAction(new FGTravelAction(this.target, this.source.getPrimaryEntity()), RETURN_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "Docking to" + source.getName()), DOCK_ACTION);

        createRoute(source.getFactionId(), 10, 1, null);
        log.info("Created an artifact expedition fleet at " + this.source.getName() + " in " + this.source.getStarSystem().getNameWithLowercaseType());

        // TODO: Add special item on target entity
        // TODO: Send expedition departure intel
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        super.notifyActionFinished(action);
        // TODO: Remove special item on target entity when fleet finishes looting
        // TODO: Install special item on source market when fleet returns to source market
        // TODO: Roll chance to leak the expedition's status
    }

    @Override
    protected boolean isPlayerTargeted() {
        return true;
    }

    @Override
    protected void spawnFleets() {
        FleetCreatorMission fcm = new FleetCreatorMission(getRandom());

        fcm.beginFleet();
        fcm.createFleet(FleetCreatorMission.FleetStyle.QUALITY, 10, this.source.getFactionId(), this.source.getLocationInHyperspace());
        fcm.setFleetSource(this.source);
        fcm.triggerMakeLowRepImpact();
        fcm.triggerFleetSetName("Artifact Expedition");

        CampaignFleetAPI fleet = fcm.createFleet();
        if (fleet != null && this.route != null) {
            setLocationAndCoordinates(fleet, this.route.getCurrent());
            this.fleets.add(fleet);
        }
    }

    @Override
    protected SectorEntityToken getSource() {
        return this.source.getPrimaryEntity();
    }

    @Override
    protected SectorEntityToken getDestination() {
        return this.target.getStarSystem().getHyperspaceAnchor();
    }

    @Override
    protected String getBaseName() {
        return "Artifact Expedition";
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
    }
}

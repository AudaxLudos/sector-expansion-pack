package sectorexpansionpack.ghosts.types;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.ghosts.GBCollideRunScript;

import java.awt.*;
import java.util.List;

public class FleetEaterGhost extends BaseSensorGhost implements Script {
    protected CampaignFleetAPI targetFleet;

    public FleetEaterGhost(SensorGhostManager manager) {
        super(manager, 20);

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        List<CampaignFleetAPI> targetFleets = Misc.getNearbyFleets(playerFleet, 2000f);

        if (targetFleets.isEmpty()) {
            setCreationFailed();
            return;
        }
        this.targetFleet = targetFleets.get(0);
        if (Misc.isImportantForReason(this.targetFleet.getMemoryWithoutUpdate(), null)) {
            setCreationFailed();
            return;
        }

        initEntity(genHugeSensorProfile(), this.targetFleet.getRadius() + genLargeRadius());
        this.entity.addTag(Tags.UNAFFECTED_BY_SLIPSTREAM);
        setDespawnRange(-8000f);

        placeNearEntity(this.targetFleet, 1800f, 2200f);

        addBehavior(new GBFollow(this.targetFleet, 1.8f, 10, 1000f, 1000f));
        addBehavior(new GBFollow(this.targetFleet, 0.2f, 20, 800f, 800f));
        addBehavior(new GBCollideRunScript(this.targetFleet, 40, this));
        addBehavior(new GBGoInDirection(0.1f, getRandom().nextFloat() * 360f, 40));
        addInterrupt(new GBIDespawn(0.1f));
    }

    @Override
    public void run() {
        if (this.targetFleet != null) {
            this.targetFleet.setSensorProfile(100000f);
            CampaignPingSpec custom = new CampaignPingSpec();
            custom.setWidth(15);
            custom.setRange(750f * 1.3f);
            custom.setDuration(0.5f);
            custom.setAlphaMult(1f);
            custom.setInFraction(0.1f);
            custom.setNum(1);
            custom.setColor(new Color(255, 100, 100, 255));
            Global.getSector().addPing(this.entity, custom);
            for (FleetMemberAPI member : this.targetFleet.getFleetData().getMembersListCopy()) {
                this.targetFleet.removeFleetMemberWithDestructionFlash(member);
            }
        }
    }
}

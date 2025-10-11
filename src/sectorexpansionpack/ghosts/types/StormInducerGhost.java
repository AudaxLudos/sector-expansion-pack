package sectorexpansionpack.ghosts.types;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class StormInducerGhost extends BaseSensorGhost {
    protected IntervalUtil timer = new IntervalUtil(0.2f, 0.3f);
    protected float radius;

    public StormInducerGhost(SensorGhostManager manager, float radius) {
        super(manager, 20);
        this.radius = radius;

        initEntity(genMediumSensorProfile(), genMediumRadius());
        this.entity.addTag(Tags.UNAFFECTED_BY_SLIPSTREAM);
        setDespawnRange(-2000f);

        if (!placeNearPlayer()) {
            setCreationFailed();
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        addBehavior(new GBDartAround(playerFleet, genDelay(15f), 50, 0f, 100f));
        addBehavior(new GBGoAwayFrom(genFloat(1f, 3f), playerFleet, this.fleeBurnLevel));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (this.entity == null) {
            return;
        }

        this.timer.advance(amount);
        if (this.timer.intervalElapsed()) {
            HyperspaceTerrainPlugin terrainPlugin = Misc.getHyperspaceTerrainPlugin();
            if (terrainPlugin != null && this.entity.isInHyperspace()) {
                terrainPlugin.setTileState(
                        this.entity.getLocation(), this.radius,
                        HyperspaceTerrainPlugin.CellState.SIGNAL,
                        0f, 0f);
            }
        }
    }
}

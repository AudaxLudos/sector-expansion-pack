package sectorexpansionpack.ghosts.types;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class StormPacifierGhost extends BaseSensorGhost {
    protected IntervalUtil timer = new IntervalUtil(0.2f, 0.3f);
    protected float radius;

    public StormPacifierGhost(SensorGhostManager manager, float radius) {
        super(manager, 20);
        this.radius = radius;

        initEntity(genMediumSensorProfile(), genMediumRadius());
        this.entity.addTag(Tags.UNAFFECTED_BY_SLIPSTREAM);
        setDespawnRange(-1000f);

        if (!placeNearPlayer()) {
            setCreationFailed();
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        addBehavior(new GBCircle(playerFleet, genDelay(15f), 25, 50f, getRandom().nextBoolean() ? 1f : -1f));
        addBehavior(new GBGoAwayFrom(genFloat(1f, 3f), playerFleet, this.fleeBurnLevel));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        this.timer.advance(amount);
        if (this.timer.intervalElapsed()) {
            HyperspaceTerrainPlugin terrainPlugin = Misc.getHyperspaceTerrainPlugin();
            if (terrainPlugin != null && this.entity.isInHyperspace()) {
                terrainPlugin.setTileState(
                        this.entity.getLocation(), this.radius,
                        HyperspaceTerrainPlugin.CellState.OFF,
                        1f, 0f);
            }
        }
    }
}

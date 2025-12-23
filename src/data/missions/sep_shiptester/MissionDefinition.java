package data.missions.sep_shiptester;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

import java.util.List;
import java.util.Objects;

public class MissionDefinition implements MissionDefinitionPlugin {
    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // Initialize fleets so we can add ships and fighter wings to them
        api.initFleet(FleetSide.PLAYER, "MMAS", FleetGoal.ATTACK, false, 5);
        api.initFleet(FleetSide.ENEMY, "MMBS", FleetGoal.ATTACK, true);

        // Set a small blurb for each fleet
        // Shows up on the mission detail
        // Shows up on  mission results screen
        api.setFleetTagline(FleetSide.PLAYER, "Muskavar Mercenary Group Alpha");
        api.setFleetTagline(FleetSide.ENEMY, "Muskavar Mercenary Group Beta");

        // Shows up as items on the mission detail screen
        api.addBriefingItem("Defeat all enemy forces");

        List<ShipHullSpecAPI> shipHullSpecs = Global.getSettings().getAllShipHullSpecs();
        for (ShipHullSpecAPI shipHullSpec : shipHullSpecs) {
            if (shipHullSpec.isDHull()) {
                continue;
            }
            ModSpecAPI modSpec = shipHullSpec.getSourceMod();
            if (modSpec == null || !Objects.equals(modSpec.getId(), "sectorexpansionpack")) {
                continue;
            }

            // Set up the player's fleet
            api.addToFleet(FleetSide.PLAYER, shipHullSpec.getHullId() + "_Hull", FleetMemberType.SHIP, "MMAS " + shipHullSpec.getHullName(), false);
            // Set up the enemy fleet.
            api.addToFleet(FleetSide.ENEMY, shipHullSpec.getHullId() + "_Hull", FleetMemberType.SHIP, "MMBS " + shipHullSpec.getHullName(), false);
        }

        // Set up the map.
        float width = 18000f;
        float height = 18000f;
        api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

        float minX = -width / 2;
        float minY = -height / 2;

        api.addNebula(minX + width * 0.5f - 300, minY + height * 0.5f, 1000);
        api.addNebula(minX + width * 0.5f + 300, minY + height * 0.5f, 1000);

        for (int i = 0; i < 5; i++) {
            float x = (float) Math.random() * width - width / 2;
            float y = (float) Math.random() * height - height / 2;
            float radius = 100f + (float) Math.random() * 400f;
            api.addNebula(x, y, radius);
        }

        // Add an asteroid field
        api.addAsteroidField(minX + width / 2f, minY + height / 2f, 0, 8000f, 20f, 70f, 100);

        api.getContext().setStandoffRange(6000f);
    }
}

package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.intel.ExpeditionFleetManager;

import java.io.IOException;
import java.util.Random;

public class ModPlugin extends BaseModPlugin {
    public static JSONObject MISSION_SCENARIOS;

    public static JSONObject getRandomMissionScenario(String missionId, Random random, boolean barEventsOnly) throws JSONException {
        WeightedRandomPicker<JSONObject> scenarioPicker = new WeightedRandomPicker<>(random);
        JSONObject mission = MISSION_SCENARIOS.getJSONObject(missionId);
        JSONArray scenarios = mission.getJSONArray("scenarios");
        for (int i = 0; i < scenarios.length(); i++) {
            JSONObject scenario = scenarios.getJSONObject(i);
            boolean hasBarEvent;
            boolean hasContactEvent;
            if (scenario.has("hasBarEvent")) {
                hasBarEvent = scenario.getBoolean("hasBarEvent");
            } else {
                hasBarEvent = getMissionScenarioDefaults(missionId).getBoolean("hasBarEvent");
            }
            if (scenario.has("hasContactEvent")) {
                hasContactEvent = scenario.getBoolean("hasContactEvent");
            } else {
                hasContactEvent = getMissionScenarioDefaults(missionId).getBoolean("hasContactEvent");
            }
            if (barEventsOnly) {
                if (!hasBarEvent) {
                    continue;
                }
            } else {
                if (!hasContactEvent) {
                    continue;
                }
            }

            float weight;
            if (scenario.has("weight")) {
                weight = (float) scenario.getDouble("weight");
            } else {
                weight = (float) getMissionScenarioDefaults(missionId).getDouble("weight");
            }
            scenarioPicker.add(scenario, weight);
        }
        return scenarioPicker.pick();
    }

    public static JSONObject getMissionScenarioDefaults(String missionId) {
        JSONObject mission;
        try {
            mission = MISSION_SCENARIOS.getJSONObject(missionId).getJSONObject("defaults");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return mission;
    }

    @Override
    public void onApplicationLoad() {
        loadMissionScenarios();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        loadMissionScenarios();

        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ExpeditionFleetManager.class)) {
            sector.addScript(new ExpeditionFleetManager());
        }
    }

    public void loadMissionScenarios() {
        try {
            MISSION_SCENARIOS = Global.getSettings().loadJSON("data/campaign/sep_mission_scenarios.json");
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

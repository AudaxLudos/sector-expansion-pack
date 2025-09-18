package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Random;

public class ModPlugin extends BaseModPlugin {
    public static JSONObject MISSION_SCENARIOS;

    public static JSONObject getRandomMissionScenario(String missionId, Random random) throws JSONException {
        WeightedRandomPicker<JSONObject> scenarioPicker = new WeightedRandomPicker<>();
        JSONObject mission = MISSION_SCENARIOS.getJSONObject(missionId);
        JSONArray scenarios = mission.getJSONArray("scenarios");
        for (int i = 0; i < scenarios.length(); i++) {
            JSONObject scenario = scenarios.getJSONObject(i);
            float weight = 10f;
            if (scenario.has("weight")) {
                weight = (float) scenario.getDouble("weight");
            }
            scenarioPicker.add(scenario, weight);
        }
        return scenarioPicker.pick(random);
    }

    public static JSONObject getMissionScenarioDefaults(String missionId) throws JSONException {
        JSONObject mission = MISSION_SCENARIOS.getJSONObject(missionId);
        return mission.getJSONObject("defaults");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            MISSION_SCENARIOS = Global.getSettings().loadJSON("data/campaign/sep_mission_scenarios.json");
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

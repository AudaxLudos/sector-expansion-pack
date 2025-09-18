package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class ModPlugin extends BaseModPlugin {
    public static WeightedRandomPicker<JSONObject> SEP_SAR_SCENARIOS = new WeightedRandomPicker<>();

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            JSONObject data = Global.getSettings().loadJSON("data/campaign/sep_mission_scenarios.json");
            JSONArray scenarios = data.getJSONArray("sep_sar");
            for (int i = 0; i < scenarios.length(); i++) {
                JSONObject scenario = scenarios.getJSONObject(i);
                float weight = 10f;
                if (scenario.has("weight")) {
                    weight = (float) scenario.getDouble("weight");
                }
                SEP_SAR_SCENARIOS.add(scenario, weight);
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

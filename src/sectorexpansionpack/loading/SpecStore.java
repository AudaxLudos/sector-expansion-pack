package sectorexpansionpack.loading;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.loading.specs.MissionScenarioSpec;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class SpecStore {
    public static void LoadMissionScenarioSpecs() {
        try {
            JSONArray rows = Global.getSettings().loadCSV("data/campaign/sep_mission_scenarios.csv");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row.getString("missionId").isEmpty() || row.getString("scenarioId").isEmpty()) {
                    continue;
                }
                MissionScenarioSpec spec = new MissionScenarioSpec();
                if (row.getString("scenarioId").contains("#")) {
                    continue;
                }
                spec.setScenarioId(row.getString("scenarioId"));
                spec.setMissionId(row.getString("missionId"));
                spec.setFrequency((float) row.optDouble("frequency", 10f));
                spec.setDuration((float) row.optDouble("duration", -1f));
                spec.setCreditReward(row.optString("creditReward", null));
                spec.setType(row.optString("type", null));
                String rawComplications = row.getString("complications");
                if (rawComplications != null && !rawComplications.isBlank()) {
                    List<String> c = Arrays.asList(rawComplications.replaceAll("\\s+", "").split("\\|"));
                    HashSet<String> complications = new HashSet<>(c);
                    spec.setComplications(complications);
                }
                String rawTags = row.getString("tags");
                if (rawTags != null && !rawTags.isBlank()) {
                    List<String> t = Arrays.asList(rawTags.replaceAll("\\s+", "").split(","));
                    HashSet<String> tags = new HashSet<>(t);
                    spec.setTags(tags);
                }
                spec.setData1(row.optString("data1", null));
                spec.setData2(row.optString("data2", null));
                spec.setData3(row.optString("data3", null));
                Global.getSettings().putSpec(MissionScenarioSpec.class, spec.getScenarioId(), spec);
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }
}

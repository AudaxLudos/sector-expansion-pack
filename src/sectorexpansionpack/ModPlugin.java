package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.ghosts.types.FleetEaterGhostCreator;
import sectorexpansionpack.ghosts.types.StormInducerGhostCreator;
import sectorexpansionpack.ghosts.types.StormPacifierGhostCreator;
import sectorexpansionpack.intel.ExpeditionFleetManager;
import sectorexpansionpack.intel.IncursionFleetManager;

import java.io.IOException;
import java.util.*;

public class ModPlugin extends BaseModPlugin {
    public static List<String> COLONY_ITEM_WHITELIST = new ArrayList<>();
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
        loadMissionScenariosV2();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Utils.setRandom(new Random(Long.parseLong(Global.getSector().getSeedString().replaceAll("\\D", ""))));

        loadColonyItemWhitelist();
        loadMissionScenarios();
        loadNeededScripts();

        StormPacifierGhostCreator.register();
        StormInducerGhostCreator.register();
        FleetEaterGhostCreator.register();
    }

    public void loadColonyItemWhitelist() {
        List<String> whitelist = new ArrayList<>();

        try {
            JSONArray spreadsheet = Global.getSettings().getMergedSpreadsheetDataForMod("id", "data/config/sep_colony_item_whitelist.csv", "sectorexpansionpack");

            for (int i = 0; i < spreadsheet.length(); i++) {
                JSONObject row = spreadsheet.getJSONObject(i);
                String specialItemId = row.getString("id");

                whitelist.add(specialItemId);
            }
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

        COLONY_ITEM_WHITELIST = whitelist;
    }

    public void loadMissionScenarios() {
        try {
            MISSION_SCENARIOS = Global.getSettings().loadJSON("data/campaign/sep_mission_scenarios.json");
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadMissionScenariosV2() {
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
                Global.getSettings().putSpec(MissionScenarioSpec.class, spec.scenarioId, spec);
            }
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadNeededScripts() {
        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ExpeditionFleetManager.class)) {
            sector.addScript(new ExpeditionFleetManager());
        }
        if (!sector.hasScript(IncursionFleetManager.class)) {
            sector.addScript(new IncursionFleetManager());
        }
    }
}

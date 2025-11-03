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
        loadMissionScenariosV2();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Utils.setRandom(new Random(Long.parseLong(Global.getSector().getSeedString().replaceAll("\\D", ""))));

        loadMissionScenarios();
        loadNeededScripts();

        StormPacifierGhostCreator.register();
        StormInducerGhostCreator.register();
        FleetEaterGhostCreator.register();
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
                spec.setMissionId(row.getString("missionId"));
                spec.setScenarioId(row.getString("scenarioId"));
                spec.setFrequency((float) row.optDouble("frequency", 10f));
                spec.setMinCreditReward(row.optInt("minCreditReward", -1));
                spec.setMaxCreditReward(row.optInt("minCreditReward", -1));
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

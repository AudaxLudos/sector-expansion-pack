package sectorexpansionpack;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhostManager;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.ghosts.types.FleetEaterGhostCreator;
import sectorexpansionpack.ghosts.types.StormInducerGhostCreator;
import sectorexpansionpack.ghosts.types.StormPacifierGhostCreator;
import sectorexpansionpack.intel.*;
import sectorexpansionpack.listeners.MatrixCatalystBlueprintAdder;
import sectorexpansionpack.listeners.MatrixCatalystOptionProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class ModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        addMissionScenarios();
    }

    public void addMissionScenarios() {
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

    @Override
    public void onGameLoad(boolean newGame) {
        addScriptsIfNeeded();
    }

    public void addScriptsIfNeeded() {
        // Mod settings
        Utils.setRandom(new Random(Long.parseLong(Global.getSector().getSeedString().replaceAll("\\D", ""))));
        Settings.load();

        // Scripts
        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ExpeditionFleetManager.class)) {
            sector.addScript(new ExpeditionFleetManager());
        }
        if (!sector.hasScript(IncursionFleetManager.class)) {
            sector.addScript(new IncursionFleetManager());
        }

        GenericMissionManager genericMissionManager = GenericMissionManager.getInstance();
        if (!genericMissionManager.hasMissionCreator(ClearDebrisFieldsIntelCreator.class)) {
            genericMissionManager.addMissionCreator(new ClearDebrisFieldsIntelCreator());
        }
        if (!genericMissionManager.hasMissionCreator(ConstructObjectiveIntelCreator.class)) {
            genericMissionManager.addMissionCreator(new ConstructObjectiveIntelCreator());
        }

        // Campaign input listeners
        ListenerManagerAPI listenerManager = Global.getSector().getListenerManager();
        if (!listenerManager.hasListenerOfClass(MatrixCatalystOptionProvider.class)) {
            listenerManager.addListener(new MatrixCatalystOptionProvider(), true);
        }

        // Campaign event listeners
        if (!Global.getSector().getPlayerFaction().knowsIndustry("sep_matrix_catalyst")) {
            Global.getSector().addTransientListener(new MatrixCatalystBlueprintAdder());
        }

        SensorGhostManager.CREATORS.add(new StormPacifierGhostCreator());
        SensorGhostManager.CREATORS.add(new StormInducerGhostCreator());
        SensorGhostManager.CREATORS.add(new FleetEaterGhostCreator());
    }
}

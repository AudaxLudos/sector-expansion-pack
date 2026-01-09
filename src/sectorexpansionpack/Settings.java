package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Settings implements LunaSettingsListener {
    public static String MOD_ID = "sectorexpansionpack";
    public static boolean EXPEDITIONS_ENABLED;
    public static boolean INCURSIONS_ENABLED;
    public static List<String> COLONY_ITEM_WHITELIST = new ArrayList<>();

    public static void load() {
        loadColonyItemWhitelist();
        loadSettings();
    }

    public static void loadColonyItemWhitelist() {
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

    public static void loadSettings() {
        if (isLunaLibEnabled()) {
            LunaSettings.addSettingsListener(new Settings());
        }
        setSettings();
    }

    public static void setSettings() {
        EXPEDITIONS_ENABLED = getBoolean(MOD_ID, "sep_expeditions_enabled");
        INCURSIONS_ENABLED = getBoolean(MOD_ID, "sep_incursions_enabled");
    }

    public static boolean getBoolean(String modId, String settingId) {
        if (isLunaLibEnabled()) {
            return Boolean.TRUE.equals(LunaSettings.getBoolean(modId, settingId));
        }
        return Global.getSettings().getBoolean(modId);
    }

    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled("lunalib");
    }

    @Override
    public void settingsChanged(String s) {
        setSettings();
    }
}

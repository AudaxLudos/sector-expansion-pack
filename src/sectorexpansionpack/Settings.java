package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

public class Settings implements LunaSettingsListener {
    public static String MOD_ID = "sectorexpansionpack";
    public static boolean EXPEDITIONS_ENABLED;
    public static boolean INCURSIONS_ENABLED;

    public static void load() {
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

    @Override
    public void settingsChanged(String s) {
        setSettings();
    }

    public static boolean isLunaLibEnabled() {
        return Global.getSettings().getModManager().isModEnabled("lunalib");
    }
}

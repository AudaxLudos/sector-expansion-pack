package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class CompactProfile extends SCBaseSkillPlugin {
    public static float DETECTED_RANGE_MULT = 0.25f;
    public static float SENSOR_PROFILE_MULT = 0.25f;
    public static float MOVE_SLOW_SPEED_MOD = 3f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
        String primaryType = AptitudePurist.getPrimaryShipDesignType(data);
        float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

        tooltip.addPara("The most common design type is %s", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(primaryType), primaryType);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Bonuses are reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(otherDesignTypeCount * 10f) + "%", otherDesignTypeCount + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) detected-at range", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(DETECTED_RANGE_MULT * debuffMult * 100f) + "%", Math.round(DETECTED_RANGE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor profile", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(SENSOR_PROFILE_MULT * debuffMult * 100f) + "%", Math.round(SENSOR_PROFILE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) burn level at which the fleet is considered to be moving slowly*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MOVE_SLOW_SPEED_MOD * debuffMult), Math.round(MOVE_SLOW_SPEED_MOD) + "");

        tooltip.addPara("*A slow moving fleet is harder to detect in some types of terrain, and can avoid some hazards. Some abilities also make the fleet move slowly when activated. A fleet is considered slow-moving at a burn level of half of its slowest ship.", 10f, Misc.getGrayColor(), Misc.getHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
        float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - DETECTED_RANGE_MULT * debuffMult, "Compact Profile");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - SENSOR_PROFILE_MULT * debuffMult, "Compact Profile");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), MOVE_SLOW_SPEED_MOD * debuffMult, "Compact Profile");
    }
}

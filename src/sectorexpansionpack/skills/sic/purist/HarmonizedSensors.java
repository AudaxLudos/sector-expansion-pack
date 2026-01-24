package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class HarmonizedSensors extends SCBaseSkillPlugin {
    public static float DETECTED_RANGE_MULT = 0.25f;
    public static float SENSOR_PROFILE_MULT = 0.25f;
    public static float MOVE_SLOW_SPEED_MOD = 3f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();
        String typeText = designData.nonCommonTypeCount > 1 ? "types" : "type";

        tooltip.addPara("The most common design type is %s", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(designData.primary), designData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill effects are reduced by %s due to %s other design " + typeText + " in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.nonCommonTypePenalty * bonusMult * 100f) + "%", designData.nonCommonTypeCount + "");
        tooltip.addPara("Skill effects are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) detected-at range", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(DETECTED_RANGE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(DETECTED_RANGE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor profile", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(SENSOR_PROFILE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(SENSOR_PROFILE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) burn level at which the fleet is considered to be moving slowly*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MOVE_SLOW_SPEED_MOD * bonusMult * penaltyMult), Math.round(MOVE_SLOW_SPEED_MOD * bonusMult) + "");

        tooltip.addPara("*A slow moving fleet is harder to detect in some types of terrain, and can avoid some hazards. Some abilities also make the fleet move slowly when activated. A fleet is considered slow-moving at a burn level of half of its slowest ship.", 10f, Misc.getGrayColor(), Misc.getHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - DETECTED_RANGE_MULT * bonusMult * penaltyMult, "Compact Profile");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - SENSOR_PROFILE_MULT * bonusMult * penaltyMult, "Compact Profile");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), MOVE_SLOW_SPEED_MOD * bonusMult * penaltyMult, "Compact Profile");
    }
}

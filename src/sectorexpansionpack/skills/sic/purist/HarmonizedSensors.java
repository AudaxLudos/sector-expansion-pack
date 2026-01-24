package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class HarmonizedSensors extends SCBaseSkillPlugin {
    public static float DETECTED_RANGE_MULT = 0.25f;
    public static float SENSOR_PROFILE_MULT = 0.25f;
    public static float SENSOR_RANGE_MULT = 0.25f;

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

        tooltip.addPara("The most common design type is %s*", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(designData.primary), designData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill effects are reduced by %s due to %s other design " + typeText + " in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.nonCommonTypePenalty * bonusMult * 100f) + "%", designData.nonCommonTypeCount + "");
        tooltip.addPara("Skill effects are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) detected-at range", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(DETECTED_RANGE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(DETECTED_RANGE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor profile", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(SENSOR_PROFILE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(SENSOR_PROFILE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor range", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(SENSOR_RANGE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(SENSOR_RANGE_MULT * bonusMult * 100f) + "%");

        String statReductionMultText = Math.round(AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type. " +
                "If there is a tie, the type is chosen alphabetically. Each different design type other than the most common incurs a "
                + statReductionMultText + " penalty." + " At least " + dominantFractionText + " of the fleet must share the most common type to avoid the "
                + statReductionMultText + " dominated penalty.", Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - (DETECTED_RANGE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (SENSOR_PROFILE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f - (SENSOR_RANGE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
    }

    @Override
    public void onActivation(SCData data) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - (DETECTED_RANGE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (SENSOR_PROFILE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f + (SENSOR_RANGE_MULT * bonusMult * penaltyMult), "Harmonized Sensors");
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getDetectedRangeMod().unmodify(getId());
        data.getFleet().getStats().getSensorProfileMod().unmodify(getId());
        data.getFleet().getStats().getSensorRangeMod().unmodify(getId());
    }
}

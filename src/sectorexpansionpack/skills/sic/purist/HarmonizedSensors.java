package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class HarmonizedSensors extends SCBaseSkillPlugin {
    public static float DETECTED_RANGE_MULT = 0.20f;
    public static float SENSOR_PROFILE_MULT = 0.20f;
    public static float SENSOR_RANGE_MULT = 0.20f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        tooltip.addPara("%s skill efficiency*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(pData.totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Based value of %s (Max: %s) due to %s primary design type", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getDesignTypeColor(pData.primary)}, Math.round(pData.bonusMult * 100f) + "%", Math.round(pData.bonusMultMax * 100f) + "%", pData.primary);
        tooltip.addPara("Reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(pData.nonCommonTypePenalty * 100f) + "%", pData.nonCommonTypeCount + "");
        tooltip.addPara("Reduced by %s due to the dominance of other design types", 0f, Misc.getNegativeHighlightColor(), Math.round(pData.otherTypeDominancePenalty * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) detected-at range", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(pData.totalMult * DETECTED_RANGE_MULT * 100f) + "%", Math.round(pData.bonusMultMax * DETECTED_RANGE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor profile", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(pData.totalMult * SENSOR_PROFILE_MULT * 100f) + "%", Math.round(pData.bonusMultMax * SENSOR_PROFILE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor range", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(pData.totalMult * SENSOR_RANGE_MULT * 100f) + "%", Math.round(pData.bonusMultMax * SENSOR_RANGE_MULT * 100f) + "%");

        String statReductionMultText = Math.round(AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type" +
                ". If there is a tie, the type is chosen alphabetically" +
                ". Each design type other than the most common reduces skill efficiency by " + statReductionMultText +
                ". If " + dominantFractionText + " of the fleet is not the most common type skill efficiency is reduced by " + statReductionMultText, Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - (pData.totalMult * DETECTED_RANGE_MULT), "Harmonized Sensors");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (pData.totalMult * SENSOR_PROFILE_MULT), "Harmonized Sensors");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f + (pData.totalMult * SENSOR_RANGE_MULT), "Harmonized Sensors");
    }

    @Override
    public void onActivation(SCData data) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        data.getFleet().getStats().getDetectedRangeMod().modifyMult(getId(), 1f - (pData.totalMult * DETECTED_RANGE_MULT), "Harmonized Sensors");
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (pData.totalMult * SENSOR_PROFILE_MULT), "Harmonized Sensors");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f + (pData.totalMult * SENSOR_RANGE_MULT), "Harmonized Sensors");
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getDetectedRangeMod().unmodify(getId());
        data.getFleet().getStats().getSensorProfileMod().unmodify(getId());
        data.getFleet().getStats().getSensorRangeMod().unmodify(getId());
    }
}

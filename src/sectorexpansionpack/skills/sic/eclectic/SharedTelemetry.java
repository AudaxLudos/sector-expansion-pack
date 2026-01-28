package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class SharedTelemetry extends SCBaseSkillPlugin {
    public static float SENSOR_PROFILE_MULT = 0.25f;
    public static float SENSOR_RANGE_MULT = 0.25f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float bonusMult = eclecticData.getSkillEffectBonus();
        float penaltyMult = eclecticData.getSkillEffectPenalty();
        float totalMult = eclecticData.getSkillEffectTotal();

        tooltip.addPara("Total skill efficiency is at %s*", 0f, Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Math.round(totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill efficiency is increased by %s due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()}, Math.round(bonusMult * 100f) + "%", eclecticData.designTypes + "");
        tooltip.addPara("Skill efficiency is reduced by %s due to %s design types above the average design type count", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(penaltyMult * 100f) + "%", eclecticData.designTypesAboveAverage + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) sensor profile", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(totalMult * SENSOR_PROFILE_MULT * 100f) + "%", Math.round(SENSOR_PROFILE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor range", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * SENSOR_RANGE_MULT * 100f) + "%", Math.round(SENSOR_RANGE_MULT * 100f) + "%");
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (effectMult * SENSOR_PROFILE_MULT));
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f - (effectMult * SENSOR_RANGE_MULT));
    }
}

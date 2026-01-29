package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.ui.LabelAPI;
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
        tooltip.addPara("Skill efficiency is reduced by %s due to %s design types above there ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(penaltyMult * 100f) + "%", eclecticData.designTypesAboveAverage + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) sensor profile", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(totalMult * SENSOR_PROFILE_MULT * 100f) + "%", Math.round(SENSOR_PROFILE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor range", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * SENSOR_RANGE_MULT * 100f) + "%", Math.round(SENSOR_RANGE_MULT * 100f) + "%");

        String designTypeShipLimit = AptitudeEclectic.DESIGN_TYPE_SHIP_LIMIT + "";
        String skillEfficiencyLimit = Math.round(AptitudeEclectic.SKILL_EFFECT_MAX_MULT * 100f) + "%";
        String skillEfficiencyPerType = Math.round(AptitudeEclectic.SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT * 100f) + "%";
        LabelAPI label = tooltip.addPara("*Skill efficiency is capped at " + skillEfficiencyLimit +
                ". Every design type in the fleet increases skill efficiency by " + skillEfficiencyPerType +
                ". Every design type has a ship limit of " + designTypeShipLimit +
                ". Every design type above there ship limit reduces skill efficiency by " + skillEfficiencyPerType, Misc.getGrayColor(), 10f);
        label.setHighlight(skillEfficiencyLimit, skillEfficiencyPerType, designTypeShipLimit, skillEfficiencyPerType);
        label.setHighlightColors(Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (effectMult * SENSOR_PROFILE_MULT));
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f - (effectMult * SENSOR_RANGE_MULT));
    }
}

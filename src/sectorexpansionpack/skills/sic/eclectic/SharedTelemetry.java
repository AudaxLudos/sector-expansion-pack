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
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        tooltip.addPara("%s total skill effect multiplier*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), String.format("%+d", Math.round(eData.totalMult * 100f)) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Increased by %s due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.bonusMult * 100f) + "%", eData.designTypesCount + "");
        tooltip.addPara("Reduced by %s due to %s design types above their ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.penaltyMult * 100f) + "%", eData.designTypesAboveLimit + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) sensor profile", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(eData.totalMult * SENSOR_PROFILE_MULT * 100f) + "%", Math.round(eData.bonusMultMax * SENSOR_PROFILE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) sensor range", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(eData.totalMult * SENSOR_RANGE_MULT * 100f) + "%", Math.round(eData.bonusMultMax * SENSOR_RANGE_MULT * 100f) + "%");

        String designTypeShipLimit = eData.designTypesShipLimit + "";
        String multLimit = Math.round(eData.bonusMultMax * 100f) + "%";
        String perTypeMult = Math.round(AptitudeEclectic.SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The skill effect multiplier is capped at " + multLimit +
                ". Every design type in the fleet increases the skill effect multiplier by " + perTypeMult +
                ". Every design type has a ship limit of " + designTypeShipLimit +
                ". Every design type above their ship limit reduces the skill effect multiplier by " + perTypeMult, Misc.getGrayColor(), 10f);
        label.setHighlight(multLimit, perTypeMult, designTypeShipLimit, perTypeMult);
        label.setHighlightColors(Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (eData.totalMult * SENSOR_PROFILE_MULT), "Shared Telemetry");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f + (eData.totalMult * SENSOR_RANGE_MULT), "Shared Telemetry");
    }

    @Override
    public void onActivation(SCData data) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);
        data.getFleet().getStats().getSensorProfileMod().modifyMult(getId(), 1f - (eData.totalMult * SENSOR_PROFILE_MULT), "Shared Telemetry");
        data.getFleet().getStats().getSensorRangeMod().modifyMult(getId(), 1f + (eData.totalMult * SENSOR_RANGE_MULT), "Shared Telemetry");
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getSensorProfileMod().unmodify(getId());
        data.getFleet().getStats().getSensorRangeMod().unmodify(getId());
    }
}

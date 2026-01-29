package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class EveryNookAndCranny extends SCBaseSkillPlugin {
    public static float CARGO_CAP_MULT = 0.5f;
    public static float FUEL_CAP_MULT = 0.5f;

    @Override
    public String getAffectsString() {
        return "all ships";
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

        tooltip.addPara("%s (Max: %s) cargo capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(CARGO_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(CARGO_CAP_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(FUEL_CAP_MULT * bonusMult * 100f) + "%");
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        stats.getFuelMod().modifyMult(getId(), 1f + FUEL_CAP_MULT * effectMult);
        stats.getCargoMod().modifyMult(getId(), 1f + CARGO_CAP_MULT * effectMult);
    }
}

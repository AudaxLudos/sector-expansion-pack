package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;
import java.util.Objects;

public class UnifiedLogistics extends SCBaseSkillPlugin {
    public static float SUPPLIES_PER_MONTH_MULT = 0.25f;
    public static float FUEL_USE_MULT = 0.25f;
    public static float FUEL_CAP_MULT = 0.2f;
    public static float CARGO_CAP_MULT = 0.2f;

    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
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

        tooltip.addPara("%s (Max: %s) monthly supply consumption for ship maintenance", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(SUPPLIES_PER_MONTH_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(SUPPLIES_PER_MONTH_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel usage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_USE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(FUEL_USE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) cargo capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(CARGO_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(CARGO_CAP_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(FUEL_CAP_MULT * bonusMult * 100f) + "%");
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        String variantType = variant.getHullSpec().getManufacturer();
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);

        if (Objects.equals(variantType, designData.primary) || (designData.hasDesignCompromise && Objects.equals(variantType, designData.secondary))) {
            float penaltyMult = designData.computeTotalPenaltyMult();
            float bonusMult = designData.getDoctrineExtremismMult();

            stats.getSuppliesPerMonth().modifyMult(getId(), 1f - SUPPLIES_PER_MONTH_MULT * bonusMult * penaltyMult);
            stats.getFuelUseMod().modifyMult(getId(), 1f - FUEL_USE_MULT * bonusMult * penaltyMult);
            stats.getFuelMod().modifyMult(getId(), 1f + FUEL_CAP_MULT * bonusMult * penaltyMult);
            stats.getCargoMod().modifyMult(getId(), 1f + CARGO_CAP_MULT * bonusMult * penaltyMult);
        }
    }
}

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
        return "ships with the first most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
        String commonDesignType = AptitudePurist.getPrimaryShipDesignType(data);
        float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

        tooltip.addPara("The most common design type is %s", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(commonDesignType), commonDesignType);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Bonuses are reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(otherDesignTypeCount * 10f) + "%", otherDesignTypeCount + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) monthly supply consumption for ship maintenance", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(SUPPLIES_PER_MONTH_MULT * debuffMult * 100f) + "%", Math.round(SUPPLIES_PER_MONTH_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel usage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_USE_MULT * debuffMult * 100f) + "%", Math.round(FUEL_USE_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) cargo capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(CARGO_CAP_MULT * debuffMult * 100f) + "%", Math.round(CARGO_CAP_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_CAP_MULT * debuffMult * 100f) + "%", Math.round(FUEL_CAP_MULT * 100f) + "%");
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        String variantType = variant.getHullSpec().getManufacturer();
        String primaryType = AptitudePurist.getPrimaryShipDesignType(data);
        String secondaryType = AptitudePurist.getSecondaryShipDesignType(data);
        boolean hasDesignCompromise = data.getAllActiveSkillsPlugins().stream().anyMatch(s -> Objects.equals(s.getId(), "sep_sic_design_compromise"));

        if (Objects.equals(variantType, primaryType) || (hasDesignCompromise && Objects.equals(variantType, secondaryType))) {
            int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
            float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

            stats.getSuppliesPerMonth().modifyMult(getId(), 1f - SUPPLIES_PER_MONTH_MULT * debuffMult);
            stats.getFuelUseMod().modifyMult(getId(), 1f - FUEL_USE_MULT * debuffMult);
            stats.getFuelMod().modifyMult(getId(), 1f + FUEL_CAP_MULT * debuffMult);
            stats.getCargoMod().modifyMult(getId(), 1f + CARGO_CAP_MULT * debuffMult);
        }
    }
}

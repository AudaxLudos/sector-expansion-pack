package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;
import java.util.Objects;

public class EfficientOperations extends SCBaseSkillPlugin {
    public static float DP_REDUCTION_MAX = 10f;
    public static float DP_REDUCTION_MULT = 0.2f;

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

        tooltip.addPara("Deployment point cost reduced by %s or %s (Max: %s or %s), whichever is less", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                Math.round(DP_REDUCTION_MULT * debuffMult * 100f) + "%", Math.round(DP_REDUCTION_MAX * debuffMult) + "",
                Math.round(DP_REDUCTION_MULT * 100f) + "%", Math.round(DP_REDUCTION_MAX) + "");
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

            float baseCost = stats.getSuppliesToRecover().getBaseValue();
            float reduction = Math.min(DP_REDUCTION_MAX * debuffMult, baseCost * DP_REDUCTION_MULT) * debuffMult;

            if (stats.getFleetMember() == null || stats.getFleetMember().getVariant() == null || (!stats.getFleetMember().getVariant().hasHullMod("neural_interface") && !stats.getFleetMember().getVariant().hasHullMod("neural_integrator"))) {
                stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(getId(), -reduction);
            }
        }
    }
}

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

public class BaselineRestoration extends SCBaseSkillPlugin {
    public static float SHIP_RECOVERY_MOD = 2f;
    public static float INSTANT_REPAIR_MOD = 0.5f;

    @Override
    public String getAffectsString() {
        return "ships with the first most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();

        tooltip.addPara("The most common design type is %s", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(designData.primary), designData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Bonuses are reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.nonCommonTypePenalty * bonusMult * 100f) + "%", designData.nonCommonTypeCount + "");
        tooltip.addPara("Bonuses are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) chance for ships to be recoverable if lost in combat", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(SHIP_RECOVERY_MOD * bonusMult * penaltyMult * 100f) + "%", Math.round(SHIP_RECOVERY_MOD * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) of hull and armor damage taken repaired after combat ends, at no cost", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(INSTANT_REPAIR_MOD * bonusMult * penaltyMult * 100f) + "%", Math.round(INSTANT_REPAIR_MOD * bonusMult * 100f) + "%");
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        String variantType = variant.getHullSpec().getManufacturer();
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);

        if (Objects.equals(variantType, designData.primary) || (designData.hasDesignCompromise && Objects.equals(variantType, designData.secondary))) {
            float penaltyMult = designData.computeTotalPenaltyMult();
            float bonusMult = designData.getDoctrineExtremismMult();

            stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(getId(), SHIP_RECOVERY_MOD * bonusMult * penaltyMult);
            stats.getDynamic().getMod(Stats.INSTA_REPAIR_FRACTION).modifyFlat(id, INSTANT_REPAIR_MOD * bonusMult * penaltyMult);
        }
    }
}

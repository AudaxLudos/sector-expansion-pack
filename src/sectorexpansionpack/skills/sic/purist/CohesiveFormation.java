package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class CohesiveFormation extends SCBaseSkillPlugin {
    public static float MAX_BURN_MOD = 2;
    public static float ACCELERATION_MULT = 1f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
        String primaryType = AptitudePurist.getPrimaryShipDesignType(data);
        float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

        tooltip.addPara("The most common design type is %s", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(primaryType), primaryType);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Bonuses are reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(otherDesignTypeCount * 10f) + "%", otherDesignTypeCount + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) maximum burn level", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MAX_BURN_MOD * debuffMult), "" + Math.round(MAX_BURN_MOD));
        tooltip.addPara("%s (Max: %s) fleet acceleration", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(ACCELERATION_MULT * debuffMult * 100f) + "%", Math.round(ACCELERATION_MULT * 100f) + "%");
    }

    @Override
    public void advance(SCData data, Float amount) {
        int otherDesignTypeCount = AptitudePurist.getNonCommonShipDesignTypeCount(data);
        float debuffMult = 1f - (otherDesignTypeCount * 0.1f);

        data.getFleet().getStats().getFleetwideMaxBurnMod().modifyFlat(getId(), (float) Math.ceil(MAX_BURN_MOD * debuffMult), "Cohesive Formation");
        data.getFleet().getStats().getAccelerationMult().modifyMult(getId(), 1f + (ACCELERATION_MULT * debuffMult), "Cohesive Formation");
    }
}

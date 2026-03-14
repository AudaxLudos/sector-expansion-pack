package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class ImprovisedRestoration extends SCBaseSkillPlugin {
    public static final float SHIP_RECOVERY_MOD = 2f;
    public static final float INSTANT_REPAIR_MOD = 0.30f;

    @Override
    public String getAffectsString() {
        return "all ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        tooltip.addPara("%s eclectic skill efficiency*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(eData.totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Based value of %s (Max: %s) due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.bonusMult * 100f) + "%", Math.round(eData.bonusMultMax * 100f) + "%", eData.designTypesCount + "");
        tooltip.addPara("Reduced by %s due to %s design types above their ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.penaltyMult * 100f) + "%", eData.designTypesAboveLimit + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s chance for ships to be recoverable if lost in combat (%s × skill efficiency)", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                "+" + Math.round(eData.totalMult * SHIP_RECOVERY_MOD * 100f) + "%", Math.round(SHIP_RECOVERY_MOD * 100f) + "%");
        tooltip.addPara("%s of hull and armor damage taken repaired after combat ends, at no cost (%s × skill efficiency)", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                "+" + Math.round(eData.totalMult * INSTANT_REPAIR_MOD * 100f) + "%", Math.round(INSTANT_REPAIR_MOD * 100f) + "%");

        String designTypeShipLimit = eData.designTypesShipLimit + "";
        String multLimit = Math.round(eData.bonusMultMax * 100f) + "%";
        String perTypeMult = Math.round(AptitudeEclectic.SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT * 100f) + "%";
        LabelAPI label = tooltip.addPara("*Every design type in the fleet increases skill efficiency by " + perTypeMult +
                ". Every design type has a ship limit of " + designTypeShipLimit +
                ". Every design type above their ship limit reduces skill efficiency by " + perTypeMult, Misc.getGrayColor(), 10f);
        label.setHighlight(multLimit, perTypeMult, multLimit, designTypeShipLimit, perTypeMult);
        label.setHighlightColors(Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(getId(), eData.totalMult * SHIP_RECOVERY_MOD);
        stats.getDynamic().getMod(Stats.INSTA_REPAIR_FRACTION).modifyFlat(id, eData.totalMult * INSTANT_REPAIR_MOD);
    }
}

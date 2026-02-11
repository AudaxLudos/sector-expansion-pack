package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class JointReclamation extends SCBaseSkillPlugin {
    public static float POST_BATTLE_SALVAGE_MOD = 0.4f;
    public static float FUEL_SALVAGE_BONUS = 0.4f;
    public static float NON_COMBAT_CREW_LOSS_MULT = 0.5f;

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

        tooltip.addPara("%s (Max: %s) post-battle salvage", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(eData.totalMult * POST_BATTLE_SALVAGE_MOD * 100f) + "%", Math.round(eData.bonusMultMax * POST_BATTLE_SALVAGE_MOD * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel salvage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(eData.totalMult * FUEL_SALVAGE_BONUS * 100f) + "%", Math.round(eData.bonusMultMax * FUEL_SALVAGE_BONUS * 100f) + "%");
        tooltip.addPara("%s (Max: %s) crew loss to non-combat operations", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(eData.totalMult * NON_COMBAT_CREW_LOSS_MULT * 100f) + "%", Math.round(eData.bonusMultMax * NON_COMBAT_CREW_LOSS_MULT * 100f) + "%");

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

        data.getFleet().getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).modifyFlat(getId(), eData.totalMult * POST_BATTLE_SALVAGE_MOD);
        data.getFleet().getStats().getDynamic().getStat(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET).modifyFlat(getId(), eData.totalMult * FUEL_SALVAGE_BONUS);
        data.getFleet().getStats().getDynamic().getStat(Stats.NON_COMBAT_CREW_LOSS_MULT).modifyMult(getId(), 1f - eData.totalMult * NON_COMBAT_CREW_LOSS_MULT);
    }

    @Override
    public void onActivation(SCData data) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        data.getFleet().getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).modifyFlat(getId(), eData.totalMult * POST_BATTLE_SALVAGE_MOD);
        data.getFleet().getStats().getDynamic().getStat(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET).modifyFlat(getId(), eData.totalMult * FUEL_SALVAGE_BONUS);
        data.getFleet().getStats().getDynamic().getStat(Stats.NON_COMBAT_CREW_LOSS_MULT).modifyMult(getId(), 1f - eData.totalMult * NON_COMBAT_CREW_LOSS_MULT);
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).unmodify(getId());
        data.getFleet().getStats().getDynamic().getStat(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET).unmodify(getId());
        data.getFleet().getStats().getDynamic().getStat(Stats.NON_COMBAT_CREW_LOSS_MULT).unmodify(getId());
    }
}

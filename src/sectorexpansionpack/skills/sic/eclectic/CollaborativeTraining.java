package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class CollaborativeTraining extends SCBaseSkillPlugin {
    public static int OFFICER_MAX_ELITE_SKILLS_MOD = 1;
    public static int OFFICER_MAX_LEVEL_MOD = 1;

    @Override
    public String getAffectsString() {
        return "All officers";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float bonusMult = eclecticData.getSkillEffectBonus();
        float maxMult = eclecticData.getMaxSkillEffectMult();
        float penaltyMult = eclecticData.getSkillEffectPenalty();
        float totalMult = eclecticData.getSkillEffectTotal();

        tooltip.addPara("Total skill efficiency is at %s*", 0f, Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Math.round(totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill efficiency is increased by %s due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()}, Math.round(bonusMult * 100f) + "%", eclecticData.designTypes + "");
        tooltip.addPara("Skill efficiency is reduced by %s due to %s design types above there ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(penaltyMult * 100f) + "%", eclecticData.designTypesAboveLimit + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) to maximum level of officers under your command", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(),"+" + Math.round(totalMult * OFFICER_MAX_LEVEL_MOD), Math.round(maxMult * OFFICER_MAX_LEVEL_MOD) + "");
        tooltip.addPara("%s (Max: %s) to maximum number of elite skills for officers under your command", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * OFFICER_MAX_ELITE_SKILLS_MOD), Math.round(maxMult * OFFICER_MAX_ELITE_SKILLS_MOD) + "");
        tooltip.addPara("*If this skill is unassigned officers over the level limit will have excess skills made inactive, prioritising elite skills", 0f, Misc.getGrayColor(), Misc.getHighlightColor());

        String designTypeShipLimit = eclecticData.getDesignTypeShipLimit() + "";
        String skillEfficiencyLimit = Math.round(eclecticData.getMaxSkillEffectMult() * 100f) + "%";
        String skillEfficiencyPerType = Math.round(AptitudeEclectic.SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT * 100f) + "%";
        LabelAPI label = tooltip.addPara("*Skill efficiency is capped at " + skillEfficiencyLimit +
                ". Every design type in the fleet increases skill efficiency by " + skillEfficiencyPerType +
                ". Every design type has a ship limit of " + designTypeShipLimit +
                ". Every design type above there ship limit reduces skill efficiency by " + skillEfficiencyPerType, Misc.getGrayColor(), 10f);
        label.setHighlight(skillEfficiencyLimit, skillEfficiencyPerType, designTypeShipLimit, skillEfficiencyPerType);
        label.setHighlightColors(Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void onActivation(SCData data) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_LEVEL_MOD).modifyFlat(getId(), 1f + OFFICER_MAX_LEVEL_MOD * effectMult);
        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).modifyFlat(getId(),  1f + OFFICER_MAX_ELITE_SKILLS_MOD * effectMult);
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_LEVEL_MOD).unmodify(getId());
        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).unmodify(getId());
    }
}

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

    @Override
    public String getAffectsString() {
        return "All officers";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        tooltip.addPara("%s skill efficiency*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(eData.totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Based value of %s (Max: %s) due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.bonusMult * 100f) + "%", Math.round(eData.bonusMultMax * 100f) + "%", eData.designTypesCount + "");
        tooltip.addPara("Reduced by %s due to %s design types above their ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(eData.penaltyMult * 100f) + "%", eData.designTypesAboveLimit + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) to maximum number of elite skills for officers under your command*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(eData.totalMult * OFFICER_MAX_ELITE_SKILLS_MOD), Math.round(eData.bonusMultMax * OFFICER_MAX_ELITE_SKILLS_MOD) + "");

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
    public void onActivation(SCData data) {
        AptitudeEclectic.EclecticFleetData eData = AptitudeEclectic.getEclecticFleetData(data);

        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).modifyFlat(getId(), 1f + eData.totalMult * OFFICER_MAX_ELITE_SKILLS_MOD);
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getCommander().getStats().getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).unmodify(getId());
    }
}

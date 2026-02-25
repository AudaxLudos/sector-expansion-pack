package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class CivilianTolerance extends SCBaseSkillPlugin {
    @Override
    public String getAffectsString() {
        return "civilian ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        tooltip.addPara("Exclude civilian ships from penalty calculations", Misc.getHighlightColor(), 0f);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Civilian ships with the most common design type is unaffected", 0f, Misc.getHighlightColor());
        tooltip.addPara("If design compromise skill is active, civilian ships with the second most common design type is unaffected", 0f, Misc.getHighlightColor());
        tooltip.setBulletedListMode(null);
    }
}

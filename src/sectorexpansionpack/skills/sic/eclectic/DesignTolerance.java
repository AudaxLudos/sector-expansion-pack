package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DesignTolerance extends SCBaseSkillPlugin {
    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData scData, TooltipMakerAPI tooltip) {
        tooltip.addPara("Max ship count per design type is set to 5", Misc.getHighlightColor(), 0f);
    }
}

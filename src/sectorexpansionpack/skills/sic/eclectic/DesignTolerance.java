package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DesignTolerance extends SCBaseSkillPlugin {
    public static final int DESIGN_TYPE_SHIP_LIMIT = 5;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData scData, TooltipMakerAPI tooltip) {
        tooltip.addPara("Max ship count per design type is increased to %s", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), DESIGN_TYPE_SHIP_LIMIT + "");
    }
}

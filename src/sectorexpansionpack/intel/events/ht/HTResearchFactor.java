package sectorexpansionpack.intel.events.ht;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class HTResearchFactor extends BaseOneTimeFactor {
    protected final String text;

    public HTResearchFactor(int points, String text) {
        super(points);
        this.text = text;
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return this.text + " data gathered";
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Topographic data and sensor readings based on completed research missions", 0f);
            }
        };
    }
}

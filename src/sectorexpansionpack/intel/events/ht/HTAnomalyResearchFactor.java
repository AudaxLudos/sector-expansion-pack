package sectorexpansionpack.intel.events.ht;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseOneTimeFactor;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class HTAnomalyResearchFactor extends BaseOneTimeFactor {
    public HTAnomalyResearchFactor(int points) {
        super(points);
    }

    @Override
    public String getDesc(BaseEventIntel intel) {
        return "Anomaly research data gathered";
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("Topographic data and sensor readings based on the anomalies encountered", 0f);
            }
        };
    }
}

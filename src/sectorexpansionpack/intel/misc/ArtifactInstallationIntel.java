package sectorexpansionpack.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.Set;

public class ArtifactInstallationIntel extends BaseIntelPlugin {
    protected MarketAPI market;
    protected FactionAPI faction;
    protected Industry industry;
    protected SpecialItemSpecAPI specialItemSpec;

    public ArtifactInstallationIntel(MarketAPI market, Industry industry, SpecialItemSpecAPI specialItemSpec) {
        this.market = market;
        this.faction = market.getFaction();
        this.industry = industry;
        this.specialItemSpec = specialItemSpec;

        setPostingRangeLY(3f, true);
        setPostingLocation(this.market.getPrimaryEntity());

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color fc = this.faction.getBaseUIColor();
        float oPad = 10f;

        info.addImage(this.faction.getLogo(), width, 128, oPad);

        info.addPara("Your contacts %s %s let you know that the %s has installed an artifact called %s on their %s in the %s", oPad,
                new Color[]{tc, fc, fc, h, h, h},
                this.market.getOnOrAt(), this.market.getName(), this.faction.getPersonNamePrefix(), this.specialItemSpec.getName(),
                this.industry.getCurrentName(), this.market.getStarSystem().getNameWithLowercaseTypeShort());

        addBulletPoints(info, ListInfoMode.IN_DESC);

        addLogTimestamp(info, tc, oPad);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float oPad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = oPad;
        }

        Color tc = getBulletColorForMode(mode);
        Color h = Misc.getHighlightColor();

        bullet(info);
        boolean isUpdate = getListInfoParam() != null;

        if (mode != ListInfoMode.IN_DESC) {
            info.addPara("System: %s", initPad, tc, h, this.market.getStarSystem().getNameWithLowercaseTypeShort());
            initPad = 0f;
            info.addPara("Market: %s", initPad, tc, h, this.market.getName());
            initPad = 0f;
            info.addPara("Artifact: %s", initPad, tc, h, this.specialItemSpec.getName());
            initPad = 0f;
        }

        unindent(info);
    }

    @Override
    public String getIcon() {
        return this.specialItemSpec.getIconName();
    }

    @Override
    protected String getName() {
        return "Artifact Installation";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.market.getPrimaryEntity();
    }

    @Override
    public boolean shouldRemoveIntel() {
        if (isImportant()) {
            return false;
        }
        if (getDaysSincePlayerVisible() < 30) {
            return false;
        }
        return super.shouldRemoveIntel();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(this.faction.getId());
        return tags;
    }
}

package sectorexpansionpack.colonies;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.colonies.actions.ColonyAction;
import sectorexpansionpack.colonies.actions.ColonyBuildAction;
import sectorexpansionpack.colonies.actions.ColonyImproveAction;
import sectorexpansionpack.colonies.actions.ColonyUpgradeAction;

import java.awt.*;
import java.util.Set;

public class ColonyDevelopmentIntel extends BaseIntelPlugin {
    protected MarketAPI market;
    protected FactionAPI faction;
    protected StarSystemAPI system;
    protected ColonyAction action;

    public ColonyDevelopmentIntel(MarketAPI market, ColonyAction action) {
        this.market = market;
        this.faction = market.getFaction();
        this.system = market.getStarSystem();
        this.action = action;

        setPostingLocation(this.market.getPrimaryEntity());
        setPostingRangeLY(3f);

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public String getIcon() {
        return this.faction.getCrest();
    }

    @Override
    protected String getName() {
        return "Colony Development";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Developments");
        tags.add(getFaction().getId());
        return tags;
    }

    @Override
    public String getSortString() {
        return "Colony";
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        addBasicDescription(info, width, height);
        addDetailedDescription(info, width, height);
    }

    protected void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color tc = Misc.getTextColor();
        Color b = Misc.getHighlightColor();
        Color fc = getFaction().getBaseUIColor();

        info.addImage(getFaction().getLogo(), width, 128, opad);
        LabelAPI label = info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefix()) + " officials has agreed " +
                "on developing a colony in the " + getSystem().getNameWithLowercaseType() + ".", tc, opad);
        label.setHighlightColors(fc, b);
        label.setHighlight(Misc.ucFirst(getFaction().getPersonNamePrefix()), getSystem().getNameWithLowercaseType());
    }

    protected void addDetailedDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color tc = Misc.getTextColor();
        Color b = Misc.getHighlightColor();
        Color fc = getFaction().getBaseUIColor();
        String actionText = getActionText();
        String industryName = ((ColonyBuildAction) this.action).getSpec().getName();
        String indOrStruct = ((ColonyBuildAction) this.action).getSpec().hasTag(Industries.TAG_INDUSTRY) ? "industry" : "structure";

        LabelAPI label = info.addPara("The " + this.market.getName() + " colony in the " +
                getSystem().getNameWithLowercaseType() + " is " + actionText + " " + Misc.getAOrAnFor(industryName)
                + " " + industryName + " " + indOrStruct + ".", tc, opad);
        label.setHighlightColors(fc, b, b);
        label.setHighlight(this.market.getName(), getSystem().getNameWithLowercaseType(), industryName);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        Object param = getListInfoParam();
        boolean isUpdate = param != null;

        if (mode == ListInfoMode.MESSAGES || mode == ListInfoMode.INTEL || mode == ListInfoMode.MAP_TOOLTIP) {
            info.addPara("Faction: %s", initPad, tc, getFaction().getBaseUIColor(), getFaction().getDisplayName());
            initPad = 0f;
            info.addPara(getSystem().getNameWithTypeShort(), tc, initPad);
            initPad = 0f;
        }

        unindent(info);
    }

    protected String getActionText() {
        String result;
        if (this.action instanceof ColonyBuildAction && !(this.action instanceof ColonyUpgradeAction) && !(this.action instanceof ColonyImproveAction)) {
            result = "building";
        } else if (this.action instanceof ColonyUpgradeAction) {
            result = "upgrading";
        } else {
            result = "improving";
        }
        return result;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return getSystem().getHyperspaceAnchor();
    }

    public FactionAPI getFaction() {
        return this.faction;
    }

    public StarSystemAPI getSystem() {
        return this.system;
    }
}

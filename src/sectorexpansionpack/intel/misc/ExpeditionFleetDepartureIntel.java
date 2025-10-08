package sectorexpansionpack.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.Set;

public class ExpeditionFleetDepartureIntel extends BaseIntelPlugin {
    protected RouteManager.RouteData route;
    protected Float sinceLaunched;
    protected FactionAPI faction;
    protected MarketAPI source;

    public ExpeditionFleetDepartureIntel(RouteManager.RouteData route, MarketAPI source) {
        this.route = route;
        this.faction = Global.getSector().getFaction(route.getFactionId());
        this.source = source;

        setPostingRangeLY(3f, true);
        setPostingLocation(source.getPrimaryEntity());

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        float oPad = 10f;

        info.addImage(this.faction.getLogo(), width, 128, oPad);

        LabelAPI label = info.addPara("Your contacts " + this.source.getOnOrAt() + " " + this.source.getName() +
                        " let you know that " + this.faction.getPersonNamePrefixAOrAn() + " " + this.faction.getPersonNamePrefix() +
                        " expedition fleet is preparing for a voyage and will soon depart to an unknown location.",
                oPad, tc);
        label.setHighlight(this.source.getName(), this.faction.getPersonNamePrefix());
        label.setHighlightColors(this.source.getFaction().getBaseUIColor(), this.faction.getBaseUIColor());

        info.addPara("Your contacts believe that the %s found a location of an artifact.", oPad,
                this.faction.getBaseUIColor(), this.faction.getPersonNamePrefix());

        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float oPad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = oPad;

        Color tc = getBulletColorForMode(mode);
        Color g = Misc.getGrayColor();

        bullet(info);
        boolean isUpdate = getListInfoParam() != null;

        if (mode != ListInfoMode.IN_DESC) {
            info.addPara("Faction: " + this.faction.getDisplayName(), initPad, tc,
                    this.faction.getBaseUIColor(), this.faction.getDisplayName());
            initPad = 0f;
        }

        info.addPara("Destination: Unknown", initPad, tc, g, "Unknown");
        initPad = 0f;
        info.addPara("Artifact: Unknown", initPad, tc, g, "Unknown");
        initPad = 0f;

        if (isUpdate) {
            info.addPara("Fleet launched", tc, initPad);
        } else {
            float delay = this.route.getDelay();
            if (delay > 0) {
                addDays(info, "until departure", delay, tc, initPad);
            } else {
                info.addPara("Recently launched", tc, initPad);
            }
        }

        unindent(info);
    }

    @Override
    protected String getName() {
        return "Expedition Fleet";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return this.faction;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.postingLocation;
    }

    @Override
    protected void advanceImpl(float amount) {
        if (this.route.getDelay() > 0) return;
        if (this.sinceLaunched == null) this.sinceLaunched = 0f;
        if (this.sinceLaunched <= 0 && amount > 0) {
            sendUpdateIfPlayerHasIntel(new Object(), true);
        }

        float days = Misc.getDays(amount);
        this.sinceLaunched += days;
    }

    @Override
    public boolean shouldRemoveIntel() {
        if (this.route.getDelay() > 0) return false;
        if (isImportant()) return false;
        return this.sinceLaunched == null || !(this.sinceLaunched < getBaseDaysAfterEnd());
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "sep_expeditionFleet");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_DEPARTURES);
        tags.add(this.faction.getId());
        return tags;
    }

    public String getSortString() {
        return "Expedition Fleet Departure";
    }
}

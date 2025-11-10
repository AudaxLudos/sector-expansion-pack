package sectorexpansionpack.missions;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.ModPlugin;
import sectorexpansionpack.Utils;

import java.util.Objects;
import java.util.Random;

public class EntityFinderMission extends HubMissionWithSearch {
    public EntityFinderMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        return false;
    }

    public void requireMarketCanUseSpecialItem(SpecialItemData specialItemData) {
        this.search.marketReqs.add(new MarketCanUseSpecialItemReq(specialItemData));
    }

    public void requirePlanetNoSpecialSalvage() {
        this.search.planetReqs.add(new PlanetNoSpecialSalvage());
    }

    public void requireEntityNoSpecialSalvage() {
        this.search.entityReqs.add(new EntityNoSpecialSalvage());
    }

    public void requireMarketUsesSpecialItems() {
        this.search.marketReqs.add(new MarketUsesSpecialItems());
    }

    public void requireMarketHasCompatibleSpecialItemsWithOther(MarketAPI other) {
        this.search.marketReqs.add(new MarketHasCompatibleSpecialItemsWithOther(other));
    }

    public void requireEntityNoMemoryFlag(String flag) {
        this.search.entityReqs.add(new EntityBooleanMemoryFlag(flag, true));
    }

    public void requireMarketNoMemoryFlag(String flag) {
        this.search.marketReqs.add(new MarketBooleanMemoryFlag(flag, true));
    }

    public void requirePlanetNoMemoryFlag(String flag) {
        this.search.planetReqs.add(new PlanetBooleanMemoryFlag(flag, true));
    }

    public static class PlanetNoSpecialSalvage implements PlanetRequirement {
        @Override
        public boolean planetMatchesRequirement(PlanetAPI planet) {
            return Misc.getSalvageSpecial(planet) == null;
        }
    }

    public static class EntityNoSpecialSalvage implements EntityRequirement {
        @Override
        public boolean entityMatchesRequirement(SectorEntityToken entity) {
            return Misc.getSalvageSpecial(entity) == null;
        }
    }

    public static class EntityBooleanMemoryFlag implements EntityRequirement {
        String flag;
        boolean negate;

        public EntityBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean entityMatchesRequirement(SectorEntityToken entity) {
            boolean memVal = entity.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }

    public static class MarketBooleanMemoryFlag implements MarketRequirement {
        String flag;
        boolean negate;

        public MarketBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            boolean memVal = market.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }

    public static class MarketUsesSpecialItems implements MarketRequirement {
        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry i : market.getIndustries()) {
                if (i.getSpecialItem() == null) {
                    continue;
                }
                if (!ModPlugin.COLONY_ITEM_WHITELIST.contains(i.getSpecialItem().getId())) {
                    continue;
                }
                return true;
            }
            return false;
        }
    }

    public static class MarketHasCompatibleSpecialItemsWithOther implements MarketRequirement {
        MarketAPI other;

        public MarketHasCompatibleSpecialItemsWithOther(MarketAPI other) {
            this.other = other;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry ind : market.getIndustries()) {
                SpecialItemData otherData = ind.getSpecialItem();
                if (otherData == null) {
                    continue;
                }
                if (!ModPlugin.COLONY_ITEM_WHITELIST.contains(otherData.getId())) {
                    continue;
                }
                for (Industry otherInd : this.other.getIndustries()) {
                    if (otherInd.wantsToUseSpecialItem(otherData)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class MarketCanUseSpecialItemReq implements MarketRequirement {
        public SpecialItemData specialItemData;

        public MarketCanUseSpecialItemReq(SpecialItemData specialItemData) {
            this.specialItemData = specialItemData;
        }

        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry industry : market.getIndustries()) {
                if (industry.wantsToUseSpecialItem(this.specialItemData)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class MarketFactionChangedChecker implements ConditionChecker {
        public String prevFactionId;
        public MarketAPI market;

        public MarketFactionChangedChecker(MarketAPI market) {
            this.prevFactionId = market.getFactionId();
            this.market = market;
        }

        @Override
        public boolean conditionsMet() {
            return !Objects.equals(this.market.getFactionId(), this.prevFactionId);
        }
    }

    public static class PlanetBooleanMemoryFlag implements PlanetRequirement {
        String flag;
        boolean negate;

        public PlanetBooleanMemoryFlag(String flag, boolean negate) {
            this.flag = flag;
            this.negate = negate;
        }

        @Override
        public boolean planetMatchesRequirement(PlanetAPI planet) {
            boolean memVal = planet.getMemoryWithoutUpdate().getBoolean(this.flag);
            if (this.negate) {
                return !memVal;
            }
            return memVal;
        }
    }
}

package sectorexpansionpack.missions;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.Utils;

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
        this.search.marketReqs.add(new ArtifactIncursionMission.MarketCanUseSpecialItemReq(specialItemData));
    }

    public void requirePlanetNoSpecialSalvage() {
        this.search.planetReqs.add(new PlanetNoSpecialSalvage());
    }

    public void requireEntityNoSpecialSalvage() {
        this.search.entityReqs.add(new EntityNoSpecialSalvage());
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

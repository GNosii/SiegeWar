package com.gmail.goosius.siegewar.metadata;

import com.gmail.goosius.siegewar.SiegeWar;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.metadata.CustomDataField;
import com.palmergames.bukkit.towny.object.metadata.IntegerDataField;

public class NationMetaDataController {
    @SuppressWarnings("unused")
    private SiegeWar plugin;

    private static String //IDF keys
        plunderGained = "siegewar_totalplundergained",
        plunderLost = "siegewar_totalplunderlost",
        townsGained = "siegewar_totaltownsgained",
        townsLost = "siegewar_totaltownslost";

    public NationMetaDataController(SiegeWar plugin) {
        this.plugin = plugin;
    }

    public static int getIdf(Nation nation, String key) {
        if (nation.hasMeta(key)) {
            CustomDataField<?> cdf = nation.getMetadata(key);
            if (cdf instanceof IntegerDataField)
                return ((IntegerDataField) cdf).getValue();
        }
        return 0;
    }

    public static void setIdf(Nation nation, String key, int num) {
        if (nation.hasMeta(key)) {
            if (num == 0)
                nation.removeMetaData(nation.getMetadata(key));
            else {
                CustomDataField<?> cdf = nation.getMetadata(key);
                if (cdf instanceof IntegerDataField) {
                    ((IntegerDataField) cdf).setValue(num);
                    nation.save();
                }
                return;
            }
        } else if (num != 0)
            nation.addMetaData(new IntegerDataField(key, num));
    }

    public static int getTotalPlunderGained(Nation nation) {
        return getIdf(nation, plunderGained);
    }

    public static int getTotalPlunderLost(Nation nation) {
        return getIdf(nation, plunderLost);
    }

    public static int getTotalTownsGained(Nation nation) {
        return getIdf(nation, townsGained);
    }

    public static int getTotalTownsLost(Nation nation) {
        return getIdf(nation, townsLost);
    }

    public static void setTotalPlunderGained(Nation nation, int num) {
        setIdf(nation, plunderGained, num);
    }

    public static void setTotalPlunderLost(Nation nation, int num) {
        setIdf(nation, plunderLost, num);
    }

    public static void setTotalTownsGained(Nation nation, int num) {
        setIdf(nation, townsGained, num);
    }

    public static void setTotalTownsLost(Nation nation, int num) {
        setIdf(nation, townsLost, num);
    }
}

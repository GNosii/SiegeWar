package com.gmail.goosius.siegewar.utils;

import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;

/**
 * Util class containing methods related to town flags/permssions.
 */
public class SiegeWarTownUtil {
    public static void disableTownPVP(Town town) {
		if (town.isPVP())
				town.setPVP(false);

		for (TownBlock plot : town.getTownBlocks()) {
			if (plot.getPermissions().pvp) {
				if (plot.getType() == TownBlockType.ARENA)
					plot.setType(TownBlockType.RESIDENTIAL);
			
				plot.getPermissions().pvp = false;
				plot.save();
			}
		}
		town.save();
    }

    /**
	 * Sets pvp flag in a town to the desired setting.
	 * 
	 * @param town The town to set the flag for.
	 * @param desiredSetting The value to set pvp to.
	 */
	public static void setPvpFlag(Town town, boolean desiredSetting) {
		
		if (town.getPermissions().pvp != desiredSetting && SiegeWarSettings.getWarSiegePvpAlwaysOnInBesiegedTowns()) {
			town.getPermissions().pvp = desiredSetting;
			town.save();
		}
	}

}
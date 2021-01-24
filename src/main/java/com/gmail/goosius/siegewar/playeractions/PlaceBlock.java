package com.gmail.goosius.siegewar.playeractions;

import com.gmail.goosius.siegewar.Messaging;
import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.enums.SiegeStatus;
import com.gmail.goosius.siegewar.enums.SiegeWarPermissionNodes;
import com.gmail.goosius.siegewar.objects.Siege;
import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.gmail.goosius.siegewar.utils.SiegeWarBlockUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarDistanceUtil;
import com.gmail.goosius.siegewar.utils.SiegeWarMoneyUtil;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.actions.TownyBuildEvent;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.gmail.goosius.siegewar.settings.Translation;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Nation;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is fired from the SiegeWarActionListener's TownyBuildEvent listener.
 *
 * The class evaluates the event, and determines if it is siege related e.g.:
 * 1. A siege attack request  (place coloured banner outside town)
 * 2. A siege abandon request  (place white banner near attack banner)
 * 3. A town surrender request  (place white banner in town)
 * 4. A town invasion request (place chest near attack banner)
 * 5. A town plunder request (place coloured banner near attack banner)
 * 6. A siege-forbidden block
 * 7. None of the above
 * 
 * If the place block event is determined to be a siege action,
 * this class then calls an appropriate class/method in the 'playeractions' package
 *
 * @author Goosius
 */
public class PlaceBlock {
	
	/**
	 * Evaluates a block placement request.
	 * If the block is a standing banner or chest, this method calls an appropriate private method.
	 *
	 * @param player The player placing the block
	 * @param block The block about to be placed
	 * @param event The event object related to the block placement    	
	 */
	public static void evaluateSiegeWarPlaceBlockRequest(Player player, Block block, TownyBuildEvent event) {
		
		try {
			Material mat = block.getType();

			//Standing Banner placement
			if (Tag.BANNERS.isTagged(mat) && !mat.toString().contains("_W"))
                try {
					if (isValidPlacement(block))
                    	evaluatePlaceStandingBanner(player, block);
                } catch (TownyException e1) {
                    Messaging.sendErrorMsg(player, e1.getMessage());
                    return;
                }
	
			//Chest placement
			if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST)
				try {
					if (isValidPlacement(block))
						evaluatePlaceChest(player, block);
				} catch (TownyException e) {
					Messaging.sendErrorMsg(player, e.getMessage());
					return;
				}
	
			//Check for forbidden block placement
			if(SiegeWarSettings.isWarSiegeZoneBlockPlacementRestrictionsEnabled() 
					&& TownyAPI.getInstance().isWilderness(block) 
					&& SiegeWarDistanceUtil.isLocationInActiveSiegeZone(block.getLocation())
					&& SiegeWarSettings.getWarSiegeZoneBlockPlacementRestrictionsMaterials().contains(mat))
					throw new TownyException(Translation.of("msg_war_siege_zone_block_placement_forbidden"));
			
		} catch (TownyException e) {
			event.setCancelled(true);
			event.setMessage(e.getMessage());
		}
	}

	/**
	 * Evaluates a banner placement request. Determines which type of banner this
	 * is, and where it is being placed. Then calls an appropriate private method.
	 * 
	 * @param player Player placing a banner.
	 * @param block The banner.
	 * @throws TownyException thrown when the banner is not allowed to be placed.
	 */
	private static void evaluatePlaceStandingBanner(Player player, Block block) throws TownyException {

		// All outcomes require a town.
		Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
		if (resident == null || !resident.hasTown())
			throw new TownyException(Translation.of("msg_err_siege_war_action_not_a_town_member"));

		/*
		 * The banner is being placed in the wilderness as either a Nation abandoning a
		 * siege, or beginning a siege.
		 * 
		 * All wilderness outcomes require a nation.
		 */
		if (TownyAPI.getInstance().isWilderness(block)) {

			// Fail early if this Resident's Town has no Nation.
			if (!resident.hasNation())
				throw new TownyException(Translation.of("msg_err_siege_war_action_not_a_nation_member"));

			Town town = null;
			Nation nation = null;
			try {
				town = resident.getTown();
				nation = town.getNation();
			} catch (NotRegisteredException ignored) {
			}

			if (isWhiteBanner(block)) {
				// Nation abandoning the siege.

				if (!SiegeWarSettings.getWarSiegeAbandonEnabled())
					return;

				// If player has no permission to abandon,send error
				if (!TownyUniverse.getInstance().getPermissionSource().testPermission(player, SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_ABANDON.getNode()))
					throw new TownyException(Translation.of("msg_err_command_disable"));
				
				// Fail early if the nation has no sieges.
				if (!SiegeController.hasSieges(nation))
					throw new TownyException(Translation.of("msg_err_siege_war_cannot_abandon_nation_not_attacking_zone"));

				// Start abandoning the siege.
				AbandonAttack.processAbandonSiegeRequest(block, nation);

			} else {
				// Nation starting a siege or invading a town.
				evaluatePlaceColouredBannerInWilderness(block, player, resident, town, nation);
			}

		/*
		 * The banner is being placed in a town, which means it is a Town
		 * trying to surrender their siege. 
		 */
		} else {
			Town town = TownyAPI.getInstance().getTown(block.getLocation());
			// Town found
			if (town != null 
					&& SiegeWarSettings.getWarSiegeSurrenderEnabled() 
					&& SiegeController.hasSiege(town) 
					&& isWhiteBanner(block)) {
				if (!town.hasResident(resident))
		            throw new TownyException(Translation.of("msg_err_siege_war_cannot_surrender_not_your_town"));

				if (!TownyUniverse.getInstance().getPermissionSource().testPermission(player, SiegeWarPermissionNodes.SIEGEWAR_TOWN_SIEGE_SURRENDER.getNode()))
	                throw new TownyException(Translation.of("msg_err_command_disable"));

				if (SiegeController.getSiege(town).getStatus() != SiegeStatus.IN_PROGRESS)
					throw new TownyException(Translation.of("msg_err_siege_war_cannot_surrender_siege_finished"));

				if(town.isConquered())
					throw new TownyException(Translation.of("msg_war_siege_occupied_towns_cannot_surrender"));

				SurrenderTown.defenderSurrender(SiegeController.getSiege(town));
			}
		}
	}

	/**
	 * Evaluates placing a coloured banner in the wilderness.
	 * Determines if the event will be considered as an attack or invade request.
	 * @throws TownyException when the banner will not be allowed.
	 */
	private static void evaluatePlaceColouredBannerInWilderness(Block block, Player player, Resident resident, Town attackingTown, Nation nation) throws TownyException {

		// Fail early if this is not a siege-enabled world.
		if(!SiegeWarDistanceUtil.isSiegeWarEnabledInWorld(block.getWorld()))
			throw new TownyException(Translation.of("msg_err_siege_war_not_enabled_in_world"));
		
		List<TownBlock> nearbyCardinalTownBlocks = SiegeWarBlockUtil.getCardinalAdjacentTownBlocks(block);

		//Ensure that only one of the cardinal points has a townblock
		if(nearbyCardinalTownBlocks.size() > 1)
			throw new TownyException(Translation.of("msg_err_siege_war_too_many_adjacent_cardinal_town_blocks"));

		//Get nearby town
		Town town;
		try {
			town = nearbyCardinalTownBlocks.get(0).getTown();
		} catch (NotRegisteredException e) {
			return;
		}

		//Ensure that there is only one town adjacent
		List<TownBlock> adjacentTownBlocks = new ArrayList<>();
		adjacentTownBlocks.addAll(nearbyCardinalTownBlocks);
		adjacentTownBlocks.addAll(SiegeWarBlockUtil.getNonCardinalAdjacentTownBlocks(block));
		for(TownBlock adjacentTownBlock: adjacentTownBlocks) {
			try {
				if (adjacentTownBlock.getTown() != town)
					throw new TownyException(Translation.of("msg_err_siege_war_too_many_adjacent_towns"));
			} catch (NotRegisteredException nre) {}
		}

		//If the town has a siege where the player's nation is already attacking, 
		//attempt invasion, otherwise attempt attack
		if(SiegeController.hasSiege(town) && SiegeController.getSiege(town).getAttackingNation() == nation) {

			if (!SiegeWarSettings.getWarSiegeInvadeEnabled())
				return;

			if (!TownyUniverse.getInstance().getPermissionSource().testPermission(player, SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_INVADE.getNode()))
				throw new TownyException(Translation.of("msg_err_command_disable"));

			Siege siege = SiegeController.getSiege(town);
			if (siege.getStatus() != SiegeStatus.ATTACKER_WIN && siege.getStatus() != SiegeStatus.DEFENDER_SURRENDER)
				throw new TownyException(Translation.of("msg_err_siege_war_cannot_invade_without_victory"));
			
			if(attackingTown == town)
				throw new TownyException(Translation.of("msg_err_siege_war_cannot_invade_own_town"));
			
			InvadeTown.processInvadeTownRequest(nation, town, siege);

		} else {

			if (!SiegeWarSettings.getWarSiegeAttackEnabled())
				return;

			if (!TownyUniverse.getInstance().getPermissionSource().testPermission(player, SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_ATTACK.getNode()))
				throw new TownyException(Translation.of("msg_err_command_disable"));
			
	        try {
				if (TownySettings.isUsingEconomy() && !nation.getAccount().canPayFromHoldings(SiegeWarMoneyUtil.getSiegeCost(town)))
					throw new TownyException(Translation.of("msg_err_no_money"));
			} catch (EconomyException ignored) {}
	        
	        if(getNumActiveAttackSieges(nation) >= SiegeWarSettings.getWarSiegeMaxActiveSiegeAttacksPerNation())
				throw new TownyException(Translation.of("msg_err_siege_war_nation_has_too_many_active_siege_attacks"));
			
			if (attackingTown == town)
                throw new TownyException(Translation.of("msg_err_siege_war_cannot_attack_own_town"));
			
			if(SiegeWarBlockUtil.isSupportBlockUnstable(block))
				throw new TownyException(Translation.of("msg_err_siege_war_banner_support_block_not_stable"));

			AttackTown.processAttackTownRequest(
				attackingTown,
				nation,
				block,
				nearbyCardinalTownBlocks.get(0),
				town);
		}

	}

	/**
	 * Evaluates placing a chest.
	 * Determines if the event will be considered as a plunder request.
	 * @throws TownyException when the chest is not allowed to be placed.
	 */
	private static void evaluatePlaceChest(Player player, Block block) throws TownyException {
		if (!SiegeWarSettings.getWarSiegePlunderEnabled() || !TownyAPI.getInstance().isWilderness(block))
			return;
		
		if(!TownySettings.isUsingEconomy())
			throw new TownyException(Translation.of("msg_err_siege_war_cannot_plunder_without_economy"));
		
		List<TownBlock> nearbyTownBlocks = SiegeWarBlockUtil.getCardinalAdjacentTownBlocks(block);

		if (nearbyTownBlocks.size() > 1) //More than one town block nearby. Error
			throw new TownyException(Translation.of("msg_err_siege_war_too_many_town_blocks_nearby"));

		//Get nearby town
		Town town = null;
		try {
			town = nearbyTownBlocks.get(0).getTown();
		} catch (NotRegisteredException ignored) {}

		//If there is no siege, do normal block placement
		if(!SiegeController.hasSiege(town))
			return;

		//Attempt plunder.
		PlunderTown.processPlunderTownRequest(player, town);
	}
	
	private static boolean isWhiteBanner(Block block) {
		return block.getType() == Material.WHITE_BANNER  && ((Banner) block.getState()).getPatterns().size() == 0;
	}
	
	private static int getNumActiveAttackSieges(Nation nation) {
		int result = 0;
		for(Siege siege: SiegeController.getSieges()) {
			if(siege.getAttackingNation() == nation && siege.getStatus().isActive())
				result++;
		}
		return result;
	}

	private static boolean isValidPlacement(Block block) {
		if (!TownyAPI.getInstance().isWilderness(block)) {
			if (isWhiteBanner(block))
				return true;
		} else {
			if (SiegeWarBlockUtil.getCardinalAdjacentTownBlocks(block).size() > 0)
				return true;
		}
		return false;
	}
}


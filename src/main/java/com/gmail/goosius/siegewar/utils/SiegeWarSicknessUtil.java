package com.gmail.goosius.siegewar.utils;

import com.gmail.goosius.siegewar.SiegeController;
import com.gmail.goosius.siegewar.enums.SiegeWarPermissionNodes;
import com.gmail.goosius.siegewar.objects.Siege;
import com.gmail.goosius.siegewar.settings.SiegeWarSettings;
import com.gmail.goosius.siegewar.settings.Translation;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.util.BukkitTools;
import com.palmergames.util.TimeTools;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class SiegeWarSicknessUtil {

    public static void punishNonSiegeParticipantsInSiegeZone() {

        for (Player player : BukkitTools.getOnlinePlayers()) {
            Location location = player.getLocation();

            // Players immune to war nausea won't be punished
            if (player.hasPermission(SiegeWarPermissionNodes.SIEGEWAR_IMMUNE_TO_WAR_NAUSEA.getNode()))
                continue;

            List<Siege> sieges = SiegeController.getActiveSiegesAt(location);

            // not in a siege zone
            if (sieges.isEmpty())
                continue;

            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());

            if (resident == null)
                continue;

            boolean allowedInAnyOverlappingSiege = false;
            try {
                for (Siege siege : sieges) {
                    if (isSiegeParticipant(resident, siege)) {
                        allowedInAnyOverlappingSiege = true;
                        break;
                    }
                }

                if (!allowedInAnyOverlappingSiege) {
                    if (isInOwnClaims(resident)) {
                        punishWithSpecialWarSickness(player);
                    } else {
                        punishWithFullWarSickness(player);
                    }

                }
            } catch (NotRegisteredException ignored) {}

        }

    }

    public static void punishWithFullWarSickness(Player player) {
        final int effectDurationTicks = (int)(TimeTools.convertToTicks(TownySettings.getShortInterval() + 5));
        if (SiegeWarSettings.getSicknessWarningTimeInTicks() / 20 >= 1) {
            player.sendMessage(Translation.of("plugin_prefix") + Translation.of("msg_you_will_get_sickness",
                    SiegeWarSettings.getSicknessWarningTimeInTicks() / 20));
        }
        Towny.getPlugin().getServer().getScheduler().runTaskLater(Towny.getPlugin(), () -> {
            try {
                Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
                List<Siege> sieges = SiegeController.getActiveSiegesAt(player.getLocation());
                boolean allowedInAnyOverlappingSiege = false;
                for (Siege siege : sieges) {
                    if (isSiegeParticipant(resident, siege)) {
                        allowedInAnyOverlappingSiege = true;
                        break;
                    }
                }

                if (!allowedInAnyOverlappingSiege && SiegeWarDistanceUtil.isLocationInActiveSiegeZone(player.getLocation())) {
                    // still in siege zone
                    List<PotionEffect> potionEffects = new ArrayList<>();
                    potionEffects.add(new PotionEffect(PotionEffectType.CONFUSION, effectDurationTicks, 4));
                    potionEffects.add(new PotionEffect(PotionEffectType.POISON, effectDurationTicks, 4));
                    potionEffects.add(new PotionEffect(PotionEffectType.WEAKNESS, effectDurationTicks, 4));
                    potionEffects.add(new PotionEffect(PotionEffectType.SLOW, effectDurationTicks, 2));
                    potionEffects.add(new PotionEffect(PotionEffectType.SLOW_DIGGING, effectDurationTicks, 2));
                    player.addPotionEffects(potionEffects);
                    player.sendMessage(Translation.of("plugin_prefix") + Translation.of("msg_you_received_war_sickness"));
                }
            } catch (NotRegisteredException ignored) {}
        }, SiegeWarSettings.getSicknessWarningTimeInTicks());
    }

    public static void punishWithSpecialWarSickness(Player player) {
        final int effectDurationTicks = (int)(TimeTools.convertToTicks(TownySettings.getShortInterval() + 5));
        Towny.getPlugin().getServer().getScheduler().runTask(Towny.getPlugin(), new Runnable() {
            public void run() {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effectDurationTicks, 4));
            }
        });
    }

    public static boolean isSiegeParticipant(Resident resident, Siege siege) throws NotRegisteredException {

        if (!resident.hasTown())
            return false;

        Town defendingTown = siege.getDefendingTown();
        Town residentTown = resident.getTown();
        Nation attackingNation = siege.getAttackingNation();

        if (residentTown == defendingTown && resident.getPlayer()
                .hasPermission(SiegeWarPermissionNodes.SIEGEWAR_TOWN_SIEGE_POINTS.getNode())) {
            // Player is defending their own town
            return true;
        }

        if (residentTown.hasNation() &&
                (attackingNation == residentTown.getNation() || attackingNation.hasMutualAlly(residentTown.getNation()))
                && resident.getPlayer().hasPermission(SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_POINTS.getNode())) {
            // Player is attacking
            return true;
        }

        if (defendingTown.hasNation()
                && (defendingTown.getNation() == residentTown.getNation()
                || defendingTown.getNation().hasMutualAlly(residentTown.getNation()))
                && resident.getPlayer().hasPermission(SiegeWarPermissionNodes.SIEGEWAR_NATION_SIEGE_POINTS.getNode())) {
            // Player is defending another town in the nation
            return true;
        }

        return false;
    }

    private static boolean isInOwnClaims(Resident resident) throws NotRegisteredException {
        Location location = resident.getPlayer().getLocation();
        if (!resident.hasTown())
            return false;

        if (TownyAPI.getInstance().isWilderness(location))
            return false;

        return TownyAPI.getInstance().getTownBlock(location).getTown().equals(resident.getTown());
    }

}

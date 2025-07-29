package dev.m7med.punishmentsystem.mangers;

import dev.m7med.punishmentsystem.PunishmentSystem;
import dev.m7med.punishmentsystem.database.DatabaseManager;
import dev.m7med.punishmentsystem.model.Punishment;
import dev.m7med.punishmentsystem.model.PunishmentCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main manager for handling all punishment operations.
 * Provides methods for issuing, removing, and managing player punishments.
 * 
 * This class is responsible for:
 * - Issuing new punishments (bans, mutes, kicks)
 * - Removing existing punishments
 * - Managing punishment conflicts
 * - Handling punishment notifications
 * - Coordinating between database and cache
 */
public class PunishmentManager {
    
    // ========================================
    // FIELDS
    // ========================================
    
    private final DatabaseManager db;
    private final PunishmentCache cache;
    private final PunishmentSystem plugin = PunishmentSystem.getInstance();

    // ========================================
    // CONSTRUCTOR
    // ========================================

    /**
     * Creates a new PunishmentManager with the specified database manager.
     * 
     * @param db The database manager to use for persistence
     */
    public PunishmentManager(DatabaseManager db) {
        this.db = db;
        this.cache = new PunishmentCache(db);
    }

    // ========================================
    // CORE PUNISHMENT METHODS
    // ========================================

    /**
     * Issues a new punishment for a player.
     * 
     * @param playerUUID The UUID of the player to punish
     * @param playerName The name of the player to punish
     * @param type The type of punishment to issue
     * @param reason The reason for the punishment
     * @param actor The name of who issued the punishment
     * @param expiresAt When the punishment expires (null for permanent)
     * @return CompletableFuture that completes when the punishment is saved
     */
    public CompletableFuture<Void> punish(UUID playerUUID, String playerName, Punishment.Type type,
                                          String reason, String actor, Instant expiresAt) {
        Punishment p = new Punishment(0, playerUUID, playerName, reason, actor, type, ExpireTimeManager.now(), expiresAt, true);

        return db.savePunishment(p).thenRun(() -> {
            if (type == Punishment.Type.MUTE || type == Punishment.Type.TEMP_MUTE) {
                cache.addMutePunishment(playerUUID, p);
            }
        });
    }

    /**
     * Removes a punishment (marks as expired).
     * 
     * @param p The punishment to remove
     * @return CompletableFuture that completes when the punishment is removed
     */
    public CompletableFuture<Void> removePunishment(Punishment p) {
        return db.removePunishment(p.getId()).thenRun(() -> {
            if (p.getType() == Punishment.Type.MUTE || p.getType() == Punishment.Type.TEMP_MUTE) {
                cache.removeMutePunishment(p.getPlayerUUID());
            }
        });
    }

    /**
     * Removes a punishment with a specific source (for tracking who removed it).
     * 
     * @param p The punishment to remove
     * @param source The name of who removed the punishment
     * @return CompletableFuture that completes when the punishment is removed
     */
    public CompletableFuture<Void> removePunishment(Punishment p, String source) {
        return db.removePunishment(p.getId(), source).thenRun(() -> {
            if (p.getType() == Punishment.Type.MUTE || p.getType() == Punishment.Type.TEMP_MUTE) {
                cache.removeMutePunishment(p.getPlayerUUID());
            }
        });
    }

    // ========================================
    // QUERY METHODS
    // ========================================

    /**
     * Gets the active punishment for a player by UUID and type.
     * 
     * @param playerUUID The UUID of the player
     * @param type The type of punishment to look for
     * @return CompletableFuture containing the active punishment, or null if none found
     */
    public CompletableFuture<Punishment> getActiveByUUID(UUID playerUUID, Punishment.Type type) {
        if (type == Punishment.Type.MUTE || type == Punishment.Type.TEMP_MUTE) {
            return cache.getActiveMute(playerUUID);
        }
        return db.getActiveByUUID(playerUUID, type);
    }

    /**
     * Gets the punishment history for a player by name.
     * 
     * @param playerName The name of the player
     * @return CompletableFuture containing the list of punishments
     */
    public CompletableFuture<List<Punishment>> history(String playerName) {
        return db.getPunishmentsByName(playerName);
    }

    /**
     * Gets the punishment cache.
     * 
     * @return The punishment cache instance
     */
    public PunishmentCache getCache() {
        return cache;
    }

    /**
     * Gets the database manager.
     * 
     * @return The database manager instance
     */
    public DatabaseManager getDatabase() {
        return db;
    }

    // ========================================
    // CONFLICT RESOLUTION METHODS
    // ========================================

    /**
     * Checks if there are any conflicting punishments for a player.
     * 
     * @param playerUUID The UUID of the player
     * @param newType The type of punishment being applied
     * @return CompletableFuture containing true if no conflicts, false otherwise
     */
    private CompletableFuture<Boolean> checkConflictingPunishments(UUID playerUUID, Punishment.Type newType) {
        CompletableFuture<Punishment> banCheck = getActiveByUUID(playerUUID, Punishment.Type.BAN);
        CompletableFuture<Punishment> tempBanCheck = getActiveByUUID(playerUUID, Punishment.Type.TEMP_BAN);
        CompletableFuture<Punishment> ipBanCheck = getActiveByUUID(playerUUID, Punishment.Type.IP_BAN);
        CompletableFuture<Punishment> muteCheck = getActiveByUUID(playerUUID, Punishment.Type.MUTE);
        CompletableFuture<Punishment> tempMuteCheck = getActiveByUUID(playerUUID, Punishment.Type.TEMP_MUTE);

        return CompletableFuture.allOf(banCheck, tempBanCheck, ipBanCheck, muteCheck, tempMuteCheck)
                .thenApply(v -> {
                    Punishment ban = banCheck.join();
                    Punishment tempBan = tempBanCheck.join();
                    Punishment ipBan = ipBanCheck.join();
                    Punishment mute = muteCheck.join();
                    Punishment tempMute = tempMuteCheck.join();

                    switch (newType) {
                        case BAN:
                            return ban == null && ipBan == null;
                        case TEMP_BAN:
                            return tempBan == null && ban == null && ipBan == null;
                        case IP_BAN:
                            return ipBan == null;
                        case MUTE:
                            return mute == null && tempMute == null;
                        case TEMP_MUTE:
                            return tempMute == null && mute == null;
                        default:
                            return true;
                    }
                });
    }

    /**
     * Removes conflicting punishments when applying a new one.
     * 
     * @param playerUUID The UUID of the player
     * @param newType The type of punishment being applied
     * @return CompletableFuture that completes when conflicts are resolved
     */
    private CompletableFuture<Void> removeConflictingPunishments(UUID playerUUID, Punishment.Type newType) {
        return CompletableFuture.runAsync(() -> {
            try {
                switch (newType) {
                    case IP_BAN:
                        // Remove regular ban and temp ban when applying IP ban
                        Punishment ban = getActiveByUUID(playerUUID, Punishment.Type.BAN).get();
                        Punishment tempBan = getActiveByUUID(playerUUID, Punishment.Type.TEMP_BAN).get();

                        if (ban != null) {
                            removePunishment(ban).get();
                        }
                        if (tempBan != null) {
                            removePunishment(tempBan).get();
                        }
                        break;

                    case BAN:
                        // Remove temp ban when applying permanent ban
                        Punishment existingTempBan = getActiveByUUID(playerUUID, Punishment.Type.TEMP_BAN).get();
                        if (existingTempBan != null) {
                            removePunishment(existingTempBan).get();
                        }
                        break;

                    case MUTE:
                        // Remove temp mute when applying permanent mute
                        Punishment existingTempMute = getActiveByUUID(playerUUID, Punishment.Type.TEMP_MUTE).get();
                        if (existingTempMute != null) {
                            removePunishment(existingTempMute).get();
                        }
                        break;

                    case TEMP_BAN:
                        // Remove permanent ban when applying temp ban (upgrade scenario)
                        Punishment existingBan = getActiveByUUID(playerUUID, Punishment.Type.BAN).get();
                        if (existingBan != null) {
                            removePunishment(existingBan).get();
                        }
                        break;

                    case TEMP_MUTE:
                        // Remove permanent mute when applying temp mute (upgrade scenario)
                        Punishment existingMute = getActiveByUUID(playerUUID, Punishment.Type.MUTE).get();
                        if (existingMute != null) {
                            removePunishment(existingMute).get();
                        }
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error removing conflicting punishments: " + e.getMessage());
            }
        });
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Runs a task on the main thread if not already on it.
     * 
     * @param task The task to run
     */
    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ========================================
    // BAN METHODS
    // ========================================

    /**
     * Permanently bans a player.
     * 
     * @param sender The command sender issuing the ban
     * @param player The player to ban
     * @param reason The reason for the ban
     * @return CompletableFuture that completes when the ban is applied
     */
    public CompletableFuture<Void> ban(CommandSender sender, OfflinePlayer player, String reason) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        return getActiveByUUID(uuid, Punishment.Type.IP_BAN).thenCompose(ipBan -> {
            if (ipBan != null) {
                if (sender != null) {
                    Component message = ConfigManager.getMessage("already-banned", Map.of(),
                            "<red>Player already has an active IP ban!");
                    runOnMainThread(() -> sender.sendMessage(message));
                }
                return CompletableFuture.completedFuture(null);
            }

            return removeConflictingPunishments(uuid, Punishment.Type.BAN)
                    .thenCompose(v -> punish(uuid, playerName, Punishment.Type.BAN, reason, sender.getName(), null))
                    .thenRun(() -> {
                        runOnMainThread(() -> {
                            if (sender != null) {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("player", playerName);
                                placeholders.put("reason", reason);
                                Component message = ConfigManager.getMessage("ban-success", placeholders,
                                        "<green>%player% has been permanently banned. Reason: %reason%");
                                sender.sendMessage(message);
                            }

                            Player onlinePlayer = Bukkit.getPlayer(uuid);
                            if (onlinePlayer != null) {
                                Map<String, String> kickPlaceholders = new HashMap<>();
                                kickPlaceholders.put("reason", reason);
                                Component kickMessage = ConfigManager.getMessage("join-banned", kickPlaceholders,
                                        "<red>You are permanently banned! Reason: %reason%");
                                onlinePlayer.kick(kickMessage);
                            }
                        });
                    });
        });
    }

    /**
     * Temporarily bans a player.
     * 
     * @param sender The command sender issuing the ban
     * @param player The player to ban
     * @param reason The reason for the ban
     * @param expiresAt When the ban expires
     * @return CompletableFuture that completes when the ban is applied
     */
    public CompletableFuture<Void> tempBan(CommandSender sender, OfflinePlayer player, String reason, Instant expiresAt) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        return getActiveByUUID(uuid, Punishment.Type.IP_BAN).thenCompose(ipBan -> {
            if (ipBan != null) {
                if (sender != null) {
                    Component message = ConfigManager.getMessage("already-banned", Map.of(),
                            "<red>Player already has an active IP ban!");
                    runOnMainThread(() -> sender.sendMessage(message));
                }
                return CompletableFuture.completedFuture(null);
            }

            return removeConflictingPunishments(uuid, Punishment.Type.TEMP_BAN)
                    .thenCompose(v -> punish(uuid, playerName, Punishment.Type.TEMP_BAN, reason, sender.getName(), expiresAt))
                    .thenRun(() -> {
                        runOnMainThread(() -> {
                            if (sender != null) {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("player", playerName);
                                placeholders.put("reason", reason);
                                placeholders.put("duration", ExpireTimeManager.formatDurationSimple(Duration.between(ExpireTimeManager.now(), expiresAt).plusSeconds(2)));

                                Component message = ConfigManager.getMessage("tempban-success", placeholders,
                                        "<green>%player% banned for %duration%. Reason: %reason%");
                                sender.sendMessage(message);
                            }

                            Player target = Bukkit.getPlayer(uuid);
                            if (target != null) {
                                Map<String, String> kickPlaceholders = new HashMap<>();
                                kickPlaceholders.put("reason", reason);
                                kickPlaceholders.put("duration", ExpireTimeManager.formatDurationSimple(Duration.between(ExpireTimeManager.now(), expiresAt)));
                                kickPlaceholders.put("expire", ExpireTimeManager.formatInstantDetailed(expiresAt));

                                Component kickMessage = ConfigManager.getMessage("join-tempban", kickPlaceholders,
                                        "<red>You are temporarily banned for %duration%. Reason: %reason%");
                                target.kick(kickMessage);
                            }
                        });
                    });
        });
    }

    // ========================================
    // MUTE METHODS
    // ========================================

    /**
     * Permanently mutes a player.
     * 
     * @param sender The command sender issuing the mute
     * @param player The player to mute
     * @param reason The reason for the mute
     * @return CompletableFuture that completes when the mute is applied
     */
    public CompletableFuture<Void> mute(CommandSender sender, OfflinePlayer player, String reason) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        return removeConflictingPunishments(uuid, Punishment.Type.MUTE)
                .thenCompose(v -> {
                    Punishment mutePunishment = new Punishment(0, uuid, playerName, reason,
                            sender.getName(), Punishment.Type.MUTE, ExpireTimeManager.now(), null, true);

                    return db.savePunishment(mutePunishment)
                            .thenRun(() -> {
                                // Update cache after successful DB save
                                cache.addMutePunishment(uuid, mutePunishment);
                                notifyMute(sender, player, reason, null);
                            });
                });
    }

    /**
     * Temporarily mutes a player.
     * 
     * @param sender The command sender issuing the mute
     * @param player The player to mute
     * @param reason The reason for the mute
     * @param expiresAt When the mute expires
     * @return CompletableFuture that completes when the mute is applied
     */
    public CompletableFuture<Void> tempMute(CommandSender sender, OfflinePlayer player,
                                            String reason, Instant expiresAt) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        return removeConflictingPunishments(uuid, Punishment.Type.TEMP_MUTE)
                .thenCompose(v -> {
                    Punishment tempMute = new Punishment(0, uuid, playerName, reason,
                            sender.getName(), Punishment.Type.TEMP_MUTE, ExpireTimeManager.now(), expiresAt, true);

                    return db.savePunishment(tempMute)
                            .thenRun(() -> {
                                // Update cache after successful DB save
                                cache.addMutePunishment(uuid, tempMute);
                                notifyMute(sender, player, reason, expiresAt);
                            });
                });
    }

    /**
     * Notifies the sender and target player about a mute action.
     * 
     * @param sender The command sender who issued the mute
     * @param player The player who was muted
     * @param reason The reason for the mute
     * @param expiresAt When the mute expires (null for permanent)
     */
    private void notifyMute(CommandSender sender, OfflinePlayer player,
                            String reason, Instant expiresAt) {
        runOnMainThread(() -> {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();

            if (sender != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", playerName);
                placeholders.put("reason", reason);

                if (expiresAt != null) {
                    placeholders.put("duration", ExpireTimeManager.formatDurationSimple(Duration.between(ExpireTimeManager.now(), expiresAt)));
                    Component message = ConfigManager.getMessage("tempmute-success", placeholders,
                            "<green>%player% muted for %duration%. Reason: %reason%");
                    sender.sendMessage(message);
                } else {
                    Component message = ConfigManager.getMessage("mute-success", placeholders,
                            "<green>%player% has been permanently muted. Reason: %reason%");
                    sender.sendMessage(message);
                }
            }

            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                Map<String, String> mutePlaceholders = new HashMap<>();
                mutePlaceholders.put("reason", reason);

                if (expiresAt != null) {
                    mutePlaceholders.put("duration", ExpireTimeManager.formatDurationChat(Duration.between(ExpireTimeManager.now(), expiresAt)));
                    Component muteMessage = ConfigManager.getMessage("chat-tempmute", mutePlaceholders,
                            "<yellow>You are temporarily muted for %duration%. Reason: %reason%");
                    target.sendMessage(muteMessage);
                } else {
                    Component muteMessage = ConfigManager.getMessage("chat-muted", mutePlaceholders,
                            "<yellow>You are permanently muted! Reason: %reason%");
                    target.sendMessage(muteMessage);
                }
            }
        });
    }

    // ========================================
    // OTHER PUNISHMENT METHODS
    // ========================================

    /**
     * Kicks a player from the server.
     * 
     * @param sender The command sender issuing the kick
     * @param player The player to kick
     * @param reason The reason for the kick
     * @return CompletableFuture that completes when the kick is applied
     */
    public CompletableFuture<Void> kick(CommandSender sender, Player player, String reason) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        Punishment p = new Punishment(0, uuid, playerName, reason, sender.getName(), Punishment.Type.KICK, ExpireTimeManager.now(), null, false);

        return db.savePunishment(p).thenRun(() -> {
            runOnMainThread(() -> {
                if (sender != null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", playerName);
                    placeholders.put("reason", reason);

                    Component message = ConfigManager.getMessage("kick-success", placeholders,
                            "<green>%player% has been kicked. Reason: %reason%");
                    sender.sendMessage(message);
                }

                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    Map<String, String> kickPlaceholders = new HashMap<>();
                    kickPlaceholders.put("reason", reason);

                    Component kickMessage = ConfigManager.getMessage("player-get-kicked", kickPlaceholders,
                            "<red>You have been kicked from the server! Reason: %reason%");
                    target.kick(kickMessage);
                }
            });
        });
    }

    /**
     * IP bans a player.
     * 
     * @param sender The command sender issuing the IP ban
     * @param player The player to IP ban
     * @param reason The reason for the IP ban
     * @return CompletableFuture that completes when the IP ban is applied
     */
    public CompletableFuture<Void> ipBan(CommandSender sender, OfflinePlayer player, String reason) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        return removeConflictingPunishments(uuid, Punishment.Type.IP_BAN)
                .thenCompose(v -> {
                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    String ipAddress = null;

                    // Try to get IP from online player
                    if (onlinePlayer != null && onlinePlayer.getAddress() != null) {
                        ipAddress = onlinePlayer.getAddress().getAddress().getHostAddress();
                    }

                    // If player is offline, we could look up their last known IP from database
                    // This would require storing IP addresses on every join

                    final String finalIpAddress = ipAddress;

                    Punishment ipBanPunishment = new Punishment(0, uuid, playerName, reason,
                            sender.getName(), Punishment.Type.IP_BAN, ExpireTimeManager.now(), null, true, finalIpAddress);

                    return (ipAddress != null ?
                            db.savePunishmentWithIP(ipBanPunishment, ipAddress) :
                            db.savePunishment(ipBanPunishment))
                            .thenRun(() -> {
                                runOnMainThread(() -> {
                                    if (sender != null) {
                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put("player", playerName);
                                        placeholders.put("reason", reason);

                                        Component message = ConfigManager.getMessage("ipban-success", placeholders,
                                                "<green>%player% has been IP-banned. Reason: %reason%");
                                        sender.sendMessage(message);
                                    }

                                    if (onlinePlayer != null) {
                                        Map<String, String> kickPlaceholders = new HashMap<>();
                                        kickPlaceholders.put("reason", reason);

                                        Component kickMessage = ConfigManager.getMessage("join-ipban", kickPlaceholders,
                                                "<dark_red>You are IP banned! This ban affects all accounts from your IP. Reason: %reason%");
                                        onlinePlayer.kick(kickMessage);
                                    }
                                });
                            });
                });
    }

    // ========================================
    // REMOVAL METHODS
    // ========================================

    /**
     * Unbans a player (removes all active bans).
     * 
     * @param sender The command sender issuing the unban
     * @param player The player to unban
     * @return CompletableFuture that completes when the unban is applied
     */
    public CompletableFuture<Void> unban(CommandSender sender, OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String source = sender != null ? sender.getName() : "Console";

        CompletableFuture<Punishment> banCheck = getActiveByUUID(uuid, Punishment.Type.BAN);
        CompletableFuture<Punishment> tempBanCheck = getActiveByUUID(uuid, Punishment.Type.TEMP_BAN);
        CompletableFuture<Punishment> ipBanCheck = getActiveByUUID(uuid, Punishment.Type.IP_BAN);

        return CompletableFuture.allOf(banCheck, tempBanCheck, ipBanCheck)
                .thenCompose(v -> {
                    Punishment ban = banCheck.join();
                    Punishment tempBan = tempBanCheck.join();
                    Punishment ipBan = ipBanCheck.join();

                    if (ban == null && tempBan == null && ipBan == null) {
                        if (sender != null) {
                            runOnMainThread(() -> {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("player", playerName);
                                Component message = ConfigManager.getMessage("not-banned", placeholders,
                                        "<red>%player% is not banned!");
                                sender.sendMessage(message);
                            });
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    CompletableFuture<Void> removeBan = ban != null ? removePunishment(ban, source) : CompletableFuture.completedFuture(null);
                    CompletableFuture<Void> removeTempBan = tempBan != null ? removePunishment(tempBan, source) : CompletableFuture.completedFuture(null);
                    CompletableFuture<Void> removeIpBan = ipBan != null ? removePunishment(ipBan, source) : CompletableFuture.completedFuture(null);

                    return CompletableFuture.allOf(removeBan, removeTempBan, removeIpBan)
                            .thenRun(() -> {
                                runOnMainThread(() -> {
                                    if (sender != null) {
                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put("player", playerName);

                                        Component message = ConfigManager.getMessage("unban-success", placeholders,
                                                "<green>%player% has been unbanned.");
                                        sender.sendMessage(message);
                                    }
                                });
                            });
                });
    }

    /**
     * Unmutes a player.
     * 
     * @param sender The command sender issuing the unmute
     * @param player The player to unmute
     * @return CompletableFuture that completes when the unmute is applied
     */
    public CompletableFuture<Void> unmute(CommandSender sender, OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String source = sender != null ? sender.getName() : "Console";

        // Check both MUTE and TEMP_MUTE types in database directly
        CompletableFuture<Punishment> muteCheck = db.getActiveByUUID(uuid, Punishment.Type.MUTE);
        CompletableFuture<Punishment> tempMuteCheck = db.getActiveByUUID(uuid, Punishment.Type.TEMP_MUTE);

        return CompletableFuture.allOf(muteCheck, tempMuteCheck)
                .thenCompose(v -> {
                    Punishment mute = muteCheck.join();
                    Punishment tempMute = tempMuteCheck.join();

                    // Check if either punishment exists and is active
                    Punishment activeMute = null;
                    if (mute != null && mute.isActive() &&
                            (mute.getExpiresAt() == null || mute.getExpiresAt().isAfter(ExpireTimeManager.now()))) {
                        activeMute = mute;
                    } else if (tempMute != null && tempMute.isActive() &&
                            (tempMute.getExpiresAt() == null || tempMute.getExpiresAt().isAfter(ExpireTimeManager.now()))) {
                        activeMute = tempMute;
                    }

                    if (activeMute == null) {
                        if (sender != null) {
                            runOnMainThread(() -> {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("player", playerName);
                                Component message = ConfigManager.getMessage("not-muted", placeholders,
                                        "<red>%player% is not muted!");
                                sender.sendMessage(message);
                            });
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    final Punishment punishmentToRemove = activeMute;

                    // Remove from database first, then cache
                    return db.removePunishment(punishmentToRemove.getId(), source)
                            .thenRun(() -> {
                                // Clear cache after successful database removal
                                cache.removeMutePunishment(uuid);

                                runOnMainThread(() -> {
                                    if (sender != null) {
                                        Map<String, String> placeholders = new HashMap<>();
                                        placeholders.put("player", playerName);

                                        Component message = ConfigManager.getMessage("unmute-success", placeholders,
                                                "<green>%player% has been unmuted.");
                                        sender.sendMessage(message);
                                    }

                                    Player target = Bukkit.getPlayer(uuid);
                                    if (target != null) {
                                        Component unmuteMessage = ConfigManager.getMessage("player-unmuted",
                                                Map.of("player", "You"), "<green>You have been unmuted.");
                                        target.sendMessage(unmuteMessage);
                                    }
                                });
                            });
                });
    }

    // ========================================
    // CLEANUP METHODS
    // ========================================

    /**
     * Shuts down the punishment manager and cleans up resources.
     */
    public void shutdown() {
        if (cache != null) {
            cache.shutdown();
        }
    }
}
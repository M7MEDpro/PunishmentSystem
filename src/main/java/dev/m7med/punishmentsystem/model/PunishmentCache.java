package dev.m7med.punishmentsystem.model;

import dev.m7med.punishmentsystem.PunishmentSystem;
import dev.m7med.punishmentsystem.database.DatabaseManager;
import dev.m7med.punishmentsystem.mangers.ExpireTimeManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache manager for storing and managing mute punishments in memory.
 * 
 * This class is responsible for:
 * - Caching mute punishments for fast access
 * - Managing cache cleanup and expiration
 * - Loading player mute data from database
 * - Providing thread-safe access to cached punishments
 * - Automatic cleanup of expired punishments
 */
public class PunishmentCache {
    
    // ========================================
    // FIELDS
    // ========================================
    
    private final DatabaseManager db;
    private final PunishmentSystem plugin = PunishmentSystem.getInstance();

    // Cache only for mute punishments (both MUTE and TEMP_MUTE)
    private static final ConcurrentHashMap<UUID, Punishment> muteCache = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    // ========================================
    // CONSTRUCTOR
    // ========================================

    /**
     * Creates a new PunishmentCache with the specified database manager.
     * 
     * @param db The database manager to use for persistence
     */
    public PunishmentCache(DatabaseManager db) {
        this.db = db;
        startCleanupTask();
    }

    // ========================================
    // CACHE MANAGEMENT METHODS
    // ========================================

    /**
     * Starts the cleanup task that removes expired punishments from cache.
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            List<UUID> toRemove = new ArrayList<>();
            
            for (Map.Entry<UUID, Punishment> entry : muteCache.entrySet()) {
                Punishment punishment = entry.getValue();
                
                // Check if punishment has expired
                if (ExpireTimeManager.isExpired(punishment.getExpiresAt())) {
                    toRemove.add(entry.getKey());
                    
                    // Update database to mark as expired
                    db.removePunishment(punishment.getId()).exceptionally(throwable -> {
                        plugin.getLogger().warning("Failed to update expired punishment in database: " + throwable.getMessage());
                        return null;
                    });
                }
            }
            
            // Remove expired punishments from cache
            for (UUID uuid : toRemove) {
                muteCache.remove(uuid);
            }
            
            if (!toRemove.isEmpty()) {
                plugin.getLogger().info("[CACHE] Removed " + toRemove.size() + " expired punishments from cache");
            }
        }, 20L * 60, 20L * 60); // Run every minute
    }

    /**
     * Loads mute punishments for a player from the database into cache.
     * 
     * @param playerUUID The UUID of the player
     * @return CompletableFuture that completes when loading is done
     */
    public CompletableFuture<Void> loadPlayerMutePunishments(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Check for permanent mute
                Punishment permanentMute = db.getActiveByUUID(playerUUID, Punishment.Type.MUTE).get();
                if (permanentMute != null && permanentMute.isActive()) {
                    muteCache.put(playerUUID, permanentMute);
                    return;
                }

                // Check for temporary mute
                Punishment tempMute = db.getActiveByUUID(playerUUID, Punishment.Type.TEMP_MUTE).get();
                if (tempMute != null && tempMute.isActive() && !ExpireTimeManager.isExpired(tempMute.getExpiresAt())) {
                    muteCache.put(playerUUID, tempMute);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading mute punishments for player " + playerUUID + ": " + e.getMessage());
            }
        });
    }

    /**
     * Gets the active mute punishment for a player.
     * 
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing the active mute punishment, or null if none found
     */
    public CompletableFuture<Punishment> getActiveMute(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Punishment punishment = muteCache.get(playerUUID);
            if (punishment != null) {
                // Check if punishment has expired
                if (ExpireTimeManager.isExpired(punishment.getExpiresAt())) {
                    muteCache.remove(playerUUID);
                    return null;
                }
                // Check if punishment is still active
                if (!punishment.isActive()) {
                    muteCache.remove(playerUUID);
                    return null;
                }
                return punishment;
            }
            return null;
        });
    }

    /**
     * Adds a mute punishment to the cache.
     * 
     * @param playerUUID The UUID of the player
     * @param punishment The punishment to add
     */
    public void addMutePunishment(UUID playerUUID, Punishment punishment) {
        if (punishment.getType() == Punishment.Type.MUTE ||
                punishment.getType() == Punishment.Type.TEMP_MUTE) {
            muteCache.put(playerUUID, punishment);
        }
    }

    /**
     * Removes a player from the cache (used on quit).
     * 
     * @param playerUUID The UUID of the player to remove
     */
    public void removePlayer(UUID playerUUID) {
        muteCache.remove(playerUUID);
    }

    /**
     * Removes a mute punishment from the cache.
     * 
     * @param playerUUID The UUID of the player
     */
    public void removeMutePunishment(UUID playerUUID) {
        muteCache.remove(playerUUID);
        // Log for debugging
        plugin.getLogger().info("[CACHE] Removed mute for player: " + playerUUID);
    }

    /**
     * Gets the active mute punishment for a player (synchronous version).
     * 
     * @param playerUUID The UUID of the player
     * @return The active mute punishment, or null if none found
     */
    public Punishment getActiveMutePunishment(UUID playerUUID) {
        Punishment punishment = muteCache.get(playerUUID);
        if (punishment != null) {
            // Check if punishment has expired
            if (ExpireTimeManager.isExpired(punishment.getExpiresAt())) {
                muteCache.remove(playerUUID);
                return null;
            }
            // Check if punishment is still active
            if (!punishment.isActive()) {
                muteCache.remove(playerUUID);
                return null;
            }
            return punishment;
        }
        return null;
    }

    // ========================================
    // CLEANUP METHODS
    // ========================================

    /**
     * Shuts down the cache and cleans up resources.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        muteCache.clear();
    }
}
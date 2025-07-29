
// ===== FIXED ChatListener.java - Complete rewrite =====
package dev.m7med.punishmentsystem.listener;

import dev.m7med.punishmentsystem.PunishmentSystem;
import dev.m7med.punishmentsystem.mangers.ConfigManager;
import dev.m7med.punishmentsystem.mangers.ExpireTimeManager;
import dev.m7med.punishmentsystem.model.Punishment;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

/**
 * Chat Listener - Mute Enforcement System
 * 
 * This class handles all chat-related events and enforces mute punishments in real-time.
 * It intercepts player chat messages and checks if the player is currently muted before
 * allowing them to send messages.
 * 
 * Key Features:
 * - Real-time mute enforcement during chat events
 * - Automatic expiration checking and cleanup
 * - Proper error handling and logging
 * - Asynchronous database operations
 * - User-friendly mute messages with remaining time
 * 
 * Event Handling:
 * - Listens to AsyncPlayerChatEvent with HIGHEST priority
 * - Cancels chat events for muted players
 * - Sends appropriate mute messages to players
 * - Updates expired punishments automatically
 * 
 * @author M7med
 * @version 1.0
 */
public class ChatListener implements Listener {

    // ========================================
    // FIELDS
    // ========================================
    
    /** MiniMessage instance for formatting chat messages */
    private final MiniMessage mm = MiniMessage.miniMessage();
    
    /** Main plugin instance for accessing managers and logging */
    private final PunishmentSystem plugin = PunishmentSystem.getInstance();
    
    /** Cache instance for fast mute lookups */
    private final dev.m7med.punishmentsystem.model.PunishmentCache cache = plugin.getCache();

    // ========================================
    // EVENT HANDLERS
    // ========================================

    /**
     * Handles chat events and enforces mute punishments.
     * 
     * This method is called whenever a player attempts to send a chat message.
     * It performs the following operations:
     * 
     * 1. Gets the player's UUID from the chat event
     * 2. Checks the cache for any active mute punishments
     * 3. If a mute is found, verifies it hasn't expired
     * 4. If expired, removes it from cache and database
     * 5. If active, cancels the chat event and sends a mute message
     * 6. Handles any errors that occur during the process
     * 
     * The method uses HIGHEST priority to ensure it runs before other chat plugins
     * and can properly cancel the event if needed.
     * 
     * @param event The chat event containing player and message information
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        // Get the player's UUID for cache lookup
        UUID uuid = event.getPlayer().getUniqueId();

        // Synchronously check cache for active mute punishments
        Punishment mute = cache.getActiveMutePunishment(uuid);
        if (mute != null && mute.isActive()) {
            // Check if punishment has expired
            if (ExpireTimeManager.isExpired(mute.getExpiresAt())) {
                // Punishment has expired, update database and remove from cache
                cache.removeMutePunishment(uuid);
                plugin.getDatabaseManager().removePunishment(mute.getId())
                        .exceptionally(throwable -> {
                            plugin.getLogger().warning("Failed to update expired punishment in database: " + throwable.getMessage());
                            return null;
                        });
                return;
            }

            // Cancel the chat event and send mute message
            event.setCancelled(true);

            // Format duration for display
            String duration = ExpireTimeManager.isPermanent(mute.getExpiresAt()) ?
                    "Permanent" :
                    ExpireTimeManager.formatDurationChat(ExpireTimeManager.getRemainingTime(mute.getExpiresAt()));

            // Determine message key based on punishment type
            String messageKey = mute.getType() == Punishment.Type.TEMP_MUTE ?
                    "chat-tempmute" : "chat-muted";

            // Create and send the mute message
            String msg = ConfigManager.msg(messageKey)
                    .replace("%reason%", mute.getReason())
                    .replace("%duration%", duration);

            event.getPlayer().sendMessage(mm.deserialize(msg));
        }
    }
}
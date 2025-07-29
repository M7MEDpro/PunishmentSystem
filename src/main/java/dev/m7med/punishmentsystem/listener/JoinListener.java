
package dev.m7med.punishmentsystem.listener;

import dev.m7med.punishmentsystem.PunishmentSystem;
import dev.m7med.punishmentsystem.mangers.ConfigManager;
import dev.m7med.punishmentsystem.mangers.ExpireTimeManager;
import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.m7med.punishmentsystem.model.Punishment;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Join Listener - Ban Enforcement and Cache Management System
 * 
 * This class handles all player join/quit events and enforces ban punishments.
 * It intercepts player login attempts and checks for active bans before allowing
 * players to join the server.
 * 
 * Key Features:
 * - Pre-login ban checking with comprehensive ban type support
 * - IP ban enforcement for all accounts from banned IPs
 * - Automatic expiration checking and cleanup
 * - Cache management for player mute data
 * - Proper error handling and user-friendly kick messages
 * - Asynchronous database operations for performance
 * 
 * Event Handling:
 * - AsyncPlayerPreLoginEvent: Ban checking before player joins
 * - PlayerJoinEvent: Load player mute data into cache
 * - PlayerQuitEvent: Clean up player data from cache
 * 
 * Ban Priority (highest to lowest):
 * 1. IP Ban (by player UUID)
 * 2. IP Ban (by IP address)
 * 3. Permanent Ban
 * 4. Temporary Ban
 * 
 * @author M7med
 * @version 1.0
 */
public class JoinListener implements Listener {

    // ========================================
    // FIELDS
    // ========================================
    
    /** Punishment manager for handling all punishment operations */
    private final PunishmentManager punishmentManager = PunishmentSystem.getInstance().getPunishmentManager();

    // ========================================
    // EVENT HANDLERS
    // ========================================

    /**
     * Handles player pre-login events and checks for active bans.
     * 
     * This method is called before a player joins the server and performs
     * comprehensive ban checking. It checks all types of bans in order of priority:
     * 
     * 1. IP Ban (by player UUID) - highest priority
     * 2. IP Ban (by IP address) - affects all accounts from that IP
     * 3. Permanent Ban - standard player ban
     * 4. Temporary Ban - time-limited player ban
     * 
     * For each ban type, it:
     * - Checks if the punishment is active and not expired
     * - If expired, automatically removes it from the database
     * - If active, prevents login and sends appropriate kick message
     * 
     * The method uses HIGHEST priority to ensure it runs before other plugins
     * and can properly prevent login if needed.
     * 
     * @param event The pre-login event containing player and connection information
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        // Extract player information from the event
        UUID playerUUID = event.getUniqueId();
        String playerName = event.getName();
        String playerIP = event.getAddress().getHostAddress();

        try {
            // Check all types of bans for the player
            CompletableFuture<Void> banCheck = checkAllBans(playerUUID, playerName, playerIP, event);
            banCheck.join();

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", e.getMessage());
            Component errorMessage = ConfigManager.getMessage("data-loading-error", placeholders,
                    "An error occurred while loading your data. Please try again later.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, errorMessage);
        }
    }

    /**
     * Handles player join events and loads mute data.
     * 
     * This method is called when a player successfully joins the server.
     * It loads the player's mute data from the database into the cache
     * for fast access during chat events.
     * 
     * The method runs asynchronously to avoid blocking the main thread
     * and handles any errors that might occur during the loading process.
     * 
     * @param event The join event containing player information
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        punishmentManager.getCache().loadPlayerMutePunishments(playerUUID)
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }

    /**
     * Handles player quit events and cleans up cache.
     * 
     * This method is called when a player leaves the server.
     * It removes the player's data from the cache to free up memory
     * and prevent stale data from persisting.
     * 
     * @param event The quit event containing player information
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        punishmentManager.getCache().removePlayer(playerUUID);
    }

    // ========================================
    // PRIVATE METHODS
    // ========================================

    /**
     * Checks all types of bans for a player and handles them appropriately.
     * 
     * This method performs comprehensive ban checking by running multiple
     * database queries in parallel and then processing the results in order
     * of priority. It handles the following ban types:
     * 
     * - IP Ban (by player UUID): Highest priority, affects specific player
     * - IP Ban (by IP address): Affects all accounts from that IP
     * - Permanent Ban: Standard player ban
     * - Temporary Ban: Time-limited player ban
     * 
     * For each ban type, it checks if the punishment is active and not expired.
     * If a punishment has expired, it's automatically removed from the database.
     * If a punishment is active, the player is prevented from joining with an
     * appropriate kick message.
     * 
     * @param playerUUID The UUID of the player attempting to join
     * @param playerName The name of the player attempting to join
     * @param playerIP The IP address of the player attempting to join
     * @param event The pre-login event for sending kick messages
     * @return CompletableFuture that completes when all checks are done
     */
    private CompletableFuture<Void> checkAllBans(UUID playerUUID, String playerName, String playerIP, AsyncPlayerPreLoginEvent event) {
        // Run all ban checks in parallel for better performance
        CompletableFuture<Punishment> banCheck = punishmentManager.getActiveByUUID(playerUUID, Punishment.Type.BAN);
        CompletableFuture<Punishment> tempBanCheck = punishmentManager.getActiveByUUID(playerUUID, Punishment.Type.TEMP_BAN);
        CompletableFuture<Punishment> ipBanCheck = punishmentManager.getActiveByUUID(playerUUID, Punishment.Type.IP_BAN);
        CompletableFuture<Punishment> ipBanByAddressCheck = checkIPBanByAddress(playerIP);

        return CompletableFuture.allOf(banCheck, tempBanCheck, ipBanCheck, ipBanByAddressCheck)
                .thenRun(() -> {
                    // Check IP ban first (highest priority)
                    Punishment ipBan = ipBanCheck.join();
                    if (ipBan != null && isActivePunishment(ipBan)) {
                        handleIPBan(event, ipBan);
                        return;
                    }

                    // Check IP ban by address
                    Punishment ipBanByAddress = ipBanByAddressCheck.join();
                    if (ipBanByAddress != null && isActivePunishment(ipBanByAddress)) {
                        handleIPBan(event, ipBanByAddress);
                        return;
                    }

                    // Check permanent ban
                    Punishment ban = banCheck.join();
                    if (ban != null && isActivePunishment(ban)) {
                        handlePermanentBan(event, ban);
                        return;
                    }

                    // Check temporary ban
                    Punishment tempBan = tempBanCheck.join();
                    if (tempBan != null && isActivePunishment(tempBan)) {
                        handleTemporaryBan(event, tempBan);
                        return;
                    }
                });
    }

    /**
     * Checks for IP bans by address.
     * 
     * This method queries the database for any active IP bans associated
     * with the given IP address. It's used to enforce IP bans that affect
     * all accounts from a specific IP address, regardless of which player
     * originally received the IP ban.
     * 
     * @param ipAddress The IP address to check for bans
     * @return CompletableFuture containing the IP ban punishment, if any
     */
    private CompletableFuture<Punishment> checkIPBanByAddress(String ipAddress) {
        return punishmentManager.getDatabase().getActiveIPBanByAddress(ipAddress);
    }

    /**
     * Checks if a punishment is currently active and not expired.
     * 
     * This method validates a punishment by checking:
     * 1. If the punishment object is not null
     * 2. If the punishment is marked as active
     * 3. If the punishment has not expired
     * 
     * If a punishment is found to be expired, it's automatically removed
     * from the database and marked as expired by the system.
     * 
     * @param punishment The punishment to check
     * @return true if the punishment is active, false otherwise
     */
    private boolean isActivePunishment(Punishment punishment) {
        if (punishment == null || !punishment.isActive()) {
            return false;
        }

        if (ExpireTimeManager.isExpired(punishment.getExpiresAt())) {
            // Punishment has expired, remove it and allow login
            punishmentManager.removePunishment(punishment, "System").exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            });
            return false;
        }

        return true;
    }

    /**
     * Handles permanent ban kick messages.
     * 
     * This method creates and sends a kick message for permanent bans.
     * It uses the configured message format and includes the ban reason
     * in the message.
     * 
     * @param event The pre-login event for sending the kick message
     * @param ban The permanent ban punishment
     */
    private void handlePermanentBan(AsyncPlayerPreLoginEvent event, Punishment ban) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", ban.getReason());

        Component kickMessage = ConfigManager.getMessage("join-banned", placeholders,
                "<red>You are permanently banned! Reason: %reason%");

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
    }

    /**
     * Handles temporary ban kick messages.
     * 
     * This method creates and sends a kick message for temporary bans.
     * It includes the ban reason, remaining duration, and expiration date
     * in the message to provide comprehensive information to the player.
     * 
     * @param event The pre-login event for sending the kick message
     * @param tempBan The temporary ban punishment
     */
    private void handleTemporaryBan(AsyncPlayerPreLoginEvent event, Punishment tempBan) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", tempBan.getReason());
        
        // Calculate remaining time
        Duration remainingTime = ExpireTimeManager.getRemainingTime(tempBan.getExpiresAt());
        placeholders.put("duration", ExpireTimeManager.formatDurationChat(remainingTime));
        
        // Format expiration date
        placeholders.put("expire", ExpireTimeManager.formatInstant(tempBan.getExpiresAt()));

        Component kickMessage = ConfigManager.getMessage("join-tempban", placeholders,
                "<red>You are temporarily banned for %duration%. Reason: %reason% (Expires at: %expire%)");

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
    }

    /**
     * Handles IP ban kick messages.
     * 
     * This method creates and sends a kick message for IP bans.
     * It explains that the ban affects all accounts from the player's IP
     * address and includes the ban reason.
     * 
     * @param event The pre-login event for sending the kick message
     * @param ipBan The IP ban punishment
     */
    private void handleIPBan(AsyncPlayerPreLoginEvent event, Punishment ipBan) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reason", ipBan.getReason());

        Component kickMessage = ConfigManager.getMessage("join-ipban", placeholders,
                "<dark_red>You are IP banned! This ban affects all accounts from your IP. Reason: %reason%");

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
    }
}
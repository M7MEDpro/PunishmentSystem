package dev.m7med.punishmentsystem.database;

import dev.m7med.punishmentsystem.model.Punishment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Database Manager Interface - Core Database Operations Contract
 * 
 * This interface defines the contract for all database implementations in the punishment system.
 * It provides a unified API for storing, retrieving, and managing punishment data regardless
 * of the underlying database technology (MySQL, SQLite, MongoDB).
 * 
 * All methods return CompletableFuture to support asynchronous operations, ensuring
 * the main server thread is not blocked during database operations.
 * 
 * Key Responsibilities:
 * - Connection management (connect, disconnect)
 * - Database schema setup
 * - CRUD operations for punishments
 * - Active punishment queries
 * - IP ban management
 * - Punishment removal with tracking
 * 
 * @author M7med
 * @version 1.0
 */
public interface DatabaseManager {
    
    /**
     * Establishes a connection to the database.
     * This method should handle all connection setup, including authentication
     * and initial configuration. Called during plugin startup.
     */
    void connect();

    /**
     * Creates and sets up the database schema.
     * This method should create all necessary tables, indexes, and collections
     * required for the punishment system to function properly.
     * Called during plugin startup after connection is established.
     */
    void setupTables();

    /**
     * Closes the database connection and cleans up resources.
     * This method should properly close all connections, statements, and
     * other database resources to prevent memory leaks.
     * Called during plugin shutdown.
     */
    void disconnect();

    /**
     * Saves a new punishment to the database.
     * 
     * @param p The punishment object to save
     * @return CompletableFuture that completes when the punishment is successfully saved
     */
    CompletableFuture<Void> savePunishment(Punishment p);

    /**
     * Saves a punishment with a specific IP address.
     * Used primarily for IP bans where we need to store the IP address
     * separately from the punishment object.
     * 
     * @param p The punishment object to save
     * @param ipAddress The IP address to associate with the punishment
     * @return CompletableFuture that completes when the punishment is successfully saved
     */
    CompletableFuture<Void> savePunishmentWithIP(Punishment p, String ipAddress);

    /**
     * Retrieves all punishments for a specific player by their UUID.
     * Returns punishments in chronological order (newest first).
     * 
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing a list of all punishments for the player
     */
    CompletableFuture<List<Punishment>> getPunishmentsByUUID(UUID playerUUID);

    /**
     * Retrieves all punishments for a specific player by their name.
     * Returns punishments in chronological order (newest first).
     * Name matching should be case-insensitive.
     * 
     * @param playerName The name of the player
     * @return CompletableFuture containing a list of all punishments for the player
     */
    CompletableFuture<List<Punishment>> getPunishmentsByName(String playerName);

    /**
     * Retrieves the currently active punishment of a specific type for a player by UUID.
     * Only returns punishments that are marked as active and have not expired.
     * If an active punishment is found but has expired, it will be automatically
     * marked as expired in the database.
     * 
     * @param playerUUID The UUID of the player
     * @param type The type of punishment to look for (BAN, MUTE, etc.)
     * @return CompletableFuture containing the active punishment, or null if none found
     */
    CompletableFuture<Punishment> getActiveByUUID(UUID playerUUID, Punishment.Type type);

    /**
     * Retrieves the currently active punishment of a specific type for a player by name.
     * Only returns punishments that are marked as active and have not expired.
     * If an active punishment is found but has expired, it will be automatically
     * marked as expired in the database.
     * Name matching should be case-insensitive.
     * 
     * @param playerName The name of the player
     * @param type The type of punishment to look for (BAN, MUTE, etc.)
     * @return CompletableFuture containing the active punishment, or null if none found
     */
    CompletableFuture<Punishment> getActiveByName(String playerName, Punishment.Type type);
    
    /**
     * Retrieves all active IP bans for a specific IP address.
     * Used to check if an IP address is banned, regardless of which player
     * originally received the IP ban.
     * 
     * @param ipAddress The IP address to check
     * @return CompletableFuture containing a list of active IP bans for the address
     */
    CompletableFuture<List<Punishment>> getActiveIPBansByAddress(String ipAddress);
    
    /**
     * Retrieves the most recent active IP ban for a specific IP address.
     * Returns only the most recent IP ban if multiple exist for the same address.
     * 
     * @param ipAddress The IP address to check
     * @return CompletableFuture containing the most recent active IP ban, or null if none found
     */
    CompletableFuture<Punishment> getActiveIPBanByAddress(String ipAddress);
    
    /**
     * Marks a punishment as expired in the database.
     * This method is used when a punishment expires naturally (time-based).
     * The punishment is marked as inactive with "EXPIRED" as the deactivation reason.
     * 
     * @param id The unique identifier of the punishment to remove
     * @return CompletableFuture that completes when the punishment is successfully marked as expired
     */
    CompletableFuture<Void> removePunishment(int id);
    
    /**
     * Marks a punishment as removed by a specific source in the database.
     * This method is used when a punishment is manually removed by a player or console.
     * The punishment is marked as inactive with "REMOVED_BY_SOURCE" as the deactivation reason
     * and the source is recorded for audit purposes.
     * 
     * @param id The unique identifier of the punishment to remove
     * @param source The name of who removed the punishment (player name or "Console")
     * @return CompletableFuture that completes when the punishment is successfully marked as removed
     */
    CompletableFuture<Void> removePunishment(int id, String source);
}

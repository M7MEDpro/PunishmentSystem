package dev.m7med.punishmentsystem.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import dev.m7med.punishmentsystem.mangers.ConfigManager;
import dev.m7med.punishmentsystem.model.Punishment;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * MongoDB Database Manager Implementation
 * 
 * This class provides MongoDB-specific implementation of the DatabaseManager interface.
 * It handles all database operations for the punishment system using MongoDB as the backend.
 * 
 * Key Features:
 * - Uses MongoDB's document-based storage for flexible data structure
 * - Implements efficient indexing for fast queries
 * - Supports all punishment types and operations
 * - Handles deactivation tracking with reason and source
 * - Provides automatic expiration checking and cleanup
 * 
 * Database Structure:
 * - Collection: "punishments"
 * - Documents contain all punishment data including deactivation information
 * - Indexes on player_uuid, player_name, active, and ip_address for performance
 * - Compound index for efficient active punishment queries
 * 
 * @author M7med
 * @version 1.0
 */
public class MongoManager implements DatabaseManager {

    // ========================================
    // FIELDS
    // ========================================
    
    /** MongoDB connection URI from configuration */
    private final String uri;
    
    /** MongoDB database name from configuration */
    private final String databaseName;
    
    /** MongoDB client instance for database operations */
    private MongoClient client;
    
    /** MongoDB collection for storing punishment documents */
    private MongoCollection<Document> collection;
    
    /** Configuration file for database settings */
    FileConfiguration config = ConfigManager.getConfig();

    // ========================================
    // CONSTRUCTOR
    // ========================================

    /**
     * Creates a new MongoManager instance.
     * Reads MongoDB connection settings from the plugin configuration.
     */
    public MongoManager() {
        this.uri = config.getString("database.mongo.uri");
        this.databaseName = config.getString("database.mongo.database");
    }

    // ========================================
    // CONNECTION MANAGEMENT
    // ========================================

    /**
     * Establishes connection to MongoDB and sets up the collection.
     * Creates necessary indexes for optimal query performance.
     * 
     * This method:
     * - Creates MongoDB client connection
     * - Gets the punishments collection
     * - Creates indexes for fast lookups
     * - Sets up compound index for active punishment queries
     */
    @Override
    public void connect() {
        client = MongoClients.create(uri);
        MongoDatabase db = client.getDatabase(databaseName);
        collection = db.getCollection("punishments");

        // Create indexes for optimal query performance
        collection.createIndex(Indexes.ascending("player_uuid"));
        collection.createIndex(Indexes.ascending("player_name"));
        collection.createIndex(Indexes.ascending("active"));
        collection.createIndex(Indexes.ascending("ip_address"));

        // Compound index for efficient active punishment queries
        collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("player_uuid"),
                        Indexes.ascending("type"),
                        Indexes.ascending("active")
                )
        );
    }

    /**
     * MongoDB doesn't require table setup like SQL databases.
     * Collections are created automatically when first document is inserted.
     */
    @Override
    public void setupTables() {
        return;
    }

    /**
     * Closes the MongoDB client connection and cleans up resources.
     * Called during plugin shutdown to prevent resource leaks.
     */
    @Override
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    // ========================================
    // PUNISHMENT SAVING METHODS
    // ========================================

    /**
     * Saves a punishment to MongoDB as a document.
     * Converts the Punishment object to a MongoDB Document and inserts it.
     * 
     * @param p The punishment object to save
     * @return CompletableFuture that completes when the punishment is saved
     */
    @Override
    public CompletableFuture<Void> savePunishment(Punishment p) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document("player_uuid", p.getPlayerUUID().toString())
                    .append("player_name", p.getPlayerName())
                    .append("type", p.getType().name())
                    .append("reason", p.getReason())
                    .append("actor", p.getActor())
                    .append("issuedAt", p.getIssuedAt().toEpochMilli())
                    .append("expiresAt", p.getExpiresAt() == null ? null : p.getExpiresAt().toEpochMilli())
                    .append("active", p.isActive())
                    .append("ip_address", p.getIpAddress())
                    .append("deactivation_reason", p.getDeactivationReason().name())
                    .append("deactivation_source", p.getDeactivationSource())
                    .append("deactivated_at", p.getDeactivatedAt() == null ? null : p.getDeactivatedAt().toEpochMilli());
            collection.insertOne(doc);
        }, ForkJoinPool.commonPool());
    }

    /**
     * Saves a punishment with a specific IP address to MongoDB.
     * Used primarily for IP bans where the IP address needs to be stored separately.
     * 
     * @param p The punishment object to save
     * @param ipAddress The IP address to associate with the punishment
     * @return CompletableFuture that completes when the punishment is saved
     */
    @Override
    public CompletableFuture<Void> savePunishmentWithIP(Punishment p, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            Document doc = new Document("player_uuid", p.getPlayerUUID().toString())
                    .append("player_name", p.getPlayerName())
                    .append("type", p.getType().name())
                    .append("reason", p.getReason())
                    .append("actor", p.getActor())
                    .append("issuedAt", p.getIssuedAt().toEpochMilli())
                    .append("expiresAt", p.getExpiresAt() == null ? null : p.getExpiresAt().toEpochMilli())
                    .append("active", p.isActive())
                    .append("ip_address", ipAddress)
                    .append("deactivation_reason", p.getDeactivationReason().name())
                    .append("deactivation_source", p.getDeactivationSource())
                    .append("deactivated_at", p.getDeactivatedAt() == null ? null : p.getDeactivatedAt().toEpochMilli());
            collection.insertOne(doc);
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // PUNISHMENT RETRIEVAL METHODS
    // ========================================

    /**
     * Retrieves all punishments for a player by their UUID.
     * Returns punishments sorted by issue date (newest first).
     * 
     * @param playerUUID The UUID of the player
     * @return CompletableFuture containing list of all punishments for the player
     */
    @Override
    public CompletableFuture<List<Punishment>> getPunishmentsByUUID(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            collection.find(Filters.eq("player_uuid", playerUUID.toString()))
                    .sort(Sorts.descending("issuedAt"))
                    .forEach(doc -> results.add(documentToPunishment(doc)));
            return results;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves all punishments for a player by their name.
     * Uses case-insensitive regex matching for flexible name lookup.
     * Returns punishments sorted by issue date (newest first).
     * 
     * @param playerName The name of the player
     * @return CompletableFuture containing list of all punishments for the player
     */
    @Override
    public CompletableFuture<List<Punishment>> getPunishmentsByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            collection.find(Filters.regex("player_name", "^" + playerName + "$", "i"))
                    .sort(Sorts.descending("issuedAt"))
                    .forEach(doc -> results.add(documentToPunishment(doc)));
            return results;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves the active punishment of a specific type for a player by UUID.
     * Only returns punishments that are marked as active and have not expired.
     * If an active punishment is found but has expired, it will be automatically
     * marked as expired in the database.
     * 
     * @param playerUUID The UUID of the player
     * @param type The type of punishment to look for
     * @return CompletableFuture containing the active punishment, or null if none found
     */
    @Override
    public CompletableFuture<Punishment> getActiveByUUID(UUID playerUUID, Punishment.Type type) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = collection.find(
                            Filters.and(
                                    Filters.eq("player_uuid", playerUUID.toString()),
                                    Filters.eq("type", type.name()),
                                    Filters.eq("active", true)
                            ))
                    .sort(Sorts.descending("issuedAt"))
                    .first();
            if (doc == null) return null;

            Punishment p = documentToPunishment(doc);
            Instant exp = p.getExpiresAt();
            if (exp == null || exp.isAfter(Instant.now())) {
                return p;
            } else {
                removePunishment(p.getId());
                return null;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves the active punishment of a specific type for a player by name.
     * Uses case-insensitive regex matching and only returns active, non-expired punishments.
     * If an active punishment is found but has expired, it will be automatically
     * marked as expired in the database.
     * 
     * @param playerName The name of the player
     * @param type The type of punishment to look for
     * @return CompletableFuture containing the active punishment, or null if none found
     */
    @Override
    public CompletableFuture<Punishment> getActiveByName(String playerName, Punishment.Type type) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = collection.find(
                            Filters.and(
                                    Filters.regex("player_name", "^" + playerName + "$", "i"),
                                    Filters.eq("type", type.name()),
                                    Filters.eq("active", true)
                            ))
                    .sort(Sorts.descending("issuedAt"))
                    .first();
            if (doc == null) return null;

            Punishment p = documentToPunishment(doc);
            Instant exp = p.getExpiresAt();
            if (exp == null || exp.isAfter(Instant.now())) {
                return p;
            } else {
                removePunishment(p.getId());
                return null;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves the most recent active IP ban for a specific IP address.
     * Used to check if an IP address is banned, regardless of which player
     * originally received the IP ban.
     * 
     * @param ipAddress The IP address to check
     * @return CompletableFuture containing the most recent active IP ban, or null if none found
     */
    @Override
    public CompletableFuture<Punishment> getActiveIPBanByAddress(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = collection.find(
                            Filters.and(
                                    Filters.eq("ip_address", ipAddress),
                                    Filters.eq("type", "IP_BAN"),
                                    Filters.eq("active", true)
                            ))
                    .sort(Sorts.descending("issuedAt"))
                    .first();
            if (doc == null) return null;

            Punishment p = documentToPunishment(doc);
            Instant exp = p.getExpiresAt();
            if (exp == null || exp.isAfter(Instant.now())) {
                return p;
            } else {
                removePunishment(p.getId());
                return null;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves all active IP bans for a specific IP address.
     * Returns all active IP bans for the address, not just the most recent one.
     * 
     * @param ipAddress The IP address to check
     * @return CompletableFuture containing list of all active IP bans for the address
     */
    @Override
    public CompletableFuture<List<Punishment>> getActiveIPBansByAddress(String ipAddress) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> results = new ArrayList<>();
            collection.find(
                            Filters.and(
                                    Filters.eq("ip_address", ipAddress),
                                    Filters.eq("type", "IP_BAN"),
                                    Filters.eq("active", true)
                            ))
                    .sort(Sorts.descending("issuedAt"))
                    .forEach(doc -> {
                        Punishment p = documentToPunishment(doc);
                        Instant exp = p.getExpiresAt();
                        if (exp == null || exp.isAfter(Instant.now())) {
                            results.add(p);
                        } else {
                            // Remove expired punishment
                            removePunishment(p.getId());
                        }
                    });
            return results;
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // PUNISHMENT REMOVAL METHODS
    // ========================================

    /**
     * Marks a punishment as expired in the database.
     * Updates the document to set active=false, deactivation_reason="EXPIRED",
     * and sets the deactivated_at timestamp.
     * 
     * @param idHash The hash of the punishment's ObjectId
     * @return CompletableFuture that completes when the punishment is marked as expired
     */
    @Override
    public CompletableFuture<Void> removePunishment(int idHash) {
        return CompletableFuture.runAsync(() -> {
            collection.updateOne(
                    Filters.expr(new Document("$eq", List.of(new Document("$toInt", "$_id"), idHash))),
                    Updates.combine(
                        Updates.set("active", false),
                        Updates.set("deactivation_reason", "EXPIRED"),
                        Updates.set("deactivated_at", System.currentTimeMillis())
                    )
            );
        }, ForkJoinPool.commonPool());
    }

    /**
     * Marks a punishment as removed by a specific source in the database.
     * Updates the document to set active=false, deactivation_reason="REMOVED_BY_SOURCE",
     * sets the deactivation_source, and sets the deactivated_at timestamp.
     * 
     * @param idHash The hash of the punishment's ObjectId
     * @param source The name of who removed the punishment
     * @return CompletableFuture that completes when the punishment is marked as removed
     */
    @Override
    public CompletableFuture<Void> removePunishment(int idHash, String source) {
        return CompletableFuture.runAsync(() -> {
            collection.updateOne(
                    Filters.expr(new Document("$eq", List.of(new Document("$toInt", "$_id"), idHash))),
                    Updates.combine(
                        Updates.set("active", false),
                        Updates.set("deactivation_reason", "REMOVED_BY_SOURCE"),
                        Updates.set("deactivation_source", source),
                        Updates.set("deactivated_at", System.currentTimeMillis())
                    )
            );
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Converts a MongoDB Document to a Punishment object.
     * Handles all field mapping including the new deactivation fields.
     * Uses ObjectId hash as the punishment ID for consistency.
     * 
     * @param doc The MongoDB document to convert
     * @return The converted Punishment object
     */
    private Punishment documentToPunishment(Document doc) {
        ObjectId oid = doc.getObjectId("_id");
        int idHash = oid.hashCode();

        UUID playerUUID = UUID.fromString(doc.getString("player_uuid"));
        String playerName = doc.getString("player_name");
        String reason = doc.getString("reason");
        String actor = doc.getString("actor");
        Punishment.Type type = Punishment.Type.valueOf(doc.getString("type"));

        Instant issuedAt = Instant.ofEpochMilli(doc.getLong("issuedAt"));
        Long expMillis = doc.containsKey("expiresAt") && doc.get("expiresAt") != null
                ? doc.getLong("expiresAt")
                : null;
        Instant expiresAt = expMillis == null ? null : Instant.ofEpochMilli(expMillis);

        boolean active = doc.getBoolean("active", false);
        String ipAddress = doc.getString("ip_address");
        
        // Handle new deactivation fields
        String deactivationReasonStr = doc.getString("deactivation_reason");
        Punishment.DeactivationReason deactivationReason = deactivationReasonStr != null ? 
            Punishment.DeactivationReason.valueOf(deactivationReasonStr) : Punishment.DeactivationReason.ACTIVE;
        
        String deactivationSource = doc.getString("deactivation_source");
        
        Instant deactivatedAt = null;
        Long deactivatedAtMillis = doc.containsKey("deactivated_at") && doc.get("deactivated_at") != null
                ? doc.getLong("deactivated_at")
                : null;
        if (deactivatedAtMillis != null) {
            deactivatedAt = Instant.ofEpochMilli(deactivatedAtMillis);
        }

        return new Punishment(idHash, playerUUID, playerName, reason, actor, type, issuedAt, expiresAt, active, ipAddress, deactivationReason, deactivationSource, deactivatedAt);
    }
}
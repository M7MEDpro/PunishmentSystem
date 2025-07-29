package dev.m7med.punishmentsystem.database;

import dev.m7med.punishmentsystem.mangers.ConfigManager;
import dev.m7med.punishmentsystem.model.Punishment;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * SQLite Database Manager Implementation
 * 
 * This class provides SQLite-specific implementation of the DatabaseManager interface.
 * It handles all database operations for the punishment system using SQLite as the backend.
 * 
 * Key Features:
 * - Uses SQLite file-based database for simple deployment
 * - Implements efficient indexing for fast queries
 * - Supports all punishment types and operations
 * - Handles deactivation tracking with reason and source
 * - Provides automatic expiration checking and cleanup
 * - Uses prepared statements for security and performance
 * - Perfect for single-server setups or development environments
 * 
 * Database Structure:
 * - File: SQLite database file (configurable location)
 * - Table: "punishments" with all necessary columns
 * - Indexes on player_uuid, player_name, active, and ip_address for performance
 * - Uses INTEGER for boolean values (0 = false, 1 = true)
 * 
 * @author M7med
 * @version 1.0
 */
public class SQLiteManager implements DatabaseManager {
    
    // ========================================
    // FIELDS
    // ========================================
    
    /** SQLite database file URL */
    private final String url;
    
    /** Active database connection */
    private Connection connection;

    // ========================================
    // CONSTRUCTOR
    // ========================================

    /**
     * Creates a new SQLiteManager instance.
     * Reads SQLite database file path from the plugin configuration.
     */
    public SQLiteManager() {
        FileConfiguration cfg = ConfigManager.getConfig();
        this.url = "jdbc:sqlite:" + cfg.getString("database.sqlite.file");
    }

    // ========================================
    // CONNECTION MANAGEMENT
    // ========================================

    /**
     * Establishes connection to SQLite database.
     * Loads the SQLite JDBC driver and creates a connection to the database file.
     * Called during plugin startup.
     */
    @Override
    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates and sets up the SQLite database schema.
     * Creates the punishments table with all necessary columns and indexes
     * for optimal performance. Uses SQLite-specific data types.
     * 
     * Table Structure:
     * - id: Auto-incrementing primary key (INTEGER)
     * - player_uuid: Player's UUID (TEXT)
     * - player_name: Player's name (TEXT)
     * - type: Punishment type (TEXT)
     * - reason: Punishment reason (TEXT)
     * - actor: Who issued the punishment (TEXT)
     * - issuedAt: When punishment was issued (INTEGER)
     * - expiresAt: When punishment expires (INTEGER NULL)
     * - active: Whether punishment is active (INTEGER, 0/1)
     * - ip_address: Associated IP address (TEXT)
     * - deactivation_reason: Why punishment was deactivated (TEXT)
     * - deactivation_source: Who deactivated the punishment (TEXT)
     * - deactivated_at: When punishment was deactivated (INTEGER)
     */
    @Override
    public void setupTables() {
        try (Statement s = getConnection().createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS punishments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  player_uuid TEXT,
                  player_name TEXT,
                  type TEXT,
                  reason TEXT,
                  actor TEXT,
                  issuedAt INTEGER,
                  expiresAt INTEGER NULL,
                  active INTEGER,
                  ip_address TEXT,
                  deactivation_reason TEXT DEFAULT 'ACTIVE',
                  deactivation_source TEXT NULL,
                  deactivated_at INTEGER NULL
                );
            """);

            // Create indexes for optimal query performance
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_uuid ON punishments(player_uuid);");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_name ON punishments(player_name);");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_active ON punishments(active);");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ip_address ON punishments(ip_address);");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the SQLite connection and cleans up resources.
     * Called during plugin shutdown to prevent resource leaks.
     */
    @Override
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a valid database connection, creating a new one if necessary.
     * Handles connection pooling and reconnection automatically.
     * 
     * @return A valid database connection
     * @throws SQLException if connection cannot be established
     */
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    // ========================================
    // PUNISHMENT SAVING METHODS
    // ========================================

    /**
     * Saves a punishment to SQLite database.
     * Uses prepared statements to prevent SQL injection and ensure data integrity.
     * 
     * @param p The punishment object to save
     * @return CompletableFuture that completes when the punishment is saved
     */
    @Override
    public CompletableFuture<Void> savePunishment(Punishment p) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO punishments(player_uuid,player_name,type,reason,actor,issuedAt,expiresAt,active,ip_address,deactivation_reason,deactivation_source,deactivated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, p.getPlayerUUID().toString());
                ps.setString(2, p.getPlayerName());
                ps.setString(3, p.getType().name());
                ps.setString(4, p.getReason());
                ps.setString(5, p.getActor());
                ps.setLong(6, p.getIssuedAt().toEpochMilli());
                if (p.getExpiresAt() != null) ps.setLong(7, p.getExpiresAt().toEpochMilli());
                else ps.setNull(7, Types.BIGINT);
                ps.setInt(8, p.isActive() ? 1 : 0);
                ps.setString(9, p.getIpAddress());
                ps.setString(10, p.getDeactivationReason().name());
                ps.setString(11, p.getDeactivationSource());
                if (p.getDeactivatedAt() != null) ps.setLong(12, p.getDeactivatedAt().toEpochMilli());
                else ps.setNull(12, Types.BIGINT);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Saves a punishment with a specific IP address to SQLite database.
     * Used primarily for IP bans where the IP address needs to be stored separately.
     * 
     * @param p The punishment object to save
     * @param ipAddress The IP address to associate with the punishment
     * @return CompletableFuture that completes when the punishment is saved
     */
    @Override
    public CompletableFuture<Void> savePunishmentWithIP(Punishment p, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO punishments(player_uuid,player_name,type,reason,actor,issuedAt,expiresAt,active,ip_address,deactivation_reason,deactivation_source,deactivated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, p.getPlayerUUID().toString());
                ps.setString(2, p.getPlayerName());
                ps.setString(3, p.getType().name());
                ps.setString(4, p.getReason());
                ps.setString(5, p.getActor());
                ps.setLong(6, p.getIssuedAt().toEpochMilli());
                if (p.getExpiresAt() != null) ps.setLong(7, p.getExpiresAt().toEpochMilli());
                else ps.setNull(7, Types.BIGINT);
                ps.setInt(8, p.isActive() ? 1 : 0);
                ps.setString(9, ipAddress);
                ps.setString(10, p.getDeactivationReason().name());
                ps.setString(11, p.getDeactivationSource());
                if (p.getDeactivatedAt() != null) ps.setLong(12, p.getDeactivatedAt().toEpochMilli());
                else ps.setNull(12, Types.BIGINT);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
            List<Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY issuedAt DESC")) {
                ps.setString(1, playerUUID.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(fromResult(rs));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves all punishments for a player by their name.
     * Uses case-insensitive matching for flexible name lookup.
     * Returns punishments sorted by issue date (newest first).
     * 
     * @param playerName The name of the player
     * @return CompletableFuture containing list of all punishments for the player
     */
    @Override
    public CompletableFuture<List<Punishment>> getPunishmentsByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> list = new ArrayList<>();
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE LOWER(player_name) = LOWER(?) ORDER BY issuedAt DESC")) {
                ps.setString(1, playerName);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(fromResult(rs));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
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
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE player_uuid = ? AND type = ? AND active = 1 ORDER BY issuedAt DESC LIMIT 1")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, type.name());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Punishment p = fromResult(rs);
                    Instant exp = p.getExpiresAt();
                    if (exp == null || exp.isAfter(Instant.now())) return p;
                    else {
                        // Remove expired punishment
                        removePunishment(p.getId());
                        return null;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves the active punishment of a specific type for a player by name.
     * Uses case-insensitive matching and only returns active, non-expired punishments.
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
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE LOWER(player_name) = LOWER(?) AND type = ? AND active = 1 ORDER BY issuedAt DESC LIMIT 1")) {
                ps.setString(1, playerName);
                ps.setString(2, type.name());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Punishment p = fromResult(rs);
                    Instant exp = p.getExpiresAt();
                    if (exp == null || exp.isAfter(Instant.now())) return p;
                    else {
                        // Remove expired punishment
                        removePunishment(p.getId());
                        return null;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // PUNISHMENT REMOVAL METHODS
    // ========================================

    /**
     * Marks a punishment as expired in the database.
     * Updates the record to set active=0, deactivation_reason='EXPIRED',
     * and sets the deactivated_at timestamp.
     * 
     * @param id The unique identifier of the punishment to remove
     * @return CompletableFuture that completes when the punishment is marked as expired
     */
    @Override
    public CompletableFuture<Void> removePunishment(int id) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE punishments SET active = 0, deactivation_reason = 'EXPIRED', deactivated_at = ? WHERE id = ?")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setInt(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Marks a punishment as removed by a specific source in the database.
     * Updates the record to set active=0, deactivation_reason='REMOVED_BY_SOURCE',
     * sets the deactivation_source, and sets the deactivated_at timestamp.
     * 
     * @param id The unique identifier of the punishment to remove
     * @param source The name of who removed the punishment
     * @return CompletableFuture that completes when the punishment is marked as removed
     */
    @Override
    public CompletableFuture<Void> removePunishment(int id, String source) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE punishments SET active = 0, deactivation_reason = 'REMOVED_BY_SOURCE', deactivation_source = ?, deactivated_at = ? WHERE id = ?")) {
                ps.setString(1, source);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Converts a SQLite ResultSet to a Punishment object.
     * Handles all field mapping including the new deactivation fields.
     * Properly handles null values and type conversions for SQLite.
     * 
     * @param rs The SQLite ResultSet to convert
     * @return The converted Punishment object
     * @throws SQLException if there's an error reading from the ResultSet
     */
    private Punishment fromResult(ResultSet rs) throws SQLException {
        Instant expires = rs.getLong("expiresAt") == 0
                ? null
                : Instant.ofEpochMilli(rs.getLong("expiresAt"));
        String ipAddress = rs.getString("ip_address");
        
        // Handle new deactivation fields
        String deactivationReasonStr = rs.getString("deactivation_reason");
        Punishment.DeactivationReason deactivationReason = deactivationReasonStr != null ? 
            Punishment.DeactivationReason.valueOf(deactivationReasonStr) : Punishment.DeactivationReason.ACTIVE;
        
        String deactivationSource = rs.getString("deactivation_source");
        
        Instant deactivatedAt = null;
        long deactivatedAtMillis = rs.getLong("deactivated_at");
        if (deactivatedAtMillis > 0) {
            deactivatedAt = Instant.ofEpochMilli(deactivatedAtMillis);
        }
        
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("reason"),
                rs.getString("actor"),
                Punishment.Type.valueOf(rs.getString("type")),
                Instant.ofEpochMilli(rs.getLong("issuedAt")),
                expires,
                rs.getInt("active") == 1,
                ipAddress,
                deactivationReason,
                deactivationSource,
                deactivatedAt
        );
    }

    // ========================================
    // IP BAN METHODS
    // ========================================

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
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE ip_address = ? AND type = 'IP_BAN' AND active = 1 ORDER BY issuedAt DESC LIMIT 1")) {
                ps.setString(1, ipAddress);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Punishment p = fromResult(rs);
                    Instant exp = p.getExpiresAt();
                    if (exp == null || exp.isAfter(Instant.now())) {
                        return p;
                    }
                    // Remove expired punishment
                    removePunishment(p.getId());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
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
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT * FROM punishments WHERE ip_address = ? AND type = 'IP_BAN' AND active = 1 ORDER BY issuedAt DESC")) {
                ps.setString(1, ipAddress);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Punishment p = fromResult(rs);
                    Instant exp = p.getExpiresAt();
                    if (exp == null || exp.isAfter(Instant.now())) {
                        results.add(p);
                    } else {
                        // Remove expired punishment
                        removePunishment(p.getId());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return results;
        }, ForkJoinPool.commonPool());
    }
}
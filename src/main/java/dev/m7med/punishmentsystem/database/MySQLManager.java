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
 * MySQL Database Manager Implementation
 * 
 * This class provides MySQL-specific implementation of the DatabaseManager interface.
 * It handles all database operations for the punishment system using MySQL as the backend.
 * 
 * Key Features:
 * - Uses MySQL relational database for structured data storage
 * - Implements efficient indexing for fast queries
 * - Supports all punishment types and operations
 * - Handles deactivation tracking with reason and source
 * - Provides automatic expiration checking and cleanup
 * - Uses connection pooling and prepared statements for security
 * 
 * Database Structure:
 * - Table: "punishments"
 * - Columns include all punishment data plus deactivation tracking fields
 * - Indexes on player_uuid, player_name, active, and ip_address for performance
 * - Uses InnoDB engine for transaction support
 * 
 * @author M7med
 * @version 1.0
 */
public class MySQLManager implements DatabaseManager {

    // ========================================
    // FIELDS
    // ========================================
    
    /** MySQL connection URL with all necessary parameters */
    private final String url;
    
    /** MySQL username for authentication */
    private final String user;
    
    /** MySQL password for authentication */
    private final String pass;
    
    /** Active database connection */
    private Connection connection;
    
    /** Configuration file for database settings */
    FileConfiguration cfg = ConfigManager.getConfig();

    // ========================================
    // CONSTRUCTOR
    // ========================================

    /**
     * Creates a new MySQLManager instance.
     * Reads MySQL connection settings from the plugin configuration and builds
     * the connection URL with proper parameters for Minecraft server environments.
     */
    public MySQLManager() {
        String host = cfg.getString("database.mysql.host");
        int port = cfg.getInt("database.mysql.port");
        String db = cfg.getString("database.mysql.database");
        this.user = cfg.getString("database.mysql.username");
        this.pass = cfg.getString("database.mysql.password");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";
    }

    // ========================================
    // CONNECTION MANAGEMENT
    // ========================================

    /**
     * Establishes connection to MySQL database.
     * Creates the initial connection using the configured credentials.
     * Called during plugin startup.
     */
    @Override
    public void connect() {
        try {
            connection = DriverManager.getConnection(url, user, pass);
        } catch( SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates and sets up the MySQL database schema.
     * Creates the punishments table with all necessary columns and indexes
     * for optimal performance. Uses InnoDB engine for transaction support.
     * 
     * Table Structure:
     * - id: Auto-incrementing primary key
     * - player_uuid: Player's UUID (VARCHAR(36))
     * - player_name: Player's name (VARCHAR(16))
     * - type: Punishment type (VARCHAR(20))
     * - reason: Punishment reason (TEXT)
     * - actor: Who issued the punishment (VARCHAR(36))
     * - issuedAt: When punishment was issued (BIGINT)
     * - expiresAt: When punishment expires (BIGINT NULL)
     * - active: Whether punishment is active (TINYINT(1))
     * - ip_address: Associated IP address (VARCHAR(45))
     * - deactivation_reason: Why punishment was deactivated (VARCHAR(20))
     * - deactivation_source: Who deactivated the punishment (VARCHAR(36))
     * - deactivated_at: When punishment was deactivated (BIGINT)
     */
    @Override
    public void setupTables() {
        try (Statement s = getConnection().createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS punishments (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  player_uuid VARCHAR(36),
                  player_name VARCHAR(16),
                  type VARCHAR(20),
                  reason TEXT,
                  actor VARCHAR(36),
                  issuedAt BIGINT,
                  expiresAt BIGINT NULL,
                  active TINYINT(1),
                  ip_address VARCHAR(45),
                  deactivation_reason VARCHAR(20) DEFAULT 'ACTIVE',
                  deactivation_source VARCHAR(36) NULL,
                  deactivated_at BIGINT NULL,
                  INDEX idx_player_uuid (player_uuid),
                  INDEX idx_player_name (player_name),
                  INDEX idx_active (active),
                  INDEX idx_ip_address (ip_address)
                ) ENGINE=InnoDB;
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the MySQL connection and cleans up resources.
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
            connection = DriverManager.getConnection(url, user, pass);
        }
        return connection;
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
                        removePunishment(p.getId());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return results;
        }, ForkJoinPool.commonPool());
    }

    // ========================================
    // PUNISHMENT SAVING METHODS
    // ========================================

    /**
     * Saves a punishment to MySQL database.
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
     * Saves a punishment with a specific IP address to MySQL database.
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
                    "SELECT * FROM punishments WHERE player_name = ? ORDER BY issuedAt DESC")) {
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
     * Only returns punishments that are marked as active and have not expired.
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
                    "SELECT * FROM punishments WHERE player_name = ? AND type = ? AND active = 1 ORDER BY issuedAt DESC LIMIT 1")) {
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
     * Converts a MySQL ResultSet to a Punishment object.
     * Handles all field mapping including the new deactivation fields.
     * Properly handles null values and type conversions.
     * 
     * @param rs The MySQL ResultSet to convert
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
}
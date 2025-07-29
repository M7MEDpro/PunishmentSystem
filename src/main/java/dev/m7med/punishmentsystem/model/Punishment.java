package dev.m7med.punishmentsystem.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Punishment Model - Core Data Structure for All Punishment Types
 * 
 * This class represents a punishment in the punishment system. It contains all
 * the information about a punishment including who was punished, why, when,
 * and how it was resolved. The class is immutable to ensure data integrity.
 * 
 * Key Features:
 * - Immutable design for thread safety and data integrity
 * - Support for all punishment types (BAN, MUTE, KICK, etc.)
 * - Comprehensive deactivation tracking
 * - IP address support for IP bans
 * - Timestamp tracking for all events
 * - UUID-based player identification
 * 
 * Punishment Types:
 * - BAN: Permanent ban preventing server access
 * - TEMP_BAN: Temporary ban with expiration time
 * - MUTE: Permanent mute preventing chat
 * - TEMP_MUTE: Temporary mute with expiration time
 * - IP_BAN: Ban affecting all accounts from an IP address
 * - KICK: One-time removal from server (not stored as active)
 * 
 * Deactivation Reasons:
 * - ACTIVE: Punishment is currently active
 * - EXPIRED: Punishment expired naturally due to time
 * - REMOVED_BY_SOURCE: Punishment was manually removed by someone
 * 
 * @author M7med
 * @version 1.0
 */
public class Punishment {
    
    // ========================================
    // ENUMS
    // ========================================
    
    /**
     * Enumeration of all possible punishment types.
     * Each type represents a different form of punishment with specific behavior.
     */
    public enum Type { 
        /** Permanent ban preventing server access */
        BAN, 
        /** Temporary ban with expiration time */
        TEMP_BAN, 
        /** Permanent mute preventing chat */
        MUTE, 
        /** Temporary mute with expiration time */
        TEMP_MUTE, 
        /** Ban affecting all accounts from an IP address */
        IP_BAN, 
        /** One-time removal from server (not stored as active) */
        KICK 
    }
    
    /**
     * Enumeration of reasons why a punishment was deactivated.
     * Used for tracking and audit purposes.
     */
    public enum DeactivationReason { 
        /** Punishment is currently active and in effect */
        ACTIVE, 
        /** Punishment expired naturally due to time limit */
        EXPIRED, 
        /** Punishment was manually removed by a player or console */
        REMOVED_BY_SOURCE 
    }

    // ========================================
    // FIELDS
    // ========================================
    
    /** Unique identifier for the punishment in the database */
    private final int id;
    
    /** UUID of the player who was punished */
    private final UUID playerUUID;
    
    /** Name of the player who was punished */
    private final String playerName;
    
    /** Reason given for the punishment */
    private final String reason;
    
    /** Name of who issued the punishment */
    private final String actor;
    
    /** Type of punishment applied */
    private final Type type;
    
    /** When the punishment was issued */
    private final Instant issuedAt;
    
    /** When the punishment expires (null for permanent) */
    private final Instant expiresAt;
    
    /** Whether the punishment is currently active */
    private final boolean active;
    
    /** IP address associated with the punishment (for IP bans) */
    private final String ipAddress;
    
    /** Reason why the punishment was deactivated */
    private final DeactivationReason deactivationReason;
    
    /** Who deactivated the punishment (if manually removed) */
    private final String deactivationSource;
    
    /** When the punishment was deactivated */
    private final Instant deactivatedAt;

    // ========================================
    // CONSTRUCTORS
    // ========================================

    /**
     * Creates a new punishment with IP address support.
     * 
     * This constructor is used for punishments that need to track an IP address,
     * such as IP bans. The deactivation reason is automatically set based on
     * the active status.
     * 
     * @param id Unique identifier for the punishment
     * @param playerUUID UUID of the punished player
     * @param playerName Name of the punished player
     * @param reason Reason for the punishment
     * @param actor Who issued the punishment
     * @param type Type of punishment
     * @param issuedAt When the punishment was issued
     * @param expiresAt When the punishment expires (null for permanent)
     * @param active Whether the punishment is active
     * @param ipAddress IP address associated with the punishment
     */
    public Punishment(int id, UUID playerUUID, String playerName, String reason, String actor,
                      Type type, Instant issuedAt, Instant expiresAt, boolean active, String ipAddress) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.reason = reason;
        this.actor = actor;
        this.type = type;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.ipAddress = ipAddress;
        this.deactivationReason = active ? DeactivationReason.ACTIVE : DeactivationReason.EXPIRED;
        this.deactivationSource = null;
        this.deactivatedAt = null;
    }

    /**
     * Creates a new punishment without IP address.
     * 
     * This constructor is used for standard punishments that don't need
     * IP address tracking. The deactivation reason is automatically set
     * based on the active status.
     * 
     * @param id Unique identifier for the punishment
     * @param playerUUID UUID of the punished player
     * @param playerName Name of the punished player
     * @param reason Reason for the punishment
     * @param actor Who issued the punishment
     * @param type Type of punishment
     * @param issuedAt When the punishment was issued
     * @param expiresAt When the punishment expires (null for permanent)
     * @param active Whether the punishment is active
     */
    public Punishment(int id, UUID playerUUID, String playerName, String reason, String actor,
                      Type type, Instant issuedAt, Instant expiresAt, boolean active) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.reason = reason;
        this.actor = actor;
        this.type = type;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.ipAddress = null;
        this.deactivationReason = active ? DeactivationReason.ACTIVE : DeactivationReason.EXPIRED;
        this.deactivationSource = null;
        this.deactivatedAt = null;
    }

    /**
     * Creates a new punishment with full deactivation tracking.
     * 
     * This constructor is used when creating punishments from database records
     * that include deactivation information. It allows for complete tracking
     * of how and when punishments were resolved.
     * 
     * @param id Unique identifier for the punishment
     * @param playerUUID UUID of the punished player
     * @param playerName Name of the punished player
     * @param reason Reason for the punishment
     * @param actor Who issued the punishment
     * @param type Type of punishment
     * @param issuedAt When the punishment was issued
     * @param expiresAt When the punishment expires (null for permanent)
     * @param active Whether the punishment is active
     * @param ipAddress IP address associated with the punishment
     * @param deactivationReason Why the punishment was deactivated
     * @param deactivationSource Who deactivated the punishment
     * @param deactivatedAt When the punishment was deactivated
     */
    public Punishment(int id, UUID playerUUID, String playerName, String reason, String actor,
                      Type type, Instant issuedAt, Instant expiresAt, boolean active, String ipAddress,
                      DeactivationReason deactivationReason, String deactivationSource, Instant deactivatedAt) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.reason = reason;
        this.actor = actor;
        this.type = type;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.ipAddress = ipAddress;
        this.deactivationReason = deactivationReason;
        this.deactivationSource = deactivationSource;
        this.deactivatedAt = deactivatedAt;
    }

    // ========================================
    // GETTER METHODS
    // ========================================

    /**
     * Gets the unique identifier for this punishment.
     * 
     * @return The punishment ID
     */
    public int getId() { return id; }
    
    /**
     * Gets the UUID of the punished player.
     * 
     * @return The player's UUID
     */
    public UUID getPlayerUUID() { return playerUUID; }
    
    /**
     * Gets the name of the punished player.
     * 
     * @return The player's name
     */
    public String getPlayerName() { return playerName; }
    
    /**
     * Gets the reason given for this punishment.
     * 
     * @return The punishment reason
     */
    public String getReason() { return reason; }
    
    /**
     * Gets the name of who issued this punishment.
     * 
     * @return The actor's name
     */
    public String getActor() { return actor; }
    
    /**
     * Gets the type of this punishment.
     * 
     * @return The punishment type
     */
    public Type getType() { return type; }
    
    /**
     * Gets when this punishment was issued.
     * 
     * @return The issue timestamp
     */
    public Instant getIssuedAt() { return issuedAt; }
    
    /**
     * Gets when this punishment expires.
     * 
     * @return The expiration timestamp, or null if permanent
     */
    public Instant getExpiresAt() { return expiresAt; }
    
    /**
     * Checks if this punishment is currently active.
     * 
     * @return true if the punishment is active, false otherwise
     */
    public boolean isActive() { return active; }
    
    /**
     * Gets the IP address associated with this punishment.
     * 
     * @return The IP address, or null if not applicable
     */
    public String getIpAddress() { return ipAddress; }
    
    /**
     * Gets the reason why this punishment was deactivated.
     * 
     * @return The deactivation reason
     */
    public DeactivationReason getDeactivationReason() { return deactivationReason; }
    
    /**
     * Gets who deactivated this punishment.
     * 
     * @return The deactivation source, or null if not applicable
     */
    public String getDeactivationSource() { return deactivationSource; }
    
    /**
     * Gets when this punishment was deactivated.
     * 
     * @return The deactivation timestamp, or null if not deactivated
     */
    public Instant getDeactivatedAt() { return deactivatedAt; }
}

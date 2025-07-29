package dev.m7med.punishmentsystem.mangers;

import dev.m7med.punishmentsystem.PunishmentSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/**
 * Configuration Manager - Centralized Configuration and Message Handling
 * 
 * This class provides centralized access to the plugin's configuration and
 * message system. It handles loading configuration values, formatting messages
 * with placeholders, and providing default values when configuration is missing.
 * 
 * Key Features:
 * - Centralized configuration access
 * - Message formatting with placeholder replacement
 * - MiniMessage support for rich text formatting
 * - Default value fallbacks for missing configuration
 * - Thread-safe configuration access
 * 
 * Configuration Structure:
 * - messages.*: All plugin messages with placeholders
 * - database.*: Database connection settings
 * - menu.*: GUI menu configuration
 * - General plugin settings
 * 
 * Message Placeholders:
 * - %player%: Player name
 * - %reason%: Punishment reason
 * - %duration%: Punishment duration
 * - %actor%: Who issued the punishment
 * - %date%: When punishment was issued
 * - %expires%: When punishment expires
 * - %status%: Active/Inactive status
 * - %deactivation_reason%: Why punishment was deactivated
 * - %deactivation_source%: Who deactivated the punishment
 * - %deactivation_date%: When punishment was deactivated
 * 
 * @author M7med
 * @version 1.0
 */
public class ConfigManager {
    
    // ========================================
    // FIELDS
    // ========================================
    
    /** Static configuration instance for the plugin */
    private static FileConfiguration config = PunishmentSystem.getInstance().getConfig();

    // ========================================
    // CONFIGURATION ACCESS METHODS
    // ========================================

    /**
     * Gets the plugin's configuration file.
     * 
     * This method provides access to the main configuration file where all
     * plugin settings, messages, and database configurations are stored.
     * The configuration is loaded from config.yml in the plugin's data folder.
     * 
     * @return The FileConfiguration object containing all plugin settings
     */
    public static FileConfiguration getConfig() {
        return config;
    }

    // ========================================
    // MESSAGE HANDLING METHODS
    // ========================================

    /**
     * Gets a formatted message with placeholder replacement.
     * 
     * This method retrieves a message from the configuration and replaces
     * placeholders with actual values. It supports MiniMessage formatting
     * for rich text display including colors, formatting, and hover effects.
     * 
     * The method looks for the message in the configuration at the path
     * "messages.{key}". If the message is not found, it uses the provided
     * default message instead.
     * 
     * Placeholder replacement is case-sensitive and uses the format %placeholder%.
     * All placeholders in the message are replaced with corresponding values
     * from the placeholders map.
     * 
     * @param key The configuration key for the message (e.g., "ban-success")
     * @param placeholders Map of placeholder names to replacement values
     * @param defaultMessage The default message to use if configuration is missing
     * @return A Component object with the formatted message
     * 
     * @example
     * Map<String, String> placeholders = new HashMap<>();
     * placeholders.put("player", "Steve");
     * placeholders.put("reason", "Griefing");
     * Component message = getMessage("ban-success", placeholders, 
     *     "<green>%player% has been banned. Reason: %reason%");
     * // Result: "<green>Steve has been banned. Reason: Griefing"
     */
    public static Component getMessage(String key, Map<String, String> placeholders, String defaultMessage) {
        // Get the raw message from configuration or use default
        String raw = config.getString("messages." + key, defaultMessage);

        // Replace all placeholders in the message
        for(Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        // Parse MiniMessage formatting and return as Component
        return MiniMessage.miniMessage().deserialize(raw);
    }

    /**
     * Gets a simple message string from configuration.
     * 
     * This method retrieves a message string from the configuration without
     * any placeholder replacement or formatting. It's useful for simple
     * text messages that don't require complex formatting.
     * 
     * The method looks for the message in the configuration at the path
     * "messages.{key}". If the message is not found, it returns an empty string.
     * 
     * @param key The configuration key for the message
     * @return The message string from configuration, or empty string if not found
     * 
     * @example
     * String message = msg("chat-muted");
     * // Returns the raw message string from config.yml
     */
    public static String msg(String key) {
        return config.getString("messages." + key, "");
    }
}

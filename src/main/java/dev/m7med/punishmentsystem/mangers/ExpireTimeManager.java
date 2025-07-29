package dev.m7med.punishmentsystem.mangers;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized manager for handling all time-related operations.
 * Provides consistent time parsing, formatting, and expiration logic across the entire punishment system.
 * 
 * This class handles:
 * - Time string parsing (e.g., "30m", "2h", "3d")
 * - Duration formatting for display
 * - Instant formatting for dates
 * - Expiration checking and calculations
 */
public class ExpireTimeManager {

    // ========================================
    // CONSTANTS AND PATTERNS
    // ========================================
    
    /** Regex pattern for parsing time strings like "30m", "2h", "3d" */
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|mo|month|months|y|yr|yrs|year|years)$",
            Pattern.CASE_INSENSITIVE
    );
    
    /** Standard date-time formatter for general display */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    /** Detailed date-time formatter for ban messages */
    private static final DateTimeFormatter DATE_TIME_DETAILED_FORMATTER = DateTimeFormatter.ofPattern("yyyy MM dd HH mm ss")
            .withZone(ZoneId.systemDefault());

    // ========================================
    // TIME PARSING METHODS
    // ========================================

    /**
     * Parses a time string into an Instant representing when something expires.
     * 
     * @param timeString The time string to parse (e.g., "30m", "2h", "3d", "1w", "4mo", "2year")
     * @return Instant representing when the time period expires
     * @throws IllegalArgumentException if the time string format is invalid
     */
    public static Instant parseExpireTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        String normalizedInput = timeString.trim().toLowerCase();
        Matcher matcher = TIME_PATTERN.matcher(normalizedInput);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid time format: '" + timeString + "'. " +
                            "Expected format: number + unit (e.g., '30m', '2h', '3d', '1w', '4mo', '2year')"
            );
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        Instant now = Instant.now();

        switch (unit) {
            case "m":
            case "min":
            case "mins":
            case "minute":
            case "minutes":
                return now.plus(amount, ChronoUnit.MINUTES);

            case "h":
            case "hr":
            case "hrs":
            case "hour":
            case "hours":
                return now.plus(amount, ChronoUnit.HOURS);

            case "d":
            case "day":
            case "days":
                return now.plus(amount, ChronoUnit.DAYS);

            case "w":
            case "week":
            case "weeks":
                return now.plus(amount * 7, ChronoUnit.DAYS);

            case "mo":
            case "month":
            case "months":
                return now.plus(amount * 30, ChronoUnit.DAYS);

            case "y":
            case "yr":
            case "yrs":
            case "year":
            case "years":
                return now.plus(amount * 365, ChronoUnit.DAYS);

            default:
                throw new IllegalArgumentException("Unsupported time unit: " + unit);
        }
    }

    // ========================================
    // INSTANT FORMATTING METHODS
    // ========================================

    /**
     * Formats an Instant to a readable date-time string.
     * 
     * @param instant The instant to format
     * @return Formatted date-time string (yyyy-MM-dd HH:mm:ss) or "Never" if null
     */
    public static String formatInstant(Instant instant) {
        if (instant == null) return "Never";
        return DATE_TIME_FORMATTER.format(instant);
    }
    
    /**
     * Formats an Instant to a detailed date-time string for ban messages.
     * 
     * @param instant The instant to format
     * @return Formatted date-time string (yyyy MM dd HH mm ss) or "Never" if null
     */
    public static String formatInstantDetailed(Instant instant) {
        if (instant == null) return "Never";
        return DATE_TIME_DETAILED_FORMATTER.format(instant);
    }

    // ========================================
    // DURATION FORMATTING METHODS
    // ========================================

    /**
     * Formats a Duration to a human-readable string.
     * Shows days, hours, minutes, and seconds as appropriate.
     * 
     * @param duration The duration to format
     * @return Human-readable duration string (e.g., "2 days, 3 hours, 15 minutes")
     */
    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "expired";
        }
        
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        StringBuilder sb = new StringBuilder();
        
        if (days > 0) {
            sb.append(days).append(" day").append(days != 1 ? "s" : "");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hours).append(" hour").append(hours != 1 ? "s" : "");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
        }
        if (seconds > 0 && days == 0 && hours == 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(seconds).append(" second").append(seconds != 1 ? "s" : "");
        }
        
        return sb.length() > 0 ? sb.toString() : "0 seconds";
    }
    
    /**
     * Formats a Duration to a simple human-readable string.
     * Shows only the largest time unit (days, hours, minutes, or seconds).
     * 
     * @param duration The duration to format
     * @return Simple human-readable duration string (e.g., "2 days", "3 hours")
     */
    public static String formatDurationSimple(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "expired";
        }
        
        long totalSeconds = duration.getSeconds();
        
        if (totalSeconds < 60) {
            return totalSeconds + " sec" + (totalSeconds != 1 ? "s" : "");
        }
        
        long totalMinutes = totalSeconds / 60;
        if (totalMinutes < 60) {
            return totalMinutes + " min" + (totalMinutes != 1 ? "s" : "");
        }
        
        long totalHours = totalMinutes / 60;
        if (totalHours < 24) {
            return totalHours + " hour" + (totalHours != 1 ? "s" : "");
        }
        
        long totalDays = totalHours / 24;
        return totalDays + " day" + (totalDays != 1 ? "s" : "");
    }
    
    /**
     * Formats a Duration to a compact string for chat messages.
     * Shows days, hours, and minutes in a compact format.
     * 
     * @param duration The duration to format
     * @return Compact duration string for chat messages (e.g., "2 days", "3 hours", "less than a minute")
     */
    public static String formatDurationChat(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "expired";
        }
        
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        
        if (days > 0) {
            return days + " day" + (days != 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return "less than a minute";
        }
    }

    // ========================================
    // EXPIRATION CHECKING METHODS
    // ========================================

    /**
     * Checks if a punishment has expired based on its expiration time.
     * 
     * @param expiresAt The expiration time to check
     * @return true if the punishment has expired, false otherwise
     */
    public static boolean isExpired(Instant expiresAt) {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    /**
     * Calculates the remaining time until expiration.
     * 
     * @param expiresAt The expiration time
     * @return Duration remaining until expiration, or null if already expired
     */
    public static Duration getRemainingTime(Instant expiresAt) {
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            return null;
        }
        return Duration.between(Instant.now(), expiresAt);
    }
    
    /**
     * Calculates the total duration of a punishment.
     * 
     * @param issuedAt When the punishment was issued
     * @param expiresAt When the punishment expires
     * @return Total duration of the punishment, or null if permanent
     */
    public static Duration getTotalDuration(Instant issuedAt, Instant expiresAt) {
        if (issuedAt == null || expiresAt == null) {
            return null;
        }
        return Duration.between(issuedAt, expiresAt);
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Gets the current timestamp as an Instant.
     * 
     * @return Current timestamp
     */
    public static Instant now() {
        return Instant.now();
    }
    
    /**
     * Checks if a duration represents a permanent punishment.
     * 
     * @param duration The duration to check
     * @return true if the duration is null (permanent), false otherwise
     */
    public static boolean isPermanent(Duration duration) {
        return duration == null;
    }
    
    /**
     * Checks if an expiration time represents a permanent punishment.
     * 
     * @param expiresAt The expiration time to check
     * @return true if the expiration time is null (permanent), false otherwise
     */
    public static boolean isPermanent(Instant expiresAt) {
        return expiresAt == null;
    }
}

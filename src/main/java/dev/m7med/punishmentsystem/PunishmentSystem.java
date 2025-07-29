package dev.m7med.punishmentsystem;

import dev.m7med.punishmentsystem.command.*;
import dev.m7med.punishmentsystem.database.DatabaseManager;
import dev.m7med.punishmentsystem.database.MongoManager;
import dev.m7med.punishmentsystem.database.MySQLManager;
import dev.m7med.punishmentsystem.database.SQLiteManager;
import dev.m7med.punishmentsystem.listener.ChatListener;
import dev.m7med.punishmentsystem.listener.GuiListener;
import dev.m7med.punishmentsystem.listener.JoinListener;
import dev.m7med.punishmentsystem.mangers.ConfigManager;
import dev.m7med.punishmentsystem.mangers.HistoryMenuManager;
import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.m7med.punishmentsystem.model.PunishmentCache;
import dev.velix.imperat.BukkitImperat;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for the PunishmentSystem.
 * 
 * This plugin provides comprehensive player punishment functionality including:
 * - Ban, IP Ban, Kick, Mute commands
 * - Temporary and permanent punishments
 * - Punishment history tracking
 * - Multiple database support (SQLite, MySQL, MongoDB)
 * - GUI-based punishment history viewer
 * 
 * @author m7med
 * @version 1.0
 */
public final class PunishmentSystem extends JavaPlugin {
    
    /** Command framework for handling plugin commands */
    private BukkitImperat imperat;
    
    /** Database manager for handling all database operations */
    private DatabaseManager db;
    
    /** Manager for handling punishment logic and operations */
    private PunishmentManager punishmentManager;
    
    /** Static instance of the plugin for global access */
    private static PunishmentSystem instance;
    
    /** Cache for storing punishment data in memory for faster access */
    private PunishmentCache cache;
    
    /** Manager for handling punishment history GUI menus */
    private HistoryMenuManager historyMenuManager;
    
    /**
     * Called when the plugin is enabled.
     * Initializes all components including database, managers, listeners, and commands.
     */
    @Override
    public void onEnable() {
        // Save default configuration file if it doesn't exist
        saveDefaultConfig();
        
        // Set the static instance for global access
        instance = this;
        
        // Initialize database based on configuration
        String type = ConfigManager.getConfig().getString("database.type", "SQLITE").toUpperCase();
        switch (type) {
            case "MYSQL": 
                db = new MySQLManager(); 
                break;
            case "MONGO": 
                db = new MongoManager(); 
                break;
            default:      
                db = new SQLiteManager(); // Default to SQLite for simplicity
        }
        
        // Connect to database and setup required tables
        db.connect();
        db.setupTables();
        
        // Initialize managers
        punishmentManager = new PunishmentManager(db);
        cache = new PunishmentCache(db);
        historyMenuManager = new HistoryMenuManager();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new JoinListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        
        // Initialize command framework with dependency injection
        imperat = BukkitImperat.builder(this)
                .dependencyResolver(PunishmentManager.class, () -> punishmentManager)
                .dependencyResolver(HistoryMenuManager.class, () -> historyMenuManager)
                .build();
        
        // Register all punishment commands
        imperat.registerCommand(new BanCommand());
        imperat.registerCommand(new IPBanCommand());
        imperat.registerCommand(new KickCommand());
        imperat.registerCommand(new MuteCommand());
        imperat.registerCommand(new TempBanCommand());
        imperat.registerCommand(new TempMuteCommand());
        imperat.registerCommand(new UnBanCommand());
        imperat.registerCommand(new UnMuteCommand());
        imperat.registerCommand(new HistoryGuiCommand());
    }

    /**
     * Called when the plugin is disabled.
     * Performs cleanup operations like disconnecting from database.
     */
    @Override
    public void onDisable() {
        // Disconnect from database to prevent data corruption
        if (db != null) {
            db.disconnect();
        }
    }
    
    /**
     * Gets the static instance of the plugin.
     * 
     * @return The PunishmentSystem instance
     */
    public static PunishmentSystem getInstance() {
        return instance;
    }

    /**
     * Gets the database manager instance.
     * 
     * @return The DatabaseManager for database operations
     */
    public DatabaseManager getDatabaseManager() {
        return db;
    }

    /**
     * Gets the punishment manager instance.
     * 
     * @return The PunishmentManager for punishment operations
     */
    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    /**
     * Gets the punishment cache instance.
     * 
     * @return The PunishmentCache for cached punishment data
     */
    public PunishmentCache getCache() {
        return cache;
    }
}

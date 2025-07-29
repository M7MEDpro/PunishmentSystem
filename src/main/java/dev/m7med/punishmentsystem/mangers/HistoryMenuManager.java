package dev.m7med.punishmentsystem.mangers;

import dev.m7med.punishmentsystem.PunishmentSystem;
import dev.m7med.punishmentsystem.model.Punishment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for handling the punishment history GUI.
 * Creates and displays a beautiful inventory interface showing a player's punishment history.
 * 
 * This class is responsible for:
 * - Building the history GUI inventory
 * - Formatting punishment information for display
 * - Handling active status checks
 * - Managing GUI item creation and placement
 */
public class HistoryMenuManager {
    
    // ========================================
    // FIELDS
    // ========================================
    
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final PunishmentSystem plugin = PunishmentSystem.getInstance();
    private final PunishmentManager service = PunishmentSystem.getInstance().getPunishmentManager();
    private final FileConfiguration config = ConfigManager.getConfig();

    // ========================================
    // PUBLIC METHODS
    // ========================================

    /**
     * Opens the punishment history menu for a specific player.
     * 
     * @param viewer The player viewing the history
     * @param target The name of the player whose history to display
     */
    public void openHistoryMenu(Player viewer, String target) {
        service.history(target).thenAccept(punishments -> {
            // Sort punishments by ID in descending order (newest first)
            punishments.sort(Comparator.comparing(Punishment::getId).reversed());

            // Check active status for all punishments
            List<CompletableFuture<Boolean>> activeChecks = new ArrayList<>();
            for (Punishment p : punishments) {
                if (p.isActive()) {
                    CompletableFuture<Boolean> activeCheck = checkIfPunishmentActive(p);
                    activeChecks.add(activeCheck);
                } else {
                    activeChecks.add(CompletableFuture.completedFuture(false));
                }
            }

            // Wait for all active checks to complete, then build and open inventory
            CompletableFuture.allOf(activeChecks.toArray(new CompletableFuture[0]))
                    .thenAccept(v -> {
                        List<Boolean> activeStatuses = new ArrayList<>();
                        for (CompletableFuture<Boolean> check : activeChecks) {
                            activeStatuses.add(check.join());
                        }

                        Inventory inv = buildInventory(target, punishments, activeStatuses);
                        Bukkit.getScheduler().runTask(plugin, () -> viewer.openInventory(inv));
                    });
        });
    }

    // ========================================
    // PRIVATE METHODS
    // ========================================

    /**
     * Checks if a punishment is currently active by verifying its status in the database.
     * 
     * @param punishment The punishment to check
     * @return CompletableFuture containing true if active, false otherwise
     */
    private CompletableFuture<Boolean> checkIfPunishmentActive(Punishment punishment) {
        if (!punishment.isActive()) {
            return CompletableFuture.completedFuture(false);
        }

        if (ExpireTimeManager.isExpired(punishment.getExpiresAt())) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return service.history(punishment.getPlayerName()).get().stream()
                        .filter(p -> p.getId() == punishment.getId())
                        .filter(p -> p.isActive())
                        .filter(p -> !ExpireTimeManager.isExpired(p.getExpiresAt()))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking punishment active status: " + e.getMessage());
                return null;
            }
        }).thenApply(foundPunishment -> foundPunishment != null);
    }

    /**
     * Builds the inventory for the punishment history menu.
     * 
     * @param target The name of the player whose history is being displayed
     * @param punishments The list of punishments to display
     * @param activeStatuses The list of active statuses corresponding to each punishment
     * @return The built inventory
     */
    private Inventory buildInventory(String target, List<Punishment> punishments, List<Boolean> activeStatuses) {
        // Create inventory with title
        String title = config.getString("menu.title", "Punishment History: %player%").replace("%player%", target);
        int size = config.getInt("menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, mm.deserialize(title));

        // Fill inventory with filler items
        fillInventoryWithFiller(inv, size);

        // Create a list that pairs punishments with their active status
        List<PunishmentWithStatus> punishmentStatusPairs = new ArrayList<>();
        for (int i = 0; i < punishments.size(); i++) {
            Punishment p = punishments.get(i);
            boolean isActive = i < activeStatuses.size() ? activeStatuses.get(i) : false;
            punishmentStatusPairs.add(new PunishmentWithStatus(p, isActive));
        }

        // Place punishment items sequentially from top-left to bottom-right
        placePunishmentItems(inv, punishmentStatusPairs, size);

        return inv;
    }

    /**
     * Fills the inventory with filler items (background).
     * 
     * @param inv The inventory to fill
     * @param size The size of the inventory
     */
    private void fillInventoryWithFiller(Inventory inv, int size) {
        String fillerMat = config.getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE");
        String fillerName = config.getString("menu.filler.name", " ");
        ItemStack filler = new ItemStack(Material.matchMaterial(fillerMat));
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(mm.deserialize(fillerName));
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler.clone());
        }
    }

    /**
     * Places punishment items in the inventory.
     * 
     * @param inv The inventory to place items in
     * @param punishmentStatusPairs The list of punishments with their status
     * @param size The size of the inventory
     */
    private void placePunishmentItems(Inventory inv, List<PunishmentWithStatus> punishmentStatusPairs, int size) {
        int currentSlot = 0;
        for (PunishmentWithStatus pair : punishmentStatusPairs) {
            if (currentSlot >= size) break; // No more slots available

            Punishment p = pair.punishment;
            boolean isCurrentlyActive = pair.isActive;

            String type = p.getType().name();
            if (!config.contains("menu.items." + type)) continue;

            // Find next available slot sequentially
            while (currentSlot < size && !isSlotAvailableForPunishment(inv, currentSlot)) {
                currentSlot++;
            }

            if (currentSlot >= size) break; // No more slots available

            // Create and place the punishment item
            ItemStack item = createPunishmentItem(p, isCurrentlyActive, type);
            inv.setItem(currentSlot, item);
            currentSlot++;
        }
    }

    /**
     * Creates an ItemStack representing a punishment for the GUI.
     * 
     * @param punishment The punishment to create an item for
     * @param isActive Whether the punishment is currently active
     * @param type The punishment type
     * @return The created ItemStack
     */
    private ItemStack createPunishmentItem(Punishment punishment, boolean isActive, String type) {
        String path = "menu.items." + type;
        String material = config.getString(path + ".material", "PAPER");
        String name = config.getString(path + ".name", "<white>" + type + " - %reason%");
        List<String> lore = config.getStringList(path + ".lore");

        Material mat = Material.matchMaterial(material);
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String formattedName = name
                    .replace("%reason%", punishment.getReason())
                    .replace("%status%", isActive ? "ACTIVE" : "INACTIVE")
                    .replace("%actor%", punishment.getActor() != null ? punishment.getActor() : "Unknown");

            // Process lore from config with placeholders
            List<Component> formattedLore = new ArrayList<>();
            for (String line : lore) {
                String formattedLine = replacePlaceholders(line, punishment, isActive);
                formattedLore.add(mm.deserialize(formattedLine));
            }

            meta.displayName(mm.deserialize(formattedName));
            meta.lore(formattedLore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Checks if a slot is available for placing a punishment item.
     * 
     * @param inv The inventory to check
     * @param slot The slot to check
     * @return true if the slot is available, false otherwise
     */
    private boolean isSlotAvailableForPunishment(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null) return true;

        String fillerMat = config.getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE");
        String fillerName = config.getString("menu.filler.name", " ");

        Material fillerMaterial = Material.matchMaterial(fillerMat);
        if (item.getType() == fillerMaterial) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                Component displayName = meta.displayName();
                Component fillerComponent = mm.deserialize(fillerName);
                return displayName.equals(fillerComponent);
            }
        }

        return false;
    }

    /**
     * Replaces placeholders in a string with actual punishment data.
     * 
     * @param text The text containing placeholders
     * @param punishment The punishment to get data from
     * @param isActive Whether the punishment is currently active
     * @return The text with placeholders replaced
     */
    private String replacePlaceholders(String text, Punishment punishment, boolean isActive) {
        // Determine deactivation information
        String deactivationInfo = "";
        if (!isActive) {
            switch (punishment.getDeactivationReason()) {
                case EXPIRED:
                    deactivationInfo = "Expired";
                    break;
                case REMOVED_BY_SOURCE:
                    deactivationInfo = "Removed by " + (punishment.getDeactivationSource() != null ? punishment.getDeactivationSource() : "Unknown");
                    break;
                default:
                    deactivationInfo = "Inactive";
            }
        }
        
        // Get deactivation date
        String deactivationDate = "";
        if (punishment.getDeactivatedAt() != null) {
            deactivationDate = ExpireTimeManager.formatInstant(punishment.getDeactivatedAt());
        }
        
        // Calculate durations
        Duration totalDuration = ExpireTimeManager.getTotalDuration(punishment.getIssuedAt(), punishment.getExpiresAt());
        Duration remainingTime = ExpireTimeManager.getRemainingTime(punishment.getExpiresAt());
        
        return text
                .replace("%reason%", punishment.getReason())
                .replace("%date%", ExpireTimeManager.formatInstant(punishment.getIssuedAt()))
                .replace("%expires%", ExpireTimeManager.formatInstant(punishment.getExpiresAt()))
                .replace("%actor%", punishment.getActor() != null ? punishment.getActor() : "Unknown")
                .replace("%status%", isActive ? "ACTIVE" : "INACTIVE")
                .replace("%id%", String.valueOf(punishment.getId()))
                .replace("%uuid%", punishment.getPlayerUUID().toString())
                .replace("%player%", punishment.getPlayerName())
                .replace("%ip%", punishment.getIpAddress() != null ? punishment.getIpAddress() : "N/A")
                .replace("%duration%", totalDuration != null ? ExpireTimeManager.formatDuration(totalDuration) : "Permanent")
                .replace("%remaining%", remainingTime != null ? ExpireTimeManager.formatDuration(remainingTime) : "Expired/Permanent")
                .replace("%deactivation_reason%", deactivationInfo)
                .replace("%deactivation_source%", punishment.getDeactivationSource() != null ? punishment.getDeactivationSource() : "N/A")
                .replace("%deactivation_date%", deactivationDate);
    }

    // ========================================
    // HELPER CLASSES
    // ========================================

    /**
     * Helper class to pair punishments with their active status.
     */
    private static class PunishmentWithStatus {
        final Punishment punishment;
        final boolean isActive;

        PunishmentWithStatus(Punishment punishment, boolean isActive) {
            this.punishment = punishment;
            this.isActive = isActive;
        }
    }
}
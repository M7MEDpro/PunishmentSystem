package dev.m7med.punishmentsystem.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * GUI Listener - Inventory Interaction Protection System
 * 
 * This class prevents players from clicking, moving, or interacting with items
 * in the punishment history GUI menu. It maintains the GUI's integrity and
 * prevents any item manipulation.
 * 
 * @author M7med
 * @version 1.0
 */
public class GuiListener implements Listener {

    /**
     * Handles inventory click events and prevents interaction with history menu.
     * 
     * This method cancels all inventory interactions when the inventory title
     * contains "Punishment History:" to prevent players from clicking, moving,
     * or picking up items in the punishment history GUI.
     * 
     * @param event The inventory click event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the inventory title contains "Punishment History:"
        String title = event.getView().getTitle();
        
        if (title.contains("Punishment History:")) {
            // Cancel the event to prevent any interaction
            event.setCancelled(true);
        }
    }
}

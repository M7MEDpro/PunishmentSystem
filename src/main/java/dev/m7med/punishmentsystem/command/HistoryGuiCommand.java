package dev.m7med.punishmentsystem.command;

import dev.m7med.punishmentsystem.mangers.HistoryMenuManager;
import dev.velix.imperat.annotations.Command;
import dev.velix.imperat.annotations.Dependency;
import dev.velix.imperat.annotations.Permission;
import dev.velix.imperat.annotations.Usage;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("history")
@Permission("punishmentsystem.history")

public class HistoryGuiCommand {

    @Dependency
    HistoryMenuManager historyMenuManager;


    @Usage
    public void usage(CommandSender sender, OfflinePlayer player) {
        if (!(sender instanceof Player)) return;
        historyMenuManager.openHistoryMenu((Player) sender,player.getName());
    }
}

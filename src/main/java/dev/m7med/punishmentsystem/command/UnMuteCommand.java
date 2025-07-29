package dev.m7med.punishmentsystem.command;

import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.velix.imperat.annotations.Command;
import dev.velix.imperat.annotations.Dependency;
import dev.velix.imperat.annotations.Permission;
import dev.velix.imperat.annotations.Usage;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Command("unmute")
@Permission("punishmentsystem.unmute")

public class UnMuteCommand {
    @Dependency
    PunishmentManager punishmentManager;
    @Usage
    public void unMute(CommandSender sender, OfflinePlayer player) {
        punishmentManager.unmute(sender, player);
    }
}

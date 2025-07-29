package dev.m7med.punishmentsystem.command;

import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.velix.imperat.annotations.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

@Command("mute")
@Permission("punishmentsystem.mute")

public class MuteCommand {

    @Dependency
    PunishmentManager punishmentManager;
    @Usage
    public void usage(CommandSender sender, @Named("target") OfflinePlayer player, @Named("reason")@Greedy String reason) {
        punishmentManager.mute(sender, player, reason);
    }
}

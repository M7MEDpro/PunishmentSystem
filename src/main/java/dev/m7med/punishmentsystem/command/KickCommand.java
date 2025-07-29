package dev.m7med.punishmentsystem.command;

import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.velix.imperat.annotations.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("kick")
@Permission("punishmentsystem.kick")
public class KickCommand {
    @Dependency
    PunishmentManager punishmentManager;

    @Usage
    public void kick(CommandSender sender,Player target, @Named("reason")@Greedy String reason) {
        punishmentManager.kick(sender, target, reason);
    }

}

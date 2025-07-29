package dev.m7med.punishmentsystem.command;

import dev.m7med.punishmentsystem.mangers.ExpireTimeManager;
import dev.m7med.punishmentsystem.mangers.PunishmentManager;
import dev.velix.imperat.annotations.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.time.Instant;

@Command("tban")
@Permission("punishmentsystem.tban")

public class TempBanCommand {
    @Dependency
    PunishmentManager punishmentManager;
    @Usage
    public void usage(CommandSender sender, @Named("target") OfflinePlayer player, @Named("expiresafter") String expire,@Named("reason")@Greedy String reason) {
       Instant expireInstant = ExpireTimeManager.parseExpireTime(expire);
        punishmentManager.tempBan(sender, player, reason,expireInstant);
    }
}

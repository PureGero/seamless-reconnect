package me.puregero.seamlessreconnect.kmeans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GetCpus extends Command {
    private final ServerAllocationSystem system;

    public GetCpus(ServerAllocationSystem system) {
        super("getcpus");

        this.system = system;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        for (Map.Entry<String, String> entries : system.cpus.entrySet()) {
            sender.sendMessage(Component.text(entries.getKey() + ": " + entries.getValue()).color(NamedTextColor.WHITE));
        }

        return true;
    }
}

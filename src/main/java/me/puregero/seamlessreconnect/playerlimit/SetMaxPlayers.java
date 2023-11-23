package me.puregero.seamlessreconnect.playerlimit;

import me.puregero.seamlessreconnect.SeamlessReconnect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.messaging.MessagingService;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

public class SetMaxPlayers extends Command implements Listener {
    private final static String PREFIX = "seamlessreconnect.maxplayers.";
    private final SeamlessReconnect plugin;

    public SetMaxPlayers(SeamlessReconnect plugin) {
        super("srsetmaxplayers");
        setPermission("seamlessreconnect.srsetmaxplayers");
        this.plugin = plugin;

        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin.getServer().getCommandMap().register("seamlessreconnect", this);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission("seamlessreconnect.srsetmaxplayers")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);

        if (provider == null) {
            sender.sendMessage(Component.text("LuckPerms not found.").color(NamedTextColor.RED));
            return false;
        }

        LuckPerms luckPerms = provider.getProvider();

        Group defaultGroup = luckPerms.getGroupManager().getGroup("default");

        if (defaultGroup == null) {
            sender.sendMessage(Component.text("Default luckperms group not found. (This shouldn't be possible??)").color(NamedTextColor.RED));
            return false;
        }

        int maxPlayers = defaultGroup.getNodes().stream()
                .filter(node -> node.getKey().startsWith(PREFIX))
                .map(node -> {
                    try {
                        return Integer.parseInt(node.getKey().substring(PREFIX.length()));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max(Integer::compareTo).orElse(0);

        if (args.length < 1) {
            sender.sendMessage(Component.text("The current player limit is " + maxPlayers).color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Usage: /srsetmaxplayers <method>").color(NamedTextColor.RED));
            return false;
        }

        try {
            Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number: " + args[0]).color(NamedTextColor.RED));
            return false;
        }

        luckPerms.getGroupManager().modifyGroup("default", group -> {
            group.data().toCollection().forEach(node -> {
                if (node.getKey().startsWith(PREFIX)) {
                    group.data().remove(node);
                }
            });
            group.data().add(Node.builder(PREFIX + args[0]).build());
        });

        luckPerms.getMessagingService().ifPresent(MessagingService::pushUpdate);

        Bukkit.broadcast(Component.text("[" + sender.getName() + "] Set max players to " + args[0]).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), "seamlessreconnect.srsetmaxplayers");
        sender.sendMessage(Component.text("Set max players to " + args[0]).color(NamedTextColor.GREEN));
        return true;
    }
}

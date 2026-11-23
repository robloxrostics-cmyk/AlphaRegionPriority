package ru.alphagrief.regionpriority;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class AlphaRegionPriority extends JavaPlugin {
    private final Map<UUID, Set<String>> knownRegions = new HashMap<>();
    private BukkitTask checkerTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            getLogger().severe("WorldGuard не найден! Плагин отключается.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        applyToAllWorlds();
        startChecker();
        getLogger().info("AlphaRegionPriority включён.");
    }

    @Override
    public void onDisable() {
        if (checkerTask != null) checkerTask.cancel();
        knownRegions.clear();
    }

    private void startChecker() {
        if (checkerTask != null) checkerTask.cancel();
        long interval = Math.max(1L, getConfig().getLong("settings.check-interval-ticks", 20L));
        checkerTask = Bukkit.getScheduler().runTaskTimer(this, this::checkNewRegions, interval, interval);
    }

    private void applyToAllWorlds() {
        for (World world : Bukkit.getWorlds()) applyToWorld(world);
    }

    private void applyToWorld(World world) {
        RegionManager manager = getRegionManager(world);
        if (manager == null) return;
        Set<String> ids = new HashSet<>();
        boolean changed = false;
        for (ProtectedRegion region : manager.getRegions().values()) {
            ids.add(region.getId());
            if (isGlobal(region)) continue;
            applySettings(region);
            changed = true;
        }
        knownRegions.put(world.getUID(), ids);
        if (changed) saveManager(manager, world);
    }

    private void checkNewRegions() {
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = getRegionManager(world);
            if (manager == null) continue;
            Set<String> previous = knownRegions.computeIfAbsent(world.getUID(), key -> new HashSet<>());
            Set<String> current = new HashSet<>();
            boolean changed = false;
            for (ProtectedRegion region : manager.getRegions().values()) {
                String id = region.getId();
                current.add(id);
                if (!isGlobal(region) && !previous.contains(id)) {
                    applySettings(region);
                    changed = true;
                    getLogger().info("Настройки применены к региону " + id + " в мире " + world.getName());
                }
            }
            if (changed) saveManager(manager, world);
            previous.clear();
            previous.addAll(current);
        }
    }

    private void applySettings(ProtectedRegion region) {
        region.setPriority(getConfig().getInt("settings.priority", 1));
        setFlag(region, Flags.PVP, "pvp");
        setFlag(region, Flags.TNT, "tnt");
        setFlag(region, Flags.CREEPER_EXPLOSION, "creeper-explosion");
        setFlag(region, Flags.MOB_SPAWNING, "mob-spawning");
        setFlag(region, Flags.FIRE_SPREAD, "fire-spread");
        setFlag(region, Flags.LAVA_FIRE, "lava-fire");
    }

    private void setFlag(ProtectedRegion region, StateFlag flag, String path) {
        String value = getConfig().getString("flags." + path, "DENY");
        if (value == null || value.equalsIgnoreCase("NONE")) {
            region.setFlag(flag, null);
            return;
        }
        try {
            region.setFlag(flag, StateFlag.State.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Неверное значение флага " + path + ": " + value);
        }
    }

    private boolean isGlobal(ProtectedRegion region) {
        return getConfig().getBoolean("settings.exclude-global", true)
                && region.getId().equalsIgnoreCase("__global__");
    }

    private RegionManager getRegionManager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    private void saveManager(RegionManager manager, World world) {
        try {
            manager.save();
        } catch (Exception exception) {
            getLogger().severe("Не удалось сохранить регионы мира " + world.getName() + ": " + exception.getMessage());
        }
    }

    private String message(String text) {
        String prefix = getConfig().getString("messages.prefix", "&6AlphaGrief &8» &7");
        return ChatColor.translateAlternateColorCodes('&', prefix + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("alpharegionpriority.admin")) {
            sender.sendMessage(message(getConfig().getString("messages.no-permission", "&cНедостаточно прав!")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            knownRegions.clear();
            applyToAllWorlds();
            startChecker();
            sender.sendMessage(message(getConfig().getString("messages.reload", "&aНастройки перезагружены!")));
            return true;
        }
        sender.sendMessage(message("&7Использование: &f/arp reload"));
        return true;
    }
}

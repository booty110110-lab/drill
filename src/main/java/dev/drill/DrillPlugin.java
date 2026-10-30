package dev.drill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DrillPlugin extends JavaPlugin implements Listener {

    private NamespacedKey key;
    private final Set<UUID> busy = new HashSet<>();

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "drill");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Drill enabled");
    }

    private ItemStack createDrill() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Бур", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Копает 3x3, глубина 2", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isDrill(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Игрок не найден: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Укажите ник: /drill ник");
            return true;
        }
        target.getInventory().addItem(createDrill()).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage("Бур выдан игроку " + target.getName());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId())) return;
        if (!p.hasPermission("drill.use")) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE) return;
        if (!isDrill(p.getInventory().getItemInMainHand())) return;

        Block origin = e.getBlock();
        BlockFace face = hitFace(p, origin);
        if (face == null) return;

        int nx = face.getModX(), ny = face.getModY(), nz = face.getModZ();
        int[] u, v;
        if (nx != 0) { u = new int[]{0, 1, 0}; v = new int[]{0, 0, 1}; }
        else if (ny != 0) { u = new int[]{1, 0, 0}; v = new int[]{0, 0, 1}; }
        else { u = new int[]{1, 0, 0}; v = new int[]{0, 1, 0}; }

        busy.add(p.getUniqueId());
        try {
            for (int layer = 0; layer < 2; layer++) {
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        if (layer == 0 && a == 0 && b == 0) continue;
                        int dx = a * u[0] + b * v[0] - layer * nx;
                        int dy = a * u[1] + b * v[1] - layer * ny;
                        int dz = a * u[2] + b * v[2] - layer * nz;
                        Block t = origin.getRelative(dx, dy, dz);
                        if (!canBreak(t)) continue;
                        p.breakBlock(t);
                    }
                }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    private boolean canBreak(Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) return false;
        if (m.getHardness() < 0) return false;
        if (b.getState() instanceof TileState) return false;
        return true;
    }

    private BlockFace hitFace(Player p, Block origin) {
        RayTraceResult r = p.rayTraceBlocks(7);
        if (r != null && r.getHitBlock() != null && r.getHitBlock().equals(origin) && r.getHitBlockFace() != null) {
            return r.getHitBlockFace();
        }
        return null;
    }
}

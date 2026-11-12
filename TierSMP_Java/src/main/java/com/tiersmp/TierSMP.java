package com.tiersmp;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier SMP - Java (Paper) port of the Bedrock "Version 1" pack.
 */
public final class TierSMP extends JavaPlugin implements Listener {

    static final String[] TIERS = {"F", "F+", "D", "D+", "C", "C+", "B", "B+", "A", "A+", "S", "S+"};
    static final String[] COLORS = {"§7", "§f", "§f", "§b", "§a", "§2", "§b", "§3", "§6", "§e", "§c", "§4"};
    static final Material[] TIER_MATS = {
            Material.GRAY_CONCRETE, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE, Material.BLUE_CONCRETE,
            Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.RED_CONCRETE, Material.PURPLE_CONCRETE
    };
    static final String[] PERK_KEYS = {"health", "speed", "strength", "potion"};
    static final String[] PERK_NAMES = {"§6Health boost", "§bSpeed", "§cStrength", "§dPotion absorption"};
    static final String[] PERK_HELP = {
            "-1 = off, 0+ = amplifier", "-1 = off, 0+ = amplifier",
            "-1 = off, 0+ = amplifier", "0 = off, 1+ = absorption level when drinking a potion"
    };
    static final int MAX_FARM_LIST = 5;
    static final String ADMIN_TAG = "tier_admin";
    static final String ROD_NAME = "TierSMP";

    NamespacedKey kTier, kKills, kDeaths, kFarm;
    final Map<UUID, String> cache = new ConcurrentHashMap<>();
    final Random rng = new Random();

    // ------------------------------------------------------------------ setup

    @Override
    public void onEnable() {
        kTier = new NamespacedKey(this, "tier");
        kKills = new NamespacedKey(this, "kills");
        kDeaths = new NamespacedKey(this, "deaths");
        kFarm = new NamespacedKey(this, "farm");
        setDefaults();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) loadPlayer(p);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("TierSMP loaded");
    }

    void setDefaults() {
        FileConfiguration c = getConfig();
        int[] slots = {15, 15, 15, 15, 15, 15, 15, 15, 5, 5, 5, 5};
        int[] health = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
        int[] speed = {-1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0};
        int[] strength = {-1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0};
        int[] potion = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2};
        int[] kills = {8, 8, 7, 7, 6, 6, 5, 5, 4, 3, 2, 0};
        double[] start = {35, 14, 26, 10, 6, 3.5, 2.5, 1.5, 0.8, 0.4, 0.3, 0};
        for (int i = 0; i < TIERS.length; i++) {
            String t = TIERS[i];
            c.addDefault("slots." + t, slots[i]);
            c.addDefault("health." + t, health[i]);
            c.addDefault("speed." + t, speed[i]);
            c.addDefault("strength." + t, strength[i]);
            c.addDefault("potion." + t, potion[i]);
            c.addDefault("kills." + t, kills[i]);
            c.addDefault("start." + t, start[i]);
        }
        c.addDefault("antifarm.minutes", 30);
        c.addDefault("deaths.count", 5);
        c.options().copyDefaults(true);
        saveConfig();
    }

    // ---------------------------------------------------------------- helpers

    static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    static int idx(String t) {
        return t == null ? -1 : Arrays.asList(TIERS).indexOf(t);
    }

    static String colorTier(String t) {
        int i = idx(t);
        return (i < 0 ? "§7" : COLORS[i]) + "[" + t + "]§r";
    }

    static boolean isHigh(String t) {
        return idx(t) >= 8; // A and above
    }

    int ci(String path) {
        return getConfig().getInt(path);
    }

    void msg(Player p, String s) {
        p.sendMessage(c(s));
    }

    void broadcast(String s) {
        Bukkit.broadcast(c(s));
    }

    void thunder() {
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.playSound(o.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
        }
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    boolean isAdmin(Player p) {
        return p.hasPermission("tiersmp.admin") || p.getScoreboardTags().contains(ADMIN_TAG);
    }

    String tier(Player p) {
        return cache.getOrDefault(p.getUniqueId(), "F");
    }

    int pdcInt(Player p, NamespacedKey k) {
        Integer v = p.getPersistentDataContainer().get(k, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    void pdcSet(Player p, NamespacedKey k, int v) {
        p.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, v);
    }

    void loadPlayer(Player p) {
        String t = p.getPersistentDataContainer().get(kTier, PersistentDataType.STRING);
        if (idx(t) >= 0) {
            cache.put(p.getUniqueId(), t);
            updateTag(p, t);
        }
    }

    void setTier(Player p, String t) {
        cache.put(p.getUniqueId(), t);
        p.getPersistentDataContainer().set(kTier, PersistentDataType.STRING, t);
        pdcSet(p, kKills, 0);
        updateTag(p, t);
    }

    void updateTag(Player p, String t) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        int ti = idx(t);
        for (int i = 0; i < TIERS.length; i++) {
            String name = "tsmp_" + i;
            Team team = sb.getTeam(name);
            if (team == null) {
                team = sb.registerNewTeam(name);
                team.prefix(c(colorTier(TIERS[i]) + " §f"));
            }
            if (i == ti) {
                if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
            } else if (team.hasEntry(p.getName())) {
                team.removeEntry(p.getName());
            }
        }
        p.playerListName(c(colorTier(t) + " §f" + p.getName()));
    }

    int countTier(String t, UUID exclude) {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(exclude)) continue;
            if (tier(p).equals(t)) n++;
        }
        return n;
    }

    boolean hasRoom(String t, UUID exclude) {
        return countTier(t, exclude) < ci("slots." + t);
    }

    String findOpenTier(String t) {
        int i = idx(t);
        while (i > 0) {
            if (hasRoom(TIERS[i], null)) return TIERS[i];
            i--;
        }
        return "F";
    }

    String rollStartTier() {
        double total = 0;
        for (String t : TIERS) total += Math.max(0, getConfig().getDouble("start." + t));
        if (total <= 0) return "F";
        double r = rng.nextDouble() * total;
        double a = 0;
        for (String t : TIERS) {
            a += Math.max(0, getConfig().getDouble("start." + t));
            if (r < a) return t;
        }
        return "F";
    }

    // ------------------------------------------------------------------- join

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String t = p.getPersistentDataContainer().get(kTier, PersistentDataType.STRING);
        if (idx(t) < 0) {
            t = rollStartTier();
            if (!hasRoom(t, p.getUniqueId())) t = findOpenTier(t);
            setTier(p, t);
            msg(p, "§8[§6TierSMP§8] §ePlaced in tier " + colorTier(t) + "§e.");
        } else {
            cache.put(p.getUniqueId(), t);
            updateTag(p, t);
            msg(p, "§8[§6TierSMP§8] §eWelcome back. Tier: " + colorTier(t));
        }
        if (isHigh(t)) {
            thunder();
            broadcast("§4§l⚠ " + colorTier(t) + " §r§c" + p.getName() + " has joined. Beware...");
        }
    }

    // ------------------------------------------------------------------ death

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Player k = v.getKiller();
        boolean victimTierChanged = false;

        if (k != null && !k.equals(v)) {
            if (farmBlocked(k, v)) {
                msg(k, "§cAnti-Farm: you killed this player recently. No progress.");
            } else {
                victimTierChanged = handleKill(k, v);
            }
        } else {
            // death without a player killer: S / S+ decay and high-tier announcement
            String t = tier(v);
            if (isHigh(t)) {
                String nt = t;
                if (t.equals("S+")) nt = "S";
                else if (t.equals("S")) nt = "A";
                if (!nt.equals(t)) {
                    setTier(v, nt);
                    victimTierChanged = true;
                    msg(v, "§c§lS DECAY: §r§eDropped to " + colorTier(nt) + "§e.");
                }
                thunder();
                broadcast("§4§l☠ " + colorTier(t) + " §r§c" + v.getName() + " has died. "
                        + (!nt.equals(t) ? "§7They dropped to " + colorTier(nt) + "§7." : "§7They stay " + colorTier(t) + "§7."));
            }
        }

        deathCounter(v, victimTierChanged);
    }

    /** Returns true if the victim's tier was changed. */
    boolean handleKill(Player k, Player v) {
        String kt = tier(k), vt = tier(v);
        int ki = idx(kt), vi = idx(vt);

        if (ki < vi) {
            // Steal: killer takes the tier, victim drops one step
            setTier(k, vt);
            String drop = findOpenTier(TIERS[vi - 1]);
            setTier(v, drop);
            msg(k, "§6§lTIER STEAL! §r§eYou took " + colorTier(vt) + "§e from " + v.getName() + ".");
            msg(v, "§c" + k.getName() + " took your tier. You dropped to " + colorTier(drop) + "§c.");
            if (isHigh(vt)) {
                thunder();
                broadcast("§4§l☠ " + colorTier(vt) + " §r§c" + v.getName() + " was slain by " + k.getName()
                        + "! §eNew " + colorTier(vt) + "§e: §f" + k.getName() + "§e. §7" + v.getName()
                        + " dropped to " + colorTier(drop) + "§7.");
            }
            return true;
        }

        if (isHigh(vt)) {
            thunder();
            broadcast("§4§l☠ " + colorTier(vt) + " §r§c" + v.getName() + " was slain by " + k.getName()
                    + ". §7" + v.getName() + " stays " + colorTier(vt) + "§7.");
        }

        int need = ci("kills." + kt);
        if (kt.equals("S+") || need <= 0) {
            msg(k, "§6You are max rank.");
            return false;
        }
        String next = TIERS[ki + 1];
        int prog = pdcInt(k, kKills) + 1;
        if (prog >= need) {
            if (!hasRoom(next, k.getUniqueId())) {
                pdcSet(k, kKills, need);
                msg(k, "§cRank-up blocked: " + colorTier(next) + "§c is full. You have " + need + "/" + need + " kills.");
                return false;
            }
            setTier(k, next);
            msg(k, "§a§lRANK UP! §r§eNow " + colorTier(next));
            if (isHigh(next)) {
                thunder();
                broadcast("§4§l⚠ §r§c" + k.getName() + " reached " + colorTier(next) + "§r§c.");
            }
        } else {
            pdcSet(k, kKills, prog);
            int left = need - prog;
            msg(k, "§e[Kills] §f" + prog + "/" + need + " §7- " + left + " more kill" + (left == 1 ? "" : "s")
                    + " to reach " + colorTier(next));
        }
        return false;
    }

    void deathCounter(Player v, boolean alreadyChanged) {
        int need = ci("deaths.count");
        if (need <= 0) return;
        String t = tier(v);
        int n = pdcInt(v, kDeaths) + 1;
        if (n < need) {
            pdcSet(v, kDeaths, n);
            if (!t.equals("F")) {
                int left = need - n;
                msg(v, "§7[Deaths] " + n + "/" + need + " - " + left + " more death" + (left == 1 ? "" : "s")
                        + " and you lose a rank.");
            }
            return;
        }
        pdcSet(v, kDeaths, 0);
        if (alreadyChanged || t.equals("F")) return;
        String nt = findOpenTier(TIERS[idx(t) - 1]);
        setTier(v, nt);
        msg(v, "§c§lRANK DOWN! §r§eToo many deaths. Now " + colorTier(nt));
    }

    /** True if this kill is blocked by the anti-farm timer (and records it otherwise). */
    boolean farmBlocked(Player k, Player v) {
        long win = ci("antifarm.minutes") * 60000L;
        if (win <= 0) return false;
        long now = System.currentTimeMillis();
        Map<String, Long> farm = new LinkedHashMap<>();
        String raw = k.getPersistentDataContainer().get(kFarm, PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(";")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    try {
                        farm.put(kv[0], Long.parseLong(kv[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        String vid = v.getUniqueId().toString();
        Long last = farm.get(vid);
        if (last != null && now - last < win) return true;
        farm.entrySet().removeIf(en -> now - en.getValue() >= win);
        farm.remove(vid);
        farm.put(vid, now);
        while (farm.size() > MAX_FARM_LIST) {
            Iterator<String> it = farm.keySet().iterator();
            it.next();
            it.remove();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> en : farm.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(en.getKey()).append('=').append(en.getValue());
        }
        k.getPersistentDataContainer().set(kFarm, PersistentDataType.STRING, sb.toString());
        return false;
    }

    // ------------------------------------------------------------ perks, potion

    void eff(Player p, PotionEffectType type, int dur, int amp) {
        p.addPotionEffect(new PotionEffect(type, dur, amp, false, false, true));
    }

    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String t = tier(p);
            int h = ci("health." + t);
            int s = ci("speed." + t);
            int st = ci("strength." + t);
            if (h >= 0) eff(p, PotionEffectType.HEALTH_BOOST, 140, h);
            if (s >= 0) eff(p, PotionEffectType.SPEED, 140, s);
            if (st >= 0) eff(p, PotionEffectType.STRENGTH, 140, st);
            if (t.equals("S") || t.equals("S+")) {
                eff(p, PotionEffectType.HERO_OF_THE_VILLAGE, 140, 0);
                eff(p, PotionEffectType.REGENERATION, 60, 0);
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.POTION) return;
        int amp = ci("potion." + tier(e.getPlayer()));
        if (amp > 0) eff(e.getPlayer(), PotionEffectType.ABSORPTION, 200, amp);
    }

    // ------------------------------------------------------------------- chat

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message()).trim().toLowerCase();
        if (plain.equals("?tier") || plain.equals("!tier")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> {
                if (!isAdmin(p)) {
                    msg(p, "§cNeed permission 'tiersmp.admin' or the 'tier_admin' tag.");
                    return;
                }
                open(p, "main", null, null);
            });
            return;
        }
        final String t = tier(p);
        e.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                c(colorTier(t) + " ").append(sourceDisplayName).append(c("§f: ")).append(message)));
    }

    // --------------------------------------------------------- command + rod

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (!isAdmin(p)) {
            msg(p, "§7Your tier: " + colorTier(tier(p)));
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("stick")) {
            ItemStack rod = new ItemStack(Material.BLAZE_ROD);
            ItemMeta im = rod.getItemMeta();
            im.displayName(c("§6" + ROD_NAME).decoration(TextDecoration.ITALIC, false));
            im.lore(List.of(c("§7Right-click: Tier admin menu").decoration(TextDecoration.ITALIC, false)));
            rod.setItemMeta(im);
            p.getInventory().addItem(rod);
            msg(p, "§aGave you the TierSMP admin rod.");
            return true;
        }
        open(p, "main", null, null);
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.BLAZE_ROD) return;
        ItemMeta im = it.getItemMeta();
        if (im == null || im.displayName() == null) return;
        if (!PlainTextComponentSerializer.plainText().serialize(im.displayName()).contains(ROD_NAME)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!isAdmin(p)) {
            msg(p, "§cNo permission.");
            return;
        }
        open(p, "main", null, null);
    }

    // ================================================================= ADMIN GUI

    final class Menu implements InventoryHolder {
        final String type;
        final String key;
        final UUID target;
        final Inventory inv;
        List<UUID> order = new ArrayList<>();

        Menu(String type, String key, UUID target, String title, int size) {
            this.type = type;
            this.key = key;
            this.target = target;
            this.inv = Bukkit.createInventory(this, size, c(title));
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    ItemStack itemL(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(c(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s).decoration(TextDecoration.ITALIC, false));
            im.lore(l);
        }
        it.setItemMeta(im);
        return it;
    }

    ItemStack item(Material m, String name, String... lore) {
        return itemL(m, name, Arrays.asList(lore));
    }

    static int slotOf(int i) {
        return i < 6 ? 1 + i : 10 + (i - 6);
    }

    static int tierAt(int slot) {
        for (int i = 0; i < TIERS.length; i++) if (slotOf(i) == slot) return i;
        return -1;
    }

    static double[] spec(String key) {
        // {min, max, step, bigStep}
        return switch (key) {
            case "slots" -> new double[]{1, 100, 1, 5};
            case "kills" -> new double[]{1, 50, 1, 5};
            case "start" -> new double[]{0, 100, 0.1, 1};
            case "potion" -> new double[]{0, 9, 1, 1};
            case "antifarm" -> new double[]{0, 720, 1, 5};
            case "deaths" -> new double[]{0, 50, 1, 5};
            default -> new double[]{-1, 9, 1, 1}; // health / speed / strength
        };
    }

    static String numTitle(String key) {
        return switch (key) {
            case "slots" -> "§6Slots per tier";
            case "kills" -> "§eKills to rank up";
            case "start" -> "§6Start roll chances";
            case "health" -> "§6Health boost";
            case "speed" -> "§bSpeed";
            case "strength" -> "§cStrength";
            case "potion" -> "§dPotion absorption";
            default -> "§6Tier SMP";
        };
    }

    String singlePath(String key) {
        return key.equals("antifarm") ? "antifarm.minutes" : "deaths.count";
    }

    void open(Player p, String type, String key, UUID target) {
        String title;
        int size = 27;
        switch (type) {
            case "main" -> title = "§6Tier SMP Admin";
            case "perks" -> title = "§6Perks";
            case "num" -> title = numTitle(key);
            case "single" -> title = key.equals("antifarm") ? "§cAnti-farm timer" : "§4Death rank-down";
            case "players" -> {
                title = "§6Set tier: pick a player";
                size = 54;
            }
            default -> title = "§6Set tier";
        }
        Menu m = new Menu(type, key, target, title, size);
        render(m);
        p.openInventory(m.inv);
    }

    void render(Menu m) {
        Inventory inv = m.inv;
        inv.clear();
        switch (m.type) {
            case "main" -> {
                inv.setItem(10, item(Material.BEACON, "§aEdit perks", "§7Health, speed, strength, potion"));
                inv.setItem(11, item(Material.CHEST, "§bEdit slots", "§7Max players per tier"));
                inv.setItem(12, item(Material.DIAMOND_SWORD, "§eKills to rank up", "§7Kills needed to leave each tier"));
                inv.setItem(13, item(Material.EXPERIENCE_BOTTLE, "§6Start roll chances", "§7Chance to start in each tier"));
                inv.setItem(14, item(Material.CLOCK, "§cAnti-farm timer", "§7Now: §f" + ci("antifarm.minutes") + " min"));
                inv.setItem(15, item(Material.WITHER_SKELETON_SKULL, "§4Death rank-down",
                        "§7Now: §f" + (ci("deaths.count") == 0 ? "off" : ci("deaths.count") + " deaths")));
                inv.setItem(16, item(Material.PLAYER_HEAD, "§dSet player tier", "§7Pick an online player"));
                inv.setItem(26, item(Material.TNT, "§7Reset to defaults", "§7Click to reset all settings"));
            }
            case "perks" -> {
                Material[] mats = {Material.GOLDEN_APPLE, Material.SUGAR, Material.BLAZE_POWDER, Material.POTION};
                for (int i = 0; i < PERK_KEYS.length; i++) {
                    inv.setItem(10 + i, item(mats[i], PERK_NAMES[i], "§7" + PERK_HELP[i]));
                }
                inv.setItem(22, item(Material.ARROW, "§cBack"));
            }
            case "num" -> {
                double[] sp = spec(m.key);
                double total = 0;
                if (m.key.equals("start")) {
                    for (String t : TIERS) total += Math.max(0, getConfig().getDouble("start." + t));
                }
                for (int i = 0; i < TIERS.length; i++) {
                    if (m.key.equals("kills") && i == 11) continue;
                    String t = TIERS[i];
                    double v = getConfig().getDouble(m.key + "." + t);
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Value: §f" + fmt(v));
                    if (m.key.equals("start")) {
                        lore.add("§7Chance: §f" + (total > 0 ? String.format("%.1f", Math.max(0, v) / total * 100) : "0.0") + "%");
                    }
                    if (m.key.equals("slots")) lore.add("§7In use: §f" + countTier(t, null));
                    int pi = Arrays.asList(PERK_KEYS).indexOf(m.key);
                    if (pi >= 0) lore.add("§7" + PERK_HELP[pi]);
                    lore.add("§8Left-click: +" + fmt(sp[2]) + "   Right-click: -" + fmt(sp[2]));
                    lore.add("§8Shift-click: " + fmt(sp[3]) + " at a time");
                    inv.setItem(slotOf(i), itemL(TIER_MATS[i], colorTier(t), lore));
                }
                inv.setItem(22, item(Material.ARROW, "§cBack"));
            }
            case "single" -> {
                inv.setItem(10, item(Material.RED_CONCRETE, "§c-5"));
                inv.setItem(11, item(Material.ORANGE_CONCRETE, "§6-1"));
                String unit = m.key.equals("antifarm") ? " min" : " deaths";
                int cur = ci(singlePath(m.key));
                inv.setItem(13, item(m.key.equals("antifarm") ? Material.CLOCK : Material.WITHER_SKELETON_SKULL,
                        "§fCurrent: " + cur + unit,
                        m.key.equals("antifarm")
                                ? "§7Minutes before the same kill counts again. 0 = off."
                                : "§7Deaths needed to lose one tier. 0 = off."));
                inv.setItem(15, item(Material.LIME_CONCRETE, "§a+1"));
                inv.setItem(16, item(Material.GREEN_CONCRETE, "§2+5"));
                inv.setItem(22, item(Material.ARROW, "§cBack"));
            }
            case "players" -> {
                m.order = new ArrayList<>();
                int n = 0;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (n >= 45) break;
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sm = (SkullMeta) head.getItemMeta();
                    sm.setOwningPlayer(o);
                    sm.displayName(c("§f" + o.getName()).decoration(TextDecoration.ITALIC, false));
                    sm.lore(List.of(c("§7Tier: " + colorTier(tier(o))).decoration(TextDecoration.ITALIC, false)));
                    head.setItemMeta(sm);
                    inv.setItem(n, head);
                    m.order.add(o.getUniqueId());
                    n++;
                }
                inv.setItem(49, item(Material.ARROW, "§cBack"));
            }
            default -> { // "ptier"
                Player tp = m.target == null ? null : Bukkit.getPlayer(m.target);
                for (int i = 0; i < TIERS.length; i++) {
                    inv.setItem(slotOf(i), item(TIER_MATS[i], colorTier(TIERS[i]),
                            "§7Click to set " + (tp == null ? "player" : tp.getName()) + " to this tier"));
                }
                inv.setItem(22, item(Material.ARROW, "§cBack"));
            }
        }
    }

    void adjust(String path, String key, boolean up, boolean shift) {
        double[] sp = spec(key);
        double v = getConfig().getDouble(path);
        double d = shift ? sp[3] : sp[2];
        v += up ? d : -d;
        v = Math.max(sp[0], Math.min(sp[1], Math.round(v * 10) / 10.0));
        if (key.equals("start")) getConfig().set(path, v);
        else getConfig().set(path, (int) Math.round(v));
        saveConfig();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        if (!(top.getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
        if (!isAdmin(p)) {
            p.closeInventory();
            return;
        }
        int s = e.getSlot();
        ClickType ct = e.getClick();
        boolean left = ct.isLeftClick();
        boolean right = ct.isRightClick();
        boolean shift = ct.isShiftClick();

        switch (m.type) {
            case "main" -> {
                switch (s) {
                    case 10 -> open(p, "perks", null, null);
                    case 11 -> open(p, "num", "slots", null);
                    case 12 -> open(p, "num", "kills", null);
                    case 13 -> open(p, "num", "start", null);
                    case 14 -> open(p, "single", "antifarm", null);
                    case 15 -> open(p, "single", "deaths", null);
                    case 16 -> open(p, "players", null, null);
                    case 26 -> {
                        for (String k : new ArrayList<>(getConfig().getKeys(false))) getConfig().set(k, null);
                        setDefaults();
                        msg(p, "§aConfig reset to defaults.");
                    }
                    default -> {
                    }
                }
            }
            case "perks" -> {
                if (s >= 10 && s < 10 + PERK_KEYS.length) open(p, "num", PERK_KEYS[s - 10], null);
                else if (s == 22) open(p, "main", null, null);
            }
            case "num" -> {
                if (s == 22) {
                    if (Arrays.asList(PERK_KEYS).contains(m.key)) open(p, "perks", null, null);
                    else open(p, "main", null, null);
                    return;
                }
                int i = tierAt(s);
                if (i < 0 || (m.key.equals("kills") && i == 11)) return;
                if (!left && !right) return;
                adjust(m.key + "." + TIERS[i], m.key, left, shift);
                render(m);
            }
            case "single" -> {
                if (s == 22) {
                    open(p, "main", null, null);
                    return;
                }
                int d = switch (s) {
                    case 10 -> -5;
                    case 11 -> -1;
                    case 15 -> 1;
                    case 16 -> 5;
                    default -> 0;
                };
                if (d == 0) return;
                double[] sp = spec(m.key);
                int v = (int) Math.max(sp[0], Math.min(sp[1], ci(singlePath(m.key)) + d));
                getConfig().set(singlePath(m.key), v);
                saveConfig();
                render(m);
            }
            case "players" -> {
                if (s == 49) {
                    open(p, "main", null, null);
                } else if (s >= 0 && s < m.order.size()) {
                    open(p, "ptier", null, m.order.get(s));
                }
            }
            default -> { // "ptier"
                if (s == 22) {
                    open(p, "players", null, null);
                    return;
                }
                int i = tierAt(s);
                if (i < 0) return;
                Player target = m.target == null ? null : Bukkit.getPlayer(m.target);
                if (target == null) {
                    msg(p, "§cThat player left.");
                    open(p, "players", null, null);
                    return;
                }
                setTier(target, TIERS[i]);
                msg(p, "§aSet " + target.getName() + " -> " + colorTier(TIERS[i]));
                if (!target.equals(p)) msg(target, "§aAdmin set your tier to " + colorTier(TIERS[i]));
                render(m);
            }
        }
    }
}

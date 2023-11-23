package com.github.kumo0621.skyblock2;
import com.earth2me.essentials.api.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import java.math.BigDecimal;
// 他の必要なインポート...

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Skyblock2 extends JavaPlugin implements Listener {

    private ScoreboardManager manager;
    private Scoreboard board;

    @Override
    public void onEnable() {
        // スコアボードマネージャーとボードの初期化
        manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();

        // 役職に対応するチームを作成
        createTeam("石工", ChatColor.GOLD);
        createTeam("商人", ChatColor.DARK_PURPLE);
        createTeam("冒険者", ChatColor.GREEN);
        createTeam("漁師", ChatColor.YELLOW);
        createTeam("鍛冶屋", ChatColor.GRAY);
        createTeam("電気工事士", ChatColor.WHITE);
        createTeam("パン屋", ChatColor.LIGHT_PURPLE);
        // コンフィグファイルの読み込み
        this.saveDefaultConfig();

        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    applyRoleEffects(player);
                }
            }
        }, 0L, 20L * 60); // 20 ticks * 60 = 1分ごと
    }

    private void applyRoleEffects(Player player) {
        String role = this.getConfig().getString("players." + player.getUniqueId().toString());
        if (role != null) {
            switch (role) {
                case "石工":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 1200, 1));
                    break;
                case "商人":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    break;
                case "冒険者":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 1));
                    break;
                case "漁師":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 1200, 2));
                    break;
                case "鍛冶屋":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    break;
                case "電気工事士":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 1200, 4));
                    break;
                case "パン屋":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 1200, 1));
                    break;
            }
        }
    }

    private void createTeam(String name, ChatColor color) {
        Team team = board.registerNewTeam(name);
        team.setPrefix(color + "[" + name + "] ");
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String role = this.getConfig().getString("players." + player.getUniqueId().toString());
        if (role != null && board.getTeam(role) != null) {
            // コンフィグから読み込んだ役職に基づき、プレイヤーを適切なチームに追加
            Team team = board.getTeam(role);
            team.addEntry(player.getName());
            player.setScoreboard(board);
        } else if (!player.hasPlayedBefore()) {
            // 新規プレイヤーの場合、役職選択のメッセージを表示
            player.sendMessage(ChatColor.AQUA + "役職を選んでください。利用可能な役職: 石工, 商人, 漁師, 冒険者, 鍛冶屋, 電気工事士, パン屋");
            player.sendMessage(ChatColor.GREEN + "コマンドを使用して役職を選択: /setrole <役職名>");
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setrole") && sender instanceof Player) {
            Player player = (Player) sender;

            // コンフィグに役職が存在しない場合のみ処理を続ける
            if (!this.getConfig().contains("players." + player.getUniqueId().toString())) {
                if (args.length == 1) {
                    String role = args[0];
                    if (setRole(player, role)) {
                        player.sendMessage(ChatColor.GREEN + "役職が設定されました: " + role);
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "無効な役職です。");
                        return false;
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "使用方法: /setrole <役職名>");
                    return false;
                }
            } else {
                player.sendMessage(ChatColor.RED + "あなたは既に役職を持っています。");
                return true;
            }
        }
        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Inventory shopInventory = Bukkit.createInventory(null, 9, "Shop");
                String role = this.getConfig().getString("players." + player.getUniqueId().toString());
                // プレイヤーの役職に基づいてアイテムを設定
                if (role != null) {
                    switch (role) {
                        case "石工":
                            break;
                        case "商人":
                            addItemToShop(shopInventory, Material.COBBLESTONE, 120, "購入");
                            addItemToShop(shopInventory, Material.COBBLESTONE, 100, "売却");
                            break;
                        case "漁師":
                            addItemToShop(shopInventory, Material.DIAMOND_SWORD, 100, "ダイヤモンドの剣");
                            break;
                        case "冒険者":
                            addItemToShop(shopInventory, Material.DIAMOND_SWORD, 100, "ダイヤモンドの剣");
                            break;
                        case "鍛冶屋":
                            addItemToShop(shopInventory, Material.DIAMOND_SWORD, 100, "ダイヤモンドの剣");
                            break;
                        case "電気工事士":
                            addItemToShop(shopInventory, Material.DIAMOND_SWORD, 100, "ダイヤモンドの剣");
                            break;
                        case "パン屋":
                            addItemToShop(shopInventory, Material.DIAMOND_SWORD, 100, "ダイヤモンドの剣");
                            break;
                    }
                    player.openInventory(shopInventory);
                } else {
                    player.sendMessage("役職を選択してください");
                }
                // 他の役職に対するアイテムもここで追加..

            } else {
                sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみが使用できます。");
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("warp") && sender instanceof Player) {
            Player player = (Player) sender;
            FileConfiguration config = this.getConfig();
            String role = config.getString("players." + player.getUniqueId().toString());

            // 役職に応じたロケーションを取得
            if (role != null && config.contains("roles." + role)) {
                int x = config.getInt("roles." + role + ".x");
                int y = config.getInt("roles." + role + ".y");
                int z = config.getInt("roles." + role + ".z");

                Location loc = new Location(getServer().getWorld("world"), x, y, z);
                player.teleport(loc);
            } else {
                player.sendMessage("あなたの役職にはテレポート地点が設定されていません。");
            }
        }
        return false;
    }
    private void addItemToShop(Inventory inventory, Material material, int price, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + description);
        meta.setLore(Arrays.asList(ChatColor.YELLOW + "価格: " + price + " 円"));
        item.setItemMeta(meta);
        inventory.addItem(item);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        Inventory inventory = event.getClickedInventory();

        if (inventory != null && inventory.getHolder() == null && clickedItem != null && clickedItem.hasItemMeta()) {
            String inventoryTitle = event.getView().getTitle();
            if (inventoryTitle.equals("Shop")) {
                event.setCancelled(true);

                // 購入処理
                if (event.getRawSlot() < 1) { // 例えば、最初の9スロットを購入用に使用
                    int price = getPriceFromLore(clickedItem.getItemMeta().getLore());
                    purchaseItem(player, clickedItem, price);
                }
                // 売却処理
                else {
                    int Price = getPriceFromLore(clickedItem.getItemMeta().getLore());
                    sellItem(player, clickedItem, Price);
                }
            }
        }
    }

    private void purchaseItem(Player player, ItemStack item, int price) {
        try {
            double balance = Economy.getMoney(player.getName());
            if (balance >= price) {
                Economy.setMoney(player.getName(), balance - price);

                // 元のアイテムのMaterialを取得し、新しいアイテムを作成
                Material itemType = item.getType();
                ItemStack newItem = new ItemStack(itemType);

                // 新しいアイテムをプレイヤーのインベントリに追加
                player.getInventory().addItem(newItem);
                player.sendMessage(ChatColor.GREEN + "購入しました: " + newItem.getType().toString());
            } else {
                player.sendMessage(ChatColor.RED + "購入するのに十分なお金がありません！");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "購入中にエラーが発生しました！");
            e.printStackTrace();
        }
    }


    private void sellItem(Player player, ItemStack item, int sellPrice) {
        try {
            Material itemType = item.getType();
            if (!player.getInventory().contains(itemType)) {
                player.sendMessage(ChatColor.RED + "売却するアイテムがありません！");
                return;
            }

            // アイテムの種類に基づいて1つ減らす
            removeOneItemOfType(player, itemType);

            // 経済残高を更新
            Economy.add(player.getName(), sellPrice);
            player.sendMessage(ChatColor.GREEN + "売却しました: " + item.getItemMeta().getDisplayName());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "売却中にエラーが発生しました！");
            e.printStackTrace();
        }
    }
    private void removeOneItemOfType(Player player, Material itemType) {
        ItemStack[] items = player.getInventory().getContents();

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && item.getType() == itemType) {
                int amount = item.getAmount();

                if (amount > 1) {
                    // アイテムの数量が1より多い場合、数量を1減らす
                    item.setAmount(amount - 1);
                } else {
                    // アイテムの数量が1の場合、そのスロットからアイテムを削除
                    player.getInventory().setItem(i, null);
                }
                break; // 最初に見つかったアイテムを処理したらループを終了
            }
        }
    }

    private int getPriceFromLore(java.util.List<String> lore) {
        if (lore != null && !lore.isEmpty()) {
            String priceLine = lore.get(0); // "価格: XXX 円" の形式を想定
            return Integer.parseInt(priceLine.split(" ")[1]);
        }
        return 0;
    }
    public boolean setRole(Player player, String role) {
        if (board.getTeam(role) != null) {
            board.getTeam(role).addEntry(player.getName());
            player.setScoreboard(board);
            this.getConfig().set("players." + player.getUniqueId().toString(), role);
            this.saveConfig();
            return true;
        }
        return false;
    }
}
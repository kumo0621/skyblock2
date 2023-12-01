package com.github.kumo0621.skyblock2;

import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class Skyblock2 extends JavaPlugin implements Listener {

    private ScoreboardManager manager;
    private Scoreboard board;
    private Economy econ;

    @Override
    public void onEnable() {
        // スコアボードマネージャーとボードの初期化
        manager = Bukkit.getScoreboardManager();
        board = manager.getMainScoreboard();

        // 役職に対応するチームを作成
        createTeam("ニート", ChatColor.DARK_GRAY);
        createTeam("石工", ChatColor.GOLD);
        createTeam("裁縫師", ChatColor.DARK_PURPLE);
        createTeam("冒険者", ChatColor.GREEN);
        createTeam("漁師", ChatColor.YELLOW);
        createTeam("鍛冶屋", ChatColor.GRAY);
        createTeam("電気工事士", ChatColor.WHITE);
        createTeam("パン屋", ChatColor.LIGHT_PURPLE);
        // コンフィグファイルの読み込み
        this.saveDefaultConfig();

        // Vaultの初期化
        if (!setupEconomy() ) {
            getLogger().severe("Vaultが見つかりませんでした。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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

        // 全員をチームに追加
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            // コンフィグから読み込んだ役職に基づき、プレイヤーを適切なチームに追加
            String role = this.getConfig().getString("players." + player.getUniqueId().toString());
            if (role == null) {
                role = "ニート";
            }
            Team team = board.getTeam(role);
            team.addEntry(player.getName());
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }

    private void applyRoleEffects(Player player) {
        String role = this.getConfig().getString("players." + player.getUniqueId().toString());
        if (role != null) {
            switch (role) {
                case "石工":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 1200, 3).withParticles(false));
                    break;
                case "裁縫師":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2).withParticles(false));
                    break;
                case "冒険者":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 1).withParticles(false));
                    break;
                case "漁師":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 1200, 2).withParticles(false));
                    break;
                case "鍛冶屋":
                    break;
                case "電気工事士":
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 1200, 4).withParticles(false));
                    break;
                case "パン屋":
                    break;
            }
        }
    }

    private void createTeam(String name, ChatColor color) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.setPrefix(color + "[" + name + "] ");
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getInventory();
        String role = this.getConfig().getString("players." + player.getUniqueId().toString());
        if (role == null) {
            role = "ニート";
        }
        Team team = board.getTeam(role);
        if (team != null) {
            // コンフィグから読み込んだ役職に基づき、プレイヤーを適切なチームに追加
            team.addEntry(player.getName());
        } else if (!player.hasPlayedBefore()) {
            // 新規プレイヤーの場合、職業選択のメッセージを表示
            player.sendMessage(ChatColor.GREEN + "コンパスを右クリックして職業を選択しよう！");
        }
        if (!hasCompass(player)) {
            ItemStack compass = new ItemStack(Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();

            // ここでコンパスに名前を設定します
            meta.setDisplayName("右クリックでテレポート (しゃがみ右クリックで残高確認");
            compass.setItemMeta(meta);

            // プレイヤーにコンパスを与えます
            player.getInventory().addItem(compass);
        }
    }

    private boolean hasCompass(Player player) {
        // インベントリ内のアイテムを確認し、コンパスが存在するかどうかを返します
        ItemStack[] items = player.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.COMPASS) {
                return true; // インベントリにコンパスが見つかった場合は true を返す
            }
        }
        return false; // インベントリにコンパスが見つからなかった場合は false を返す
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 役職設定コマンドの処理
        if (cmd.getName().equalsIgnoreCase("setrole")) {
            // コマンド実行者とコマンドの引数から、対象のプレイヤーを取得
            List<Player> players = getTargetPlayer(sender, args, 1);
            if (players == null) return false;

            // コンフィグに役職が存在しない場合のみ処理を続ける
            for (Player player : players) {
                if (!this.getConfig().contains("players." + player.getUniqueId().toString()) || sender.hasPermission("skyblock2.admin")) {
                    if (args.length >= 1) {
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
        }

        // テレポートコマンドの処理
        if (cmd.getName().equalsIgnoreCase("warp") && sender instanceof Player) {
            // コマンド実行者とコマンドの引数から、対象のプレイヤーを取得
            List<Player> players = getTargetPlayer(sender, args, 0);
            if (players == null) return false;

            FileConfiguration config = this.getConfig();

            for (Player player : players) {
                // 役職に応じたロケーションを取得
                String role = config.getString("players." + player.getUniqueId().toString());
                if (role == null) {
                    role = "無職";
                }
                if (config.contains("roles." + role)) {
                    int x = config.getInt("roles." + role + ".x");
                    int y = config.getInt("roles." + role + ".y");
                    int z = config.getInt("roles." + role + ".z");

                    Location loc = new Location(getServer().getWorld("world"), x, y, z);
                    player.teleport(loc);
                } else {
                    player.sendMessage("あなたの役職にはテレポート地点が設定されていません。");
                }
            }
        }

        return false;
    }

    /**
     * コマンド実行者とコマンドの引数から、対象のプレイヤーを取得する
     *
     * @param sender コマンド実行者
     * @param args   コマンドの引数
     * @param index  引数の中でプレイヤー名を指定する位置
     * @return 対象のプレイヤーのリスト
     */
    private static List<Player> getTargetPlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            // senderに権限があるかチェック
            if (!sender.hasPermission("skyblock2.admin")) {
                sender.sendMessage(ChatColor.RED + "他のプレイヤーのコマンドを実行する権限がありません。");
                return null;
            }

            // @pとか指定があればそれを使用
            return Bukkit.getServer().selectEntities(sender, args[index]).stream()
                    .filter(entity -> entity instanceof Player)
                    .map(entity -> (Player) entity)
                    .collect(Collectors.toList());
        } else if (sender instanceof Player) {
            // なければコマンド実行者を使用
            return Collections.singletonList((Player) sender);
        } else {
            // それもなければエラー
            sender.sendMessage(ChatColor.RED + "プレイヤー名を指定してください。");
            return null;
        }
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // プレイヤーが右クリックしたかつ手にコンパスを持ってwいるか確認
        if (player.getInventory().getItemInMainHand().getType() == Material.COMPASS) {
            if (event.getAction().isRightClick()) {
                if (player.isSneaking()) {
                    // VaultAPIで残高を取得
                    double balance = econ.getBalance(player);
                    String currency = econ.currencyNamePlural();

                    // コンパスをしゃがみ右クリックしたときの処理
                    player.sendMessage(String.format(ChatColor.GREEN + "あなたの残高は %s%s です。", currency, new DecimalFormat("#,###.##").format(balance)));
                } else {
                    // コンパスを右クリックしたときの処理
                    player.performCommand("warp");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // ￥200徴収
        if (econ.withdrawPlayer(player, 500).transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "死亡により￥500徴収されました。");
        } else {
            player.sendMessage(ChatColor.RED + "死亡しましたが、お金が足りないため徴収されませんでした。");
        }
    }
}
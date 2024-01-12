package com.github.kumo0621.skyblock2;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class Skyblock2 extends JavaPlugin implements Listener {

    private ScoreboardManager manager;
    private Scoreboard board;
    private Economy econ;
    private LuckPerms luckPerms;
    private List<String> teams;
    private Team defaultTeam;
    private Team neetTeam;

    @Override
    public void onEnable() {
        // LuckPermsの初期化
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            getLogger().severe("LuckPermsが見つかりませんでした。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        luckPerms = provider.getProvider();

        // コンフィグファイルの読み込み
        this.saveDefaultConfig();
        FileConfiguration config = getConfig();
        // イベントリスナーの登録
        getServer().getPluginManager().registerEvents(this, this);

        // スコアボードマネージャーとボードの初期化
        manager = Bukkit.getScoreboardManager();
        board = manager.getMainScoreboard();

        // 職業に対応するチームを作成
        teams = new ArrayList<>();
        ConfigurationSection roles = Objects.requireNonNull(config.getConfigurationSection("roles"));
        for (String role : roles.getKeys(false)) {
            createTeam(role, roles.getString(role + ".name"), ChatColor.valueOf(roles.getString(role + ".color")));
        }

        // デフォルトロールを取得
        String defaultRole = Objects.requireNonNull(config.getString("defaultRole"), "デフォルトロールが設定されていません。");
        defaultTeam = Objects.requireNonNull(board.getTeam(defaultRole), "デフォルトロール(" + defaultRole + ")が見つかりませんでした。");

        // ニートロールを取得
        String neetRole = Objects.requireNonNull(config.getString("neetRole"), "ニートロールが設定されていません。");
        neetTeam = Objects.requireNonNull(board.getTeam(neetRole), "ニートロール(" + neetRole + ")が見つかりませんでした。");

        // Vaultの初期化
        if (!setupEconomy()) {
            getLogger().severe("Vaultが見つかりませんでした。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1分ごとに職業の効果を適用
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    applyRoleEffects(player);
                }
            }
        }, 0L, 20L * 60); // 20 ticks * 60 = 1分ごと
    }

    /**
     * Vaultの初期化
     *
     * @return 初期化に成功したかどうか
     */
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

    /**
     * 職業の効果を適用
     *
     * @param player プレイヤー
     */
    private void applyRoleEffects(Player player) {
        // コンフィグを取得
        ConfigurationSection roles = Objects.requireNonNull(getConfig().getConfigurationSection("roles"));
        // プレイヤーのチームを取得
        Team team = board.getPlayerTeam(player);
        if (team == null) return;

        // チームに設定されているポーション効果を適用
        List<?> potionEffects = roles.getList(team.getName() + ".potionEffects");
        if (potionEffects == null) return;

        // ポーション効果を適用
        for (Object effect : potionEffects) {
            if (!(effect instanceof PotionEffect)) continue;
            player.addPotionEffect((PotionEffect) effect);
        }
    }

    /**
     * 職業に対応するチームを作成
     *
     * @param name  チーム名
     * @param color チームの色
     */
    private void createTeam(String name, String displayName, ChatColor color) {
        // リストにチーム名を追加
        teams.add(name);

        // スコアボードにチームを作成
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.setPrefix(color + "[" + displayName + "] ");
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        team.setDisplayName(displayName);

        // 必要なLuckPermsグループがなければ作成
        if (!luckPerms.getGroupManager().isLoaded(name)) {
            luckPerms.getGroupManager().createAndLoadGroup(name).thenAccept(group -> {
                // パーミッションを設定
                group.data().add(Node.builder("skyblock2." + name).build());
                // パーミッションを保存
                luckPerms.getGroupManager().saveGroup(group);
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Team team = board.getPlayerTeam(player);

        // ロールが設定されていない場合、無職ロールを設定
        if (team == null) {
            team = defaultTeam;
            team.addEntry(player.getName());

            // チュートリアル地点にテレポートする
            player.performCommand("warp");
        }

        // LuckPermsのグループを設定
        setGroup(player, team.getName());

        // コンパスを持っていない場合、コンパスを与える
        if (!hasCompass(player)) {
            ItemStack compass = new ItemStack(Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();

            // ここでコンパスに名前を設定します
            meta.setDisplayName("右クリックでテレポート");
            meta.setLore(Arrays.asList(
                    "しゃがみ右クリックで残高を確認",
                    "しゃがみ左クリックでNotionを確認"
            ));
            compass.setItemMeta(meta);

            // プレイヤーにコンパスを与えます
            player.getInventory().addItem(compass);
        }
    }

    /**
     * プレイヤーがコンパスを持っているかどうかを確認します
     *
     * @param player プレイヤー
     * @return コンパスを持っているかどうか
     */
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
        // 職業設定コマンドの処理
        if (cmd.getName().equalsIgnoreCase("addrole")) {
            // コマンド実行者とコマンドの引数から、対象のプレイヤーを取得
            List<Player> players = getTargetPlayer(sender, args, 1);
            if (players == null) return false;
            // コンフィグに職業が存在しない場合のみ処理を続ける
            for (Player player : players) {
                getGroup(player).thenAccept(groups -> {
                    if (groups.size() < 2 || sender.hasPermission("skyblock2.admin")) {
                        if (args.length >= 1) {
                            String role = args[0];
                            List<String> newGroups = new ArrayList<>();
                            newGroups.addAll(groups);
                            newGroups.add(role);
                            if (setGroup(player, newGroups)) {
                                player.sendMessage(ChatColor.GREEN + "職業が設定されました: " + role);
                                return true;
                            } else {
                                player.sendMessage(ChatColor.RED + "無効な職業です。");
                                return false;
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "使用方法: /setrole <職業名>");
                            return false;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "あなたは既に職業を持っています。");
                        return true;
                    }
                });
            }
        }
        if (cmd.getName().equalsIgnoreCase("clearrole")) {

        }
        // テレポートコマンドの処理
        if (cmd.getName().equalsIgnoreCase("warp") && sender instanceof Player) {
            // コマンド実行者とコマンドの引数から、対象のプレイヤーを取得
            List<Player> players = getTargetPlayer(sender, args, 0);
            if (players == null) return false;

            FileConfiguration config = this.getConfig();

            for (Player player : players) {
                // 職業に応じたロケーションを取得
                Team team = board.getPlayerTeam(player);
                if (team == null) team = neetTeam;
                String role = team.getName();

                // テレポート地点を取得
                ConfigurationSection home = config.getConfigurationSection("roles." + role + ".home");
                if (home != null) {
                    int x = home.getInt("x");
                    int y = home.getInt("y");
                    int z = home.getInt("z");

                    // ロケーションを作成し、プレイヤーをテレポート
                    String world = home.getString("world");
                    Location loc = new Location(getServer().getWorld(world), x, y, z);
                    player.teleport(loc);
                } else {
                    player.sendMessage("あなたの職業にはテレポート地点が設定されていません。");
                }
            }
        }

        // Notionコマンドの処理
        if (cmd.getName().equalsIgnoreCase("notion") && sender instanceof Player) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + sender.getName() + " " + getConfig().getString("notion"));
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

    /**
     * プレイヤーに職業を設定する (チームに追加する)
     *
     * @param player プレイヤー
     * @param role   職業
     * @return 設定に成功したかどうか
     */
    public boolean setTeam(Player player, String role) {
        // 引数の文字列のチームを取得
        Team team = board.getTeam(role);
        // チームが存在すればプレイヤーを追加
        if (team != null) {
            team.addEntry(player.getName());
            return true;
        }
        return false;
    }

    /**
     * LuckPermsのグループを設定
     *
     * @param player プレイヤー
     * @param roles  職業
     */
    private CompletableFuture<Boolean> setGroup(Player player, List<String> roles) {
        // プレイヤー情報を読み込み
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenApply(user -> {
            // プレイヤーに職業のグループを付与
            for (String groupName : teams) {
                if (roles.contains(groupName)) {
                    // プレイヤーが所属しているグループに職業のグループを追加
                    user.data().add(InheritanceNode.builder(groupName).value(true).build());
                } else {
                    // プレイヤーが所属しているグループ以外のグループを削除
                    user.data().remove(InheritanceNode.builder(groupName).build());
                }
            }
            // プレイヤー情報を保存
            luckPerms.getUserManager().saveUser(user);
            return true;
        });
    }

    private CompletableFuture<List<String>> getGroup(Player player) {
        // プレイヤー情報を読み込み
        return luckPerms.getUserManager().loadUser(player.getUniqueId())
                .thenApply(user -> user.data().toCollection().stream()
                        .filter(node -> node.getValue() && teams.contains(node.getKey()))
                        .map(Node::getKey)
                        .collect(Collectors.toList()));

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
            } else if (event.getAction().isLeftClick()) {
                if (player.isSneaking()) {
                    // コンパスをしゃがみ左クリックしたときの処理
                    player.performCommand("notion");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 罰金徴収
        int penalty = getConfig().getInt("deathPenalty");
        if (econ.withdrawPlayer(player, penalty).transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "死亡により￥" + penalty + "徴収されました。");
        } else {
            player.sendMessage(ChatColor.RED + "死亡しましたが、お金が足りないため徴収されませんでした。");
        }
    }
}
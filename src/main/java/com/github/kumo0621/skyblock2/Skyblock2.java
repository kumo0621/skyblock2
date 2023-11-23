package com.github.kumo0621.skyblock2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

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
        if (cmd.getName().equalsIgnoreCase("warp") && sender instanceof Player) {
            Player player = (Player) sender;
            FileConfiguration config = this.getConfig();
            String role = config.getString("players." + player.getUniqueId().toString());

            // 役職に応じたロケーションを取得
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
        return false;
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
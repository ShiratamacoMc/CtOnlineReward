package cn.ctcraft.ctonlinereward.command;

import cn.ctcraft.ctonlinereward.CtOnlineReward;
import cn.ctcraft.ctonlinereward.database.DataMigration;
import cn.ctcraft.ctonlinereward.service.RemindTimer;
import cn.ctcraft.ctonlinereward.utils.MessageUtil;
import cn.ctcraft.ctonlinereward.utils.SchedulerAdapter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CommandHandler implements CommandExecutor {
    private static final CommandHandler instance = new CommandHandler();
    private CtOnlineReward ctOnlineReward = CtOnlineReward.getPlugin(CtOnlineReward.class);
    private CommandExecute commandExecute = CommandExecute.getInstance();

    public static CommandHandler getInstance() {
        return instance;
    }

    private CommandHandler() {

    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("cor")) {
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("CtOnlineReward.cor")) {
                    MessageUtil.sendLang(sender, "command.no-permission");
                    return true;
                }
                commandExecute.openInventory(player, new String[]{"1"});
            } else {
                sender.sendMessage(ctOnlineReward.getDescription().getName());
                sender.sendMessage(ctOnlineReward.getDescription().getVersion());
            }
        } else {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "open":
                    commandExecute.openInventory(sender, args);
                    break;
                case "reload":
                    if (args.length == 1 && sender.hasPermission("CtOnlineReward.reload")) {
                        reload();
                        MessageUtil.sendLang(sender, "command.reload-success");
                    }
                    break;
                case "remind":
                    if (args.length == 2) {
                        String state = args[1].toLowerCase();
                        if (state.equals("on")) {
                            toggleRemind((Player) sender, true);
                        } else if (state.equals("off")) {
                            toggleRemind((Player) sender, false);
                        }
                    }
                    break;
                case "migrate":
                    if (sender.hasPermission("CtOnlineReward.admin")) {
                        migrateData(sender);
                    } else {
                        MessageUtil.sendLang(sender, "command.no-permission");
                    }
                    break;
                case "migrateyaml":
                    if (sender.hasPermission("CtOnlineReward.admin")) {
                        migrateYamlData(sender);
                    } else {
                        MessageUtil.sendLang(sender, "command.no-permission");
                    }
                    break;
                case "dropoldtables":
                    if (sender.hasPermission("CtOnlineReward.admin")) {
                        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
                            dropOldTables(sender);
                        } else {
                            MessageUtil.sendLang(sender, "migration.drop-warning");
                            MessageUtil.sendLang(sender, "migration.drop-confirm");
                            MessageUtil.sendLang(sender, "migration.drop-backup");
                        }
                    } else {
                        MessageUtil.sendLang(sender, "command.no-permission");
                    }
                    break;
                case "time":
                    if (sender instanceof Player) {
                        showOnlineTime((Player) sender);
                    } else {
                        MessageUtil.sendLang(sender, "command.player-only");
                    }
                    break;
            }
        }

        return true;
    }

    private void reload() {
        ctOnlineReward.load();
        CtOnlineReward.placeholder.loadPapiJson();
    }

    private void toggleRemind(Player player, boolean enable) {
        List<Player> players = RemindTimer.players;
        if (enable) {
            players.remove(player);
            MessageUtil.sendLang(player, "remind.enabled");
        } else {
            players.add(player);
            MessageUtil.sendLang(player, "remind.disabled");
        }
    }

    private void migrateData(CommandSender sender) {
        MessageUtil.sendLang(sender, "migration.start-old-tables");
        String dbType = ctOnlineReward.getConfig().getString("database.type", "sqlite");
        
        SchedulerAdapter.runTaskAsynchronously(ctOnlineReward, () -> {
            try {
                DataMigration migration = new DataMigration();
                if (dbType.equalsIgnoreCase("mysql")) {
                    migration.migrateMysqlData();
                } else {
                    migration.migrateSQLiteData();
                }
                MessageUtil.sendLang(sender, "migration.success");
            } catch (Exception e) {
                MessageUtil.sendLang(sender, "migration.failed", 
                    MessageUtil.placeholders("error", e.getMessage()));
                e.printStackTrace();
            }
        });
    }

    private void migrateYamlData(CommandSender sender) {
        MessageUtil.sendLang(sender, "migration.start-yaml");
        
        SchedulerAdapter.runTaskAsynchronously(ctOnlineReward, () -> {
            try {
                DataMigration migration = new DataMigration();
                int count = migration.migrateYamlToDatabase();
                MessageUtil.sendLang(sender, "migration.success-count", 
                    MessageUtil.placeholders("count", String.valueOf(count)));
            } catch (Exception e) {
                MessageUtil.sendLang(sender, "migration.failed", 
                    MessageUtil.placeholders("error", e.getMessage()));
                e.printStackTrace();
            }
        });
    }

    private void dropOldTables(CommandSender sender) {
        MessageUtil.sendLang(sender, "migration.drop-start");
        
        SchedulerAdapter.runTaskAsynchronously(ctOnlineReward, () -> {
            try {
                DataMigration migration = new DataMigration();
                migration.dropOldTables();
                MessageUtil.sendLang(sender, "migration.drop-success");
            } catch (Exception e) {
                MessageUtil.sendLang(sender, "migration.drop-failed", 
                    MessageUtil.placeholders("error", e.getMessage()));
                e.printStackTrace();
            }
        });
    }
    
    private void showOnlineTime(Player player) {
        SchedulerAdapter.runTaskAsynchronously(ctOnlineReward, () -> {
            try {
                int dayTime = CtOnlineReward.dataService.getPlayerOnlineTime(player);
                int weekTime = CtOnlineReward.dataService.getPlayerOnlineTimeWeek(player);
                int monthTime = CtOnlineReward.dataService.getPlayerOnlineTimeMonth(player);
                int allTime = CtOnlineReward.dataService.getPlayerOnlineTimeAll(player);
                
                // 同步发送消息
                SchedulerAdapter.runTask(ctOnlineReward, () -> {
                    MessageUtil.sendLang(player, "time.header");
                    MessageUtil.sendLang(player, "time.day", 
                        MessageUtil.placeholders("time", formatTime(dayTime)));
                    MessageUtil.sendLang(player, "time.week", 
                        MessageUtil.placeholders("time", formatTime(weekTime)));
                    MessageUtil.sendLang(player, "time.month", 
                        MessageUtil.placeholders("time", formatTime(monthTime)));
                    MessageUtil.sendLang(player, "time.all", 
                        MessageUtil.placeholders("time", formatTime(allTime)));
                    MessageUtil.sendLang(player, "time.footer");
                });
            } catch (Exception e) {
                SchedulerAdapter.runTask(ctOnlineReward, () -> {
                    MessageUtil.sendLang(player, "time.error");
                });
                e.printStackTrace();
            }
        });
    }
    
    private String formatTime(int minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours < 24) {
            return hours + " 小时 " + mins + " 分钟";
        }
        int days = hours / 24;
        hours = hours % 24;
        return days + " 天 " + hours + " 小时 " + mins + " 分钟";
    }
}


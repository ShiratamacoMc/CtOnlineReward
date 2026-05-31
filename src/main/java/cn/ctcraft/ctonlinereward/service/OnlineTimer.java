package cn.ctcraft.ctonlinereward.service;

import cn.ctcraft.ctonlinereward.CtOnlineReward;
import cn.ctcraft.ctonlinereward.database.DataService;
import cn.ctcraft.ctonlinereward.database.YamlData;
import cn.ctcraft.ctonlinereward.pojo.OnlineRemind;
import cn.ctcraft.ctonlinereward.service.afk.AfkService;
import cn.ctcraft.ctonlinereward.utils.ConfigUtil;
import cn.ctcraft.ctonlinereward.utils.MessageUtil;
import cn.ctcraft.ctonlinereward.utils.SchedulerAdapter;
import cn.ctcraft.ctonlinereward.utils.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineTimer implements Runnable {
    private static HashMap<UUID, Long> onlinePlayerTime = new HashMap<>();
    private static OnlineTimer instance = new OnlineTimer();
    private DataService dataService = CtOnlineReward.dataService;
    private CtOnlineReward ctOnlineReward = CtOnlineReward.getPlugin(CtOnlineReward.class);
    private BukkitTask task;
    
    // 缓存配置，避免每次循环都读取
    private List<OnlineRemind> onlineRemindList = null;
    private boolean onlineRemindEnabled = false;
    private long lastConfigCheck = 0;
    private static final long CONFIG_CHECK_INTERVAL = 60000; // 1分钟检查一次配置

    private OnlineTimer() {
        //插件可能在服务器正常开启后启用 此时手动添加在线玩家
        for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
            if (!onlinePlayerTime.containsKey(onlinePlayer.getUniqueId())){
                addOnlinePlayer(onlinePlayer,System.currentTimeMillis());
            }
        }
        loadConfig();
    }
    
    private void loadConfig() {
        onlineRemindEnabled = ctOnlineReward.getConfig().getBoolean("Setting.onlineRemind.use");
        if (onlineRemindEnabled) {
            try {
                onlineRemindList = ConfigUtil.getObjectList(ctOnlineReward.getConfig(), "Setting.onlineRemind.remindValues", OnlineRemind.class);
            } catch (Exception e) {
                e.printStackTrace();
                onlineRemindList = new ArrayList<>();
            }
        }
        lastConfigCheck = System.currentTimeMillis();
    }

    public static OnlineTimer getInstance() {
        return instance;
    }

    public static void addOnlinePlayer(Player player, Long time) {
        onlinePlayerTime.put(player.getUniqueId(), time);
    }

    public static void removeOnlinePlayer(Player player) {
        onlinePlayerTime.remove(player.getUniqueId());
    }

    /**
     * 启动定时器
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = SchedulerAdapter.runTaskTimerAsynchronously(ctOnlineReward, this, 1200, 1200);
    }

    /**
     * 停止定时器
     */
    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void run() {
        // 定期重新加载配置
        if (System.currentTimeMillis() - lastConfigCheck > CONFIG_CHECK_INTERVAL) {
            loadConfig();
        }
        
        Collection<? extends Player> onlinePlayers = Bukkit.getServer().getOnlinePlayers();
        
        // 批量更新的数据
        Map<Player, Integer> playersToUpdate = new HashMap<>();
        
        for (Player player : onlinePlayers) {
            if (!onlinePlayerTime.containsKey(player.getUniqueId())) {
                continue;
            }
            if (YamlData.timeLimit[0] != -1){
                long nowTime = System.currentTimeMillis();
                int i = Util.timestampToHours(nowTime);
                if(i < YamlData.timeLimit[0] || i > YamlData.timeLimit[1]){
                    continue;
                }
            }

            if (AfkService.getInstance().isAfk(player)) {
                continue;
            }

            long playerOnlineTime = onlinePlayerTime.get(player.getUniqueId());
            long timePast =  System.currentTimeMillis() - playerOnlineTime;
            
            if (timePast > 60 * 1000) {
                int numMinutes = dataService.getPlayerOnlineTime(player);
                numMinutes += ((Long) (timePast / (60 * 1000))).intValue();
                
                // 添加到批量更新列表
                playersToUpdate.put(player, numMinutes);

                long newTime = System.currentTimeMillis() - (timePast % 60000);
                onlinePlayerTime.put(player.getUniqueId(), newTime);
                
                // 在线提醒（使用缓存的配置）
                if (onlineRemindEnabled && onlineRemindList != null) {
                    for (OnlineRemind remind : onlineRemindList) {
                        if (numMinutes == remind.getOnlineTime()) {
                            MessageUtil.send(player, remind.getMessage());
                        }
                    }
                }
            }
        }
        
        // 批量更新数据库（如果有数据需要更新）
        if (!playersToUpdate.isEmpty()) {
            for (Map.Entry<Player, Integer> entry : playersToUpdate.entrySet()) {
                dataService.addPlayerOnlineTime(entry.getKey(), entry.getValue());
            }
        }
    }
}

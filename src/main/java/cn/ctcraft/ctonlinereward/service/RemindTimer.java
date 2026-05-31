package cn.ctcraft.ctonlinereward.service;

import cn.ctcraft.ctonlinereward.CtOnlineReward;
import cn.ctcraft.ctonlinereward.database.YamlData;
import cn.ctcraft.ctonlinereward.service.rewardHandler.RewardOnlineTimeHandler;
import cn.ctcraft.ctonlinereward.utils.MessageUtil;
import cn.ctcraft.ctonlinereward.utils.SchedulerAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RemindTimer implements Runnable {
    private final CtOnlineReward ctOnlineReward;
    private BukkitTask task;
    //每轮检查时已经提醒过的玩家的名单
     public static List<Player> players = new ArrayList<>();

    public RemindTimer() {
        ctOnlineReward = CtOnlineReward.getPlugin(CtOnlineReward.class);
    }

    /**
     * 启动定时器
     */
    public void start(int intervalMinutes) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        long intervalTicks = intervalMinutes * 60 * 20L; // 分钟转换为 tick
        task = SchedulerAdapter.runTaskTimerAsynchronously(ctOnlineReward, this, intervalTicks, intervalTicks);
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
        JsonArray remindJson = YamlData.remindJson;
        //清除提醒过的玩家的名单
        players.clear();
        for (JsonElement jsonElement : remindJson) {
            JsonObject asJsonObject = jsonElement.getAsJsonObject();
            if (asJsonObject.has("remind") && asJsonObject.get("remind").getAsBoolean()) {
                String reward = asJsonObject.get("reward").getAsString();
                String permission = asJsonObject.has("permission") ? asJsonObject.get("permission").getAsString() : null;
                sendMessage(permission, reward);
            }
        }

    }

    private void sendMessage(String rewardId) {
        sendMessage(null,rewardId);
    }

    private void sendMessage(String permission, String rewardId) {
        FileConfiguration config = ctOnlineReward.getConfig();
        String message = config.getString("Setting.remind.message");
        if (message == null || message.isEmpty()) {
            return;
        }
        
        // 先收集所有符合条件的玩家
        List<Player> eligiblePlayers = new ArrayList<>();
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // 检查权限
            if (permission != null && !onlinePlayer.hasPermission(permission)) {
                continue;
            }
            // 检查是否已提醒
            if (players.contains(onlinePlayer)) {
                continue;
            }
            // 检查是否已领取奖励
            if (hasNotReceivedReward(onlinePlayer, rewardId)) {
                eligiblePlayers.add(onlinePlayer);
            }
        }
        
        // 批量发送消息
        for (Player player : eligiblePlayers) {
            players.add(player);
            MessageUtil.send(player, message);
        }
    }

    private boolean hasNotReceivedReward(Player player, String rewardId) {
        YamlConfiguration rewardYaml = YamlData.rewardYaml;
        ConfigurationSection configurationSection = rewardYaml.getConfigurationSection(rewardId);
        if (configurationSection == null || !configurationSection.contains("time")) {
            return false;
        }
        boolean timeIsOk = RewardOnlineTimeHandler.getInstance().onlineTimeIsOk(player, configurationSection.getString("time"));
        if (timeIsOk) {
            List<String> playerRewardArray = CtOnlineReward.dataService.getPlayerRewardArray(player);
            return playerRewardArray.isEmpty() || !playerRewardArray.contains(rewardId);
        }
        return false;
    }
}

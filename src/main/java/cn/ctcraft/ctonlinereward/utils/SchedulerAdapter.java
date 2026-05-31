package cn.ctcraft.ctonlinereward.utils;

import cn.ctcraft.ctonlinereward.CtOnlineReward;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * 调度器适配器，用于兼容 Bukkit 和 Folia
 */
public class SchedulerAdapter {
    private static final boolean IS_FOLIA;

    static {
        // 简单高效的 Folia 检测
        boolean isFolia = false;
        try {
            isFolia = Class.forName("io.papermc.paper.threadedregions.RegionizedServer") != null;
        } catch (Exception e) {
            // 不是 Folia
        }
        IS_FOLIA = isFolia;
    }

    /**
     * 检查是否运行在 Folia 上
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * 运行同步任务（延迟执行）
     */
    public static BukkitTask runTask(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            if (delay <= 0) {
                delay = 1;
            }
            ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask1 -> task.run(), delay);
            return new FoliaTaskWrapper(scheduledTask);
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /**
     * 运行同步任务（立即执行）
     */
    public static BukkitTask runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            return new FoliaTaskWrapper(null); // 立即执行的任务没有返回值
        } else {
            return Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 运行同步定时任务
     */
    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask1 -> task.run(), delay, period);
            return new FoliaTaskWrapper(scheduledTask);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    /**
     * 运行异步任务（延迟执行）
     */
    public static BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            // Folia 中异步任务延迟单位是毫秒
            long delayMs = delay * 50; // 1 tick = 50ms
            ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask1 -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            return new FoliaTaskWrapper(scheduledTask);
        } else {
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    /**
     * 运行异步任务（立即执行）
     */
    public static BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return new FoliaTaskWrapper(null); // 立即执行的任务没有返回值
        } else {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * 运行异步定时任务
     */
    public static BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            // Folia 中异步任务延迟和周期单位是毫秒
            long delayMs = delay * 50; // 1 tick = 50ms
            long periodMs = period * 50; // 1 tick = 50ms
            ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask1 -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
            return new FoliaTaskWrapper(scheduledTask);
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        }
    }

    /**
     * Folia 任务包装器，用于兼容 BukkitTask 接口
     */
    private static class FoliaTaskWrapper implements BukkitTask {
        private final ScheduledTask scheduledTask;
        private boolean cancelled = false;

        public FoliaTaskWrapper(ScheduledTask scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public int getTaskId() {
            return -1; // Folia 任务没有 ID
        }

        @Override
        public Plugin getOwner() {
            return CtOnlineReward.getPlugin(CtOnlineReward.class);
        }

        @Override
        public boolean isSync() {
            return true; // 简化处理
        }

        @Override
        public boolean isCancelled() {
            return cancelled || (scheduledTask != null && scheduledTask.isCancelled());
        }

        @Override
        public void cancel() {
            if (scheduledTask != null && !cancelled) {
                scheduledTask.cancel();
                cancelled = true;
            }
        }
    }
}
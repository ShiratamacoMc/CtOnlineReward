package cn.ctcraft.ctonlinereward.utils;

import cn.ctcraft.ctonlinereward.CtOnlineReward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 调度器适配器，用于兼容 Bukkit 和 Folia
 */
public class SchedulerAdapter {
    private static final boolean IS_FOLIA;
    private static Method getGlobalRegionSchedulerMethod;
    private static Method getAsyncSchedulerMethod;
    private static Method getEntitySchedulerMethod;
    private static Method runDelayedMethod;
    private static Method runAtFixedRateMethod;
    private static Method runDelayedAsyncMethod;
    private static Method runAtFixedRateAsyncMethod;
    private static Object globalRegionScheduler;
    private static Object asyncScheduler;

    static {
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            isFolia = true;
            
            // 获取 Folia 调度器相关的方法和对象
            Class<?> serverClass = Class.forName("org.bukkit.Bukkit");
            getGlobalRegionSchedulerMethod = serverClass.getMethod("getGlobalRegionScheduler");
            getAsyncSchedulerMethod = serverClass.getMethod("getAsyncScheduler");
            getEntitySchedulerMethod = Entity.class.getMethod("getScheduler");
            
            globalRegionScheduler = getGlobalRegionSchedulerMethod.invoke(null);
            asyncScheduler = getAsyncSchedulerMethod.invoke(null);
            
            // 获取调度方法
            Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            
            runDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Runnable.class, long.class);
            runAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Runnable.class, long.class, long.class);
            runDelayedAsyncMethod = asyncSchedulerClass.getMethod("runDelayed", Plugin.class, Runnable.class, long.class, TimeUnit.class);
            runAtFixedRateAsyncMethod = asyncSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Runnable.class, long.class, long.class, TimeUnit.class);
            
        } catch (Exception e) {
            // Folia 不可用，使用 Bukkit
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
            try {
                Object scheduledTask = runDelayedMethod.invoke(globalRegionScheduler, plugin, task, delay);
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule Folia task, falling back to Bukkit: " + e.getMessage());
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /**
     * 运行同步任务（立即执行）
     */
    public static BukkitTask runTask(Plugin plugin, Runnable task) {
        return runTask(plugin, task, 0L);
    }

    /**
     * 运行同步定时任务
     */
    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                Object scheduledTask = runAtFixedRateMethod.invoke(globalRegionScheduler, plugin, task, delay, period);
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule Folia timer task, falling back to Bukkit: " + e.getMessage());
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    /**
     * 运行异步任务（延迟执行）
     */
    public static BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                // Folia 中异步任务延迟单位是毫秒
                long delayMs = delay * 50; // 1 tick = 50ms
                Object scheduledTask = runDelayedAsyncMethod.invoke(asyncScheduler, plugin, task, delayMs, TimeUnit.MILLISECONDS);
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule Folia async task, falling back to Bukkit: " + e.getMessage());
                return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    /**
     * 运行异步任务（立即执行）
     */
    public static BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return runTaskAsynchronously(plugin, task, 0L);
    }

    /**
     * 运行异步定时任务
     */
    public static BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                // Folia 中异步任务延迟和周期单位是毫秒
                long delayMs = delay * 50; // 1 tick = 50ms
                long periodMs = period * 50; // 1 tick = 50ms
                Object scheduledTask = runAtFixedRateAsyncMethod.invoke(asyncScheduler, plugin, task, delayMs, periodMs, TimeUnit.MILLISECONDS);
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule Folia async timer task, falling back to Bukkit: " + e.getMessage());
                return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        }
    }

    /**
     * Folia 任务包装器，用于兼容 BukkitTask 接口
     */
    private static class FoliaTaskWrapper implements BukkitTask {
        private final Object foliaTask;
        private boolean cancelled = false;

        public FoliaTaskWrapper(Object foliaTask) {
            this.foliaTask = foliaTask;
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
            return cancelled;
        }

        @Override
        public void cancel() {
            if (foliaTask != null && !cancelled) {
                try {
                    Method cancelMethod = foliaTask.getClass().getMethod("cancel");
                    cancelMethod.invoke(foliaTask);
                    cancelled = true;
                } catch (Exception e) {
                    // 忽略取消失败
                }
            }
        }
    }
}
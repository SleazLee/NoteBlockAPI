package com.xxmicloxx.NoteBlockAPI.utils;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for scheduling tasks with optional Folia support.
 */
public final class Scheduler {

    private static final boolean foliaEnvironment;

    static {
        boolean foliaDetected;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            foliaDetected = true;
            Bukkit.getLogger().info("Scheduler detected Folia environment. Using Folia schedulers.");
        } catch (ClassNotFoundException e) {
            foliaDetected = false;
            Bukkit.getLogger().info("Scheduler running on Bukkit/Paper environment. Using Bukkit scheduler.");
        }
        foliaEnvironment = foliaDetected;
    }

    private Scheduler() {
    }

    /**
     * Executes a task immediately on the appropriate scheduler.
     *
     * @param runnable task to execute
     */
    public static void run(Runnable runnable) {
        if (foliaEnvironment && tryRunFoliaGlobal(runnable)) {
            return;
        }
        Bukkit.getScheduler().runTask(getPlugin(), runnable);
    }

    /**
     * Executes a task after a delay on the appropriate scheduler.
     *
     * @param runnable   task to execute
     * @param delayTicks delay in ticks
     * @return scheduled task wrapper
     */
    public static Task runLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            run(runnable);
            return Task.empty();
        }
        if (foliaEnvironment) {
            Object task = tryRunFoliaGlobalDelayed(runnable, delayTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskLater(getPlugin(), runnable, delayTicks));
    }

    /**
     * Executes a repeating task on the appropriate scheduler.
     *
     * @param runnable    task to execute
     * @param delayTicks  initial delay in ticks
     * @param periodTicks repeat period in ticks
     * @return scheduled task wrapper
     */
    public static Task runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (foliaEnvironment) {
            Object task = tryRunFoliaGlobalTimer(runnable, delayTicks, periodTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(getPlugin(), runnable, delayTicks, periodTicks));
    }

    /**
     * Executes a task asynchronously.
     *
     * @param runnable task to execute
     */
    public static void runAsync(Runnable runnable) {
        if (foliaEnvironment && tryRunFoliaAsync(runnable)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), runnable);
    }

    /**
     * Executes a task asynchronously after a delay.
     *
     * @param runnable   task to execute
     * @param delayTicks delay in ticks
     * @return scheduled task wrapper
     */
    public static Task runAsyncLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            runAsync(runnable);
            return Task.empty();
        }
        if (foliaEnvironment) {
            Object task = tryRunFoliaAsyncDelayed(runnable, delayTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), runnable, delayTicks));
    }

    /**
     * Executes a repeating asynchronous task.
     *
     * @param runnable    task to execute
     * @param delayTicks  initial delay in ticks
     * @param periodTicks repeat period in ticks
     * @return scheduled task wrapper
     */
    public static Task runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (foliaEnvironment) {
            Object task = tryRunFoliaAsyncTimer(runnable, delayTicks, periodTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), runnable, delayTicks, periodTicks));
    }

    /**
     * Executes a task in the region scheduler based on the provided location.
     *
     * @param location region location
     * @param runnable task to execute
     */
    public static void run(Location location, Runnable runnable) {
        if (foliaEnvironment && tryRunFoliaRegion(location, runnable)) {
            return;
        }
        Bukkit.getScheduler().runTask(getPlugin(), runnable);
    }

    /**
     * Executes a delayed task in the region scheduler.
     *
     * @param location   region location
     * @param runnable   task to execute
     * @param delayTicks delay in ticks
     * @return scheduled task wrapper
     */
    public static Task runLater(Location location, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            run(location, runnable);
            return Task.empty();
        }
        if (foliaEnvironment) {
            Object task = tryRunFoliaRegionDelayed(location, runnable, delayTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskLater(getPlugin(), runnable, delayTicks));
    }

    /**
     * Executes a repeating region task.
     *
     * @param location    region location
     * @param runnable    task to execute
     * @param delayTicks  initial delay in ticks
     * @param periodTicks repeat period in ticks
     * @return scheduled task wrapper
     */
    public static Task runTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (foliaEnvironment) {
            Object task = tryRunFoliaRegionTimer(location, runnable, delayTicks, periodTicks);
            if (task != null) {
                return new Task(task);
            }
        }
        return new Task(Bukkit.getScheduler().runTaskTimer(getPlugin(), runnable, delayTicks, periodTicks));
    }

    /**
     * Checks whether the Folia scheduler will be used.
     *
     * @return true if Folia scheduler is active
     */
    public static boolean isFolia() {
        return foliaEnvironment;
    }

    /**
     * Cancels the current task. (Not implemented)
     */
    public static void cancelCurrentTask() {
    }

    private static Plugin getPlugin() {
        return NoteBlockAPI.getAPI();
    }

    private static Logger getLogger() {
        Plugin plugin = NoteBlockAPI.getAPI();
        return plugin != null ? plugin.getLogger() : Bukkit.getLogger();
    }

    private static Object tryInvoke(Method method, Object target, Object... args) throws InvocationTargetException, IllegalAccessException {
        if (method == null || target == null) {
            return null;
        }
        return method.invoke(target, args);
    }

    private static void logFoliaFailure(String action, Exception exception) {
        Logger logger = getLogger();
        logger.log(Level.WARNING, "Folia scheduler action '" + action + "' failed. Falling back to Bukkit scheduler.", exception);
    }

    private static Object tryRunFoliaGlobalDelayed(Runnable runnable, long delayTicks) {
        Object scheduler = getGlobalScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            Consumer<Object> consumer = task -> runnable.run();
            return tryInvoke(method, scheduler, getPlugin(), consumer, delayTicks);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("runDelayed", exception);
            return null;
        }
    }

    private static Object tryRunFoliaGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        Object scheduler = getGlobalScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Consumer<Object> consumer = task -> runnable.run();
            long initialDelay = Math.max(1L, delayTicks);
            return tryInvoke(method, scheduler, getPlugin(), consumer, initialDelay, periodTicks);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("runAtFixedRate", exception);
            return null;
        }
    }

    private static boolean tryRunFoliaGlobal(Runnable runnable) {
        Object scheduler = getGlobalScheduler();
        if (scheduler == null) {
            return false;
        }
        try {
            Method method = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            tryInvoke(method, scheduler, getPlugin(), runnable);
            return true;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("execute", exception);
            return false;
        }
    }

    private static Object tryRunFoliaAsyncDelayed(Runnable runnable, long delayTicks) {
        Object scheduler = getAsyncScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
            Consumer<Object> consumer = task -> runnable.run();
            long delayMillis = delayTicks * 50L;
            return tryInvoke(method, scheduler, getPlugin(), consumer, delayMillis, TimeUnit.MILLISECONDS);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("runAsyncDelayed", exception);
            return null;
        }
    }

    private static Object tryRunFoliaAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        Object scheduler = getAsyncScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            Consumer<Object> consumer = task -> runnable.run();
            long delayMillis = Math.max(1L, delayTicks) * 50L;
            long periodMillis = periodTicks * 50L;
            return tryInvoke(method, scheduler, getPlugin(), consumer, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("runAsyncTimer", exception);
            return null;
        }
    }

    private static boolean tryRunFoliaAsync(Runnable runnable) {
        Object scheduler = getAsyncScheduler();
        if (scheduler == null) {
            return false;
        }
        try {
            Method method = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            Consumer<Object> consumer = task -> runnable.run();
            tryInvoke(method, scheduler, getPlugin(), consumer);
            return true;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("runAsync", exception);
            return false;
        }
    }

    private static boolean tryRunFoliaRegion(Location location, Runnable runnable) {
        Object scheduler = getRegionScheduler();
        if (scheduler == null) {
            return false;
        }
        try {
            Method method = scheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);
            tryInvoke(method, scheduler, getPlugin(), location, runnable);
            return true;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("regionExecute", exception);
            return false;
        }
    }

    private static Object tryRunFoliaRegionDelayed(Location location, Runnable runnable, long delayTicks) {
        Object scheduler = getRegionScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            Consumer<Object> consumer = task -> runnable.run();
            return tryInvoke(method, scheduler, getPlugin(), location, consumer, delayTicks);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("regionRunDelayed", exception);
            return null;
        }
    }

    private static Object tryRunFoliaRegionTimer(Location location, Runnable runnable, long delayTicks, long periodTicks) {
        Object scheduler = getRegionScheduler();
        if (scheduler == null) {
            return null;
        }
        try {
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            Consumer<Object> consumer = task -> runnable.run();
            long initialDelay = Math.max(1L, delayTicks);
            return tryInvoke(method, scheduler, getPlugin(), location, consumer, initialDelay, periodTicks);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure("regionRunAtFixedRate", exception);
            return null;
        }
    }

    private static Object getGlobalScheduler() {
        return getScheduler("getGlobalRegionScheduler");
    }

    private static Object getAsyncScheduler() {
        return getScheduler("getAsyncScheduler");
    }

    private static Object getRegionScheduler() {
        return getScheduler("getRegionScheduler");
    }

    private static Object getScheduler(String methodName) {
        try {
            Method method = Bukkit.class.getMethod(methodName);
            return method.invoke(null);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
            logFoliaFailure(methodName, exception);
            return null;
        }
    }

    /**
     * Wrapper for scheduled tasks.
     */
    public static class Task {

        private final Object foliaTask;
        private final BukkitTask bukkitTask;

        Task(Object foliaTask) {
            this(foliaTask, null);
        }

        Task(BukkitTask bukkitTask) {
            this(null, bukkitTask);
        }

        Task(Object foliaTask, BukkitTask bukkitTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = bukkitTask;
        }

        static Task empty() {
            return new Task(null, null);
        }

        /**
         * Cancels the scheduled task if available.
         */
        public void cancel() {
            if (foliaTask != null) {
                try {
                    Method method = foliaTask.getClass().getMethod("cancel");
                    method.invoke(foliaTask);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
                    logFoliaFailure("cancel", exception);
                }
            } else if (bukkitTask != null) {
                bukkitTask.cancel();
            }
        }
    }
}

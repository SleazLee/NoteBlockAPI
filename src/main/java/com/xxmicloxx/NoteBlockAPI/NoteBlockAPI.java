package com.xxmicloxx.NoteBlockAPI;

import com.xxmicloxx.NoteBlockAPI.songplayer.SongPlayer;
import com.xxmicloxx.NoteBlockAPI.utils.MathUtils;
import com.xxmicloxx.NoteBlockAPI.utils.Scheduler;
import com.xxmicloxx.NoteBlockAPI.utils.Updater;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main class; contains methods for playing and adjusting songs for players
 */
public class NoteBlockAPI extends JavaPlugin {

        private static final ApiState STATE = new ApiState();

        private static final class ApiState {
                private final Map<UUID, ArrayList<SongPlayer>> playingSongs = new ConcurrentHashMap<>();
                private final Map<UUID, Byte> playerVolume = new ConcurrentHashMap<>();
                private final Map<Plugin, Boolean> dependentPlugins = new HashMap<>();

                private Scheduler.Task dependencyScanTask;
                private Scheduler.Task updateCheckTask;

                private boolean disabling = false;
                private boolean initialized = false;

                private NoteBlockAPI pluginInstance;
                private JavaPlugin owningPlugin;
        }

        private static void ensureInitialized(JavaPlugin plugin, boolean pluginManaged) {
                synchronized (STATE) {
                        if (STATE.initialized) {
                                return;
                        }

                        STATE.initialized = true;
                        STATE.owningPlugin = plugin;
                        if (pluginManaged && plugin instanceof NoteBlockAPI) {
                                STATE.pluginInstance = (NoteBlockAPI) plugin;
                        }

                        STATE.disabling = false;

                        STATE.dependentPlugins.clear();
                        for (Plugin pl : Bukkit.getServer().getPluginManager().getPlugins()) {
                                if (pl.getDescription().getDepend().contains("NoteBlockAPI")
                                                || pl.getDescription().getSoftDepend().contains("NoteBlockAPI")) {
                                        STATE.dependentPlugins.put(pl, false);
                                }
                        }

                        final Metrics metrics = pluginManaged ? new Metrics(plugin, 1083) : null;
                        final JavaPlugin owningPlugin = plugin;

                        new NoteBlockPlayerMain().onEnable();

                        STATE.dependencyScanTask = Scheduler.runLater(new Runnable() {

                                @Override
                                public void run() {
                                        Plugin[] plugins = Bukkit.getServer().getPluginManager().getPlugins();
                                        Type[] types = new Type[]{PlayerRangeStateChangeEvent.class, SongDestroyingEvent.class, SongEndEvent.class, SongStoppedEvent.class };
                                        for (Plugin pl : plugins) {
                                                ArrayList<RegisteredListener> rls = HandlerList.getRegisteredListeners(pl);
                                                for (RegisteredListener rl : rls) {
                                                        Method[] methods = rl.getListener().getClass().getDeclaredMethods();
                                                        for (Method m : methods) {
                                                                Type[] params = m.getParameterTypes();
                                                                param:
                                                                for (Type paramType : params) {
                                                                        for (Type type : types){
                                                                                if (paramType.equals(type)) {
                                                                                        synchronized (STATE) {
                                                                                                if (STATE.dependentPlugins.containsKey(pl)) {
                                                                                                        STATE.dependentPlugins.put(pl, true);
                                                                                                }
                                                                                        }
                                                                                        break param;
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }

                                        if (metrics != null) {
                                                metrics.addCustomChart(new DrilldownPie("deprecated", () -> {
                                                        Map<String, Map<String, Integer>> map = new HashMap<>();
                                                        synchronized (STATE) {
                                                                for (Plugin tracked : STATE.dependentPlugins.keySet()){
                                                                        String deprecated = STATE.dependentPlugins.get(tracked) ? "yes" : "no";
                                                                        Map<String, Integer> entry = new HashMap<>();
                                                                        entry.put(tracked.getDescription().getFullName(), 1);
                                                                        map.put(deprecated, entry);
                                                                }
                                                        }
                                                        return map;
                                                }));
                                        }
                                }
                        }, 1);

                        if (pluginManaged) {
                                STATE.updateCheckTask = Scheduler.runAsyncTimer(new Runnable() {

                                        @Override
                                        public void run() {
                                                try {
                                                        if (Updater.checkUpdate("19287", owningPlugin.getDescription().getVersion())){
                                                                Bukkit.getLogger().info(String.format("[%s] New update available!", owningPlugin.getDescription().getName()));
                                                        }
                                                } catch (IOException e) {
                                                        Bukkit.getLogger().info(String.format("[%s] Cannot receive update from Spigot resource page!", owningPlugin.getDescription().getName()));
                                                }
                                        }
                                }, 20*10, 20 * 60 * 60 * 24);
                        }
                }
        }

        private static void shutdownInternal() {
                synchronized (STATE) {
                        if (!STATE.initialized) {
                                return;
                        }

                        STATE.disabling = true;
                        if (STATE.dependencyScanTask != null) {
                                STATE.dependencyScanTask.cancel();
                                STATE.dependencyScanTask = null;
                        }

                        if (STATE.updateCheckTask != null) {
                                STATE.updateCheckTask.cancel();
                                STATE.updateCheckTask = null;
                        }

                        if (!Scheduler.isFolia() && STATE.owningPlugin != null) {
                                Bukkit.getScheduler().cancelTasks(STATE.owningPlugin);
                                List<BukkitWorker> workers = Bukkit.getScheduler().getActiveWorkers();
                                for (BukkitWorker worker : workers){
                                        if (!worker.getOwner().equals(STATE.owningPlugin))
                                                continue;
                                        worker.getThread().interrupt();
                                }
                        }
                        if (NoteBlockPlayerMain.plugin != null) {
                                NoteBlockPlayerMain.plugin.onDisable();
                        }

                        STATE.playingSongs.clear();
                        STATE.playerVolume.clear();
                        STATE.dependentPlugins.clear();

                        STATE.pluginInstance = null;
                        STATE.owningPlugin = null;
                        STATE.initialized = false;
                        STATE.disabling = false;
                }
        }

	/**
	 * Returns true if a Player is currently receiving a song
	 * @param player
	 * @return is receiving a song
	 */
	public static boolean isReceivingSong(Player player) {
		return isReceivingSong(player.getUniqueId());
	}

	/**
	 * Returns true if a Player with specified UUID is currently receiving a song
	 * @param uuid
	 * @return is receiving a song
	 */
        public static boolean isReceivingSong(UUID uuid) {
                ArrayList<SongPlayer> songs = STATE.playingSongs.get(uuid);
                return (songs != null && !songs.isEmpty());
        }

	/**
	 * Stops the song for a Player
	 * @param player
	 */
	public static void stopPlaying(Player player) {
		stopPlaying(player.getUniqueId());
	}

	/**
	 * Stops the song for a Player
	 * @param uuid
	 */
        public static void stopPlaying(UUID uuid) {
                ArrayList<SongPlayer> songs = STATE.playingSongs.get(uuid);
                if (songs == null) {
                        return;
                }
                for (SongPlayer songPlayer : songs) {
                        songPlayer.removePlayer(uuid);
                }
        }

	/**
	 * Sets the volume for a given Player
	 * @param player
	 * @param volume
	 */
	public static void setPlayerVolume(Player player, byte volume) {
		setPlayerVolume(player.getUniqueId(), volume);
	}

	/**
	 * Sets the volume for a given Player
	 * @param uuid
	 * @param volume
	 */
        public static void setPlayerVolume(UUID uuid, byte volume) {
                STATE.playerVolume.put(uuid, volume);
        }

	/**
	 * Gets the volume for a given Player
	 * @param player
	 * @return volume (byte)
	 */
	public static byte getPlayerVolume(Player player) {
		return getPlayerVolume(player.getUniqueId());
	}

	/**
	 * Gets the volume for a given Player
	 * @param uuid
	 * @return volume (byte)
	 */
	public static byte getPlayerVolume(UUID uuid) {
                Byte byteObj = STATE.playerVolume.get(uuid);
                if (byteObj == null) {
                        byteObj = 100;
                        STATE.playerVolume.put(uuid, byteObj);
                }
                return byteObj;
        }
	
	public static ArrayList<SongPlayer> getSongPlayersByPlayer(Player player){
		return getSongPlayersByPlayer(player.getUniqueId());
	}
	
	public static ArrayList<SongPlayer> getSongPlayersByPlayer(UUID player){
                return STATE.playingSongs.get(player);
        }
	
	public static void setSongPlayersByPlayer(Player player, ArrayList<SongPlayer> songs){
		setSongPlayersByPlayer(player.getUniqueId(), songs);
	}
	
        public static void setSongPlayersByPlayer(UUID player, ArrayList<SongPlayer> songs){
                STATE.playingSongs.put(player, songs);
        }

        public static void initializeAPI(JavaPlugin plugin) {
                ensureInitialized(plugin, false);
        }

        public static void shutdownAPI() {
                shutdownInternal();
        }

        @Override
        public void onEnable() {
                ensureInitialized(this, true);
        }

        @Override
        public void onDisable() {
                shutdownInternal();
        }

	public void doSync(Runnable runnable) {
	Scheduler.run(runnable);
	}

	public void doAsync(Runnable runnable) {
	Scheduler.runAsync(runnable);
	}

	public boolean isDisabling() {
	return STATE.disabling;
	}

	public static void runSync(Runnable runnable) {
	Scheduler.run(runnable);
	}

	public static void runAsync(Runnable runnable) {
	Scheduler.runAsync(runnable);
	}

        public static boolean isDisablingAPI() {
                synchronized (STATE) {
                        return STATE.disabling;
                }
        }

        public static NoteBlockAPI getAPI(){
                return STATE.pluginInstance;
        }

        public static Plugin getOwningPlugin() {
                synchronized (STATE) {
                        return STATE.owningPlugin;
                }
        }

        protected void handleDeprecated(StackTraceElement[] ste){
		int pom = 1;
		String clazz = ste[pom].getClassName();
		while (clazz.startsWith("com.xxmicloxx.NoteBlockAPI")){
			pom++;
			clazz = ste[pom].getClassName();
		}
		String[] packageParts = clazz.split("\\.");
                ArrayList<Plugin> plugins = new ArrayList<Plugin>();
                synchronized (STATE) {
                        plugins.addAll(STATE.dependentPlugins.keySet());
                }
		
		ArrayList<Plugin> notResult = new ArrayList<Plugin>();
		parts:
		for (int i = 0; i < packageParts.length - 1; i++){
			
			for (Plugin pl : plugins){
				if (notResult.contains(pl)){ continue;}
				if (plugins.size() - notResult.size() == 1){
					break parts;
				}
				String[] plParts = pl.getDescription().getMain().split("\\.");
				if (!packageParts[i].equalsIgnoreCase(plParts[i])){
					notResult.add(pl);
					continue;
				}
			}
			plugins.removeAll(notResult);
			notResult.clear();
		}
		
		plugins.removeAll(notResult);
		notResult.clear();
		if (plugins.size() == 1){
                        synchronized (STATE) {
                                STATE.dependentPlugins.put(plugins.get(0), true);
                        }
                }
        }

}

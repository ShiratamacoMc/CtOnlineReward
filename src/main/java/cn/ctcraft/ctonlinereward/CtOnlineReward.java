package cn.ctcraft.ctonlinereward;

import cn.ctcraft.ctonlinereward.command.CommandHandler;
import cn.ctcraft.ctonlinereward.command.TabCompleter;
import cn.ctcraft.ctonlinereward.database.*;
import cn.ctcraft.ctonlinereward.listner.InventoryMonitor;
import cn.ctcraft.ctonlinereward.listner.PlayerMonitor;
import cn.ctcraft.ctonlinereward.service.OnlineTimer;
import cn.ctcraft.ctonlinereward.service.RemindTimer;
import cn.ctcraft.ctonlinereward.service.YamlService;
import cn.ctcraft.ctonlinereward.service.afk.AfkService;
import cn.ctcraft.ctonlinereward.service.afk.AfkTimer;
import cn.ctcraft.ctonlinereward.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class CtOnlineReward extends JavaPlugin {
    public static Economy economy = null;
    public static DataService dataService;
    public static Placeholder placeholder;
    public static HikariCPBase hikariCPBase;
    public static YamlConfiguration lang;
    public static LanguageHandler languageHandler;
    public static YamlConfiguration placeholderYaml;


    @Override
    public void onEnable() {
        final long timestamp = System.currentTimeMillis();

        this.getCommand("cor").setExecutor(CommandHandler.getInstance());
        this.getCommand("cor").setTabCompleter(TabCompleter.getInstance());
        getServer().getPluginManager().registerEvents(new InventoryMonitor(), this);
        getServer().getPluginManager().registerEvents(new PlayerMonitor(), this);

        // 先加载配置和语言文件
        load();
        
        // 初始化 MessageUtil（需要在 languageHandler 之后）
        MessageUtil.init(this);

        Metrics metrics = new Metrics(this);

        String databaseType = getConfig().getString("database.type", "sqlite");
        if (databaseType.equalsIgnoreCase("mysql")) {
            hikariCPBase = new HikariCPBase();
            dataService = new MysqlBase();
            getLogger().info("[CtOnlineReward] MySQL 数据库初始化成功!");
        } else {
            // 默认使用 SQLite
            if (!databaseType.equalsIgnoreCase("sqlite")) {
                getLogger().warning("[CtOnlineReward] 未知的数据库类型: " + databaseType + "，将使用 SQLite");
            }
            hikariCPBase = new HikariCPBase();
            dataService = new SQLiteBase();
            getLogger().info("[CtOnlineReward] SQLite 数据库初始化成功!");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholder = new Placeholder();
            placeholder.register();
        } else {
            getLogger().info("[CtOnlineReward] 未找到PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            getLogger().info("[CtOnlineReward] 获取PlayerPoints成功!");
        }

        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
            .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            getLogger().warning("[CtOnlineReward] 初始化Vault失败.");
        }

        OnlineTimer.getInstance().start();

        boolean afk = getConfig().getBoolean("Setting.afkConfig.use");
        if (afk) {
            int time = getConfig().getInt("Setting.afkConfig.time");
            String string = getConfig().getString("Setting.afkConfig.mode");
            if (string.equalsIgnoreCase("strong")) {
                AfkService.getInstance().openStrongMode();
            }
            new AfkTimer().start(time);
        }
        
        boolean onlineRemind = getConfig().getBoolean("Setting.remind.use");
        if (onlineRemind) {
            int anInt = getConfig().getInt("Setting.remind.time");
            new RemindTimer().start(anInt);
        }

        boolean useTimeLimit = getConfig().getBoolean("Setting.timeLimit.use");
        if (useTimeLimit) {
            getLogger().info("[CtOnlineReward] 在线时间限制已开启!");
            String limit = getConfig().getString("Setting.timeLimit.limit");
            String[] s = limit.split(",");
            YamlData.timeLimit[0] = Integer.parseInt(s[0]);
            YamlData.timeLimit[1] = Integer.parseInt(s[1]);
        }

        final long time = System.currentTimeMillis() - timestamp;
        getLogger().info("[CtOnlineReward] 插件加载完成,共耗时 " + time + "ms.");
    }

    public void load() {
        // 重载配置文件
        reloadConfig();
        saveDefaultConfig();

        // 先加载语言文件
        File langFile = new File(getDataFolder() + "/lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        }

        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        try {
            yamlConfiguration.load(langFile);
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException("lang.yml文件加载失败!", e);
        }
        lang = yamlConfiguration;
        languageHandler = new LanguageHandler();

        // 加载 GUI 配置
        YamlService yamlService = YamlService.getInstance();
        try {
            boolean b = yamlService.loadGuiYaml();
            if (b) {
                getLogger().info("[CtOnlineReward] Gui配置文件加载成功,共加载" + YamlData.guiYaml.size() + "个配置文件!");
            }
        } catch (Exception e) {
            getLogger().warning("[CtOnlineReward] Gui配置文件加载失败!");
            e.printStackTrace();
        }

        // 加载奖励配置
        boolean b2 = yamlService.loadRewardYaml();
        if (b2) {
            getLogger().info("[CtOnlineReward] 奖励配置文件加载成功!");
        }

        // 加载 PlaceholderAPI 配置
        File placeholderFile = new File(getDataFolder() + "/placeholder.yml");
        if (!placeholderFile.exists()) {
            saveResource("placeholder.yml", false);
        }
        placeholderYaml = new YamlConfiguration();
        try {
            placeholderYaml.load(placeholderFile);
            getLogger().info("[CtOnlineReward] papi配置文件加载成功!");
        } catch (Exception e) {
            getLogger().warning("[CtOnlineReward] papi变量配置文件加载失败!");
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        MessageUtil.close();
    }

    public PlayerPoints getPlayerPoints() {
        return (PlayerPoints) Bukkit.getPluginManager().getPlugin("PlayerPoints");
    }


}

package cz.coffee.skjson.api;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import ch.njol.skript.util.Version;
import com.google.gson.JsonElement;
import cz.coffee.skjson.SkJson;
import cz.coffee.skjson.api.Cache.JsonCache;
import cz.coffee.skjson.api.Cache.JsonWatcher;
import cz.coffee.skjson.api.Update.UpdateCheck;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.utils.MinecraftVersion;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static cz.coffee.skjson.api.ConfigRecords.*;
import static cz.coffee.skjson.utils.Logger.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("ALL")
public class Config {
    /**
     * The constant cache.
     */
    final static JsonCache<String, JsonElement, File> cache = new JsonCache<>();
    private static final HashMap<String, String> mapping = new HashMap<>(Map.ofEntries(
            Map.entry("CONFIG_VERSION", "version"),
            Map.entry("PROJECT_DEBUG", "debug"),
            Map.entry("LOGGING_LEVEL", "logging-level"),
            Map.entry("DEFAULT_WATCHER_INTERVAL", "watcher-interval"),
            Map.entry("PLUGIN_PREFIX", "prefixes-plugin"),
            Map.entry("ERROR_PREFIX", "prefixes-error"),
            Map.entry("WATCHER_PREFIX", "prefixes-watcher"),
            Map.entry("REQUESTS_PREFIX", "prefixes-request"),
            Map.entry("WEBHOOK_PREFIX", "prefixes-webhook"),
            Map.entry("PATH_VARIABLE_DELIMITER", "path-delimiter")
    ));
    public static YamlConfiguration pluginYaml;
    public static ConcurrentHashMap<File, JsonWatcher> watcherCache = new ConcurrentHashMap<>();
    private static Config staticConfig;
    final SkJsonLogger logger;
    final JavaPlugin plugin;
    private final ArrayList<String> errors = new ArrayList<>();
    PluginManager manager;
    int version;
    private boolean ready = true;
    private File configFile;
    private FileConfiguration config;

    /**
     * Instantiates a new Config.
     *
     * @param plugin the plugin
     */
    public Config(JavaPlugin plugin) {
        this.plugin = plugin;
        logger = SkJsonLogger.getLogger("");
        MinecraftVersion.replaceLogger(this.logger);
        Config.staticConfig = this;
    }

    public static String getMapping(final String key) {
        if (mapping.containsKey(key)) {
            return mapping.get(key);
        }
        return null;
    }

    private static String convertStreamToString(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    /**
     * Gets config.
     *
     * @return the config
     */
    public static Config getConfig() {
        return staticConfig;
    }

    /**
     * Gets cache.
     *
     * @return the cache
     */
    public static JsonCache<String, JsonElement, File> getCache() {
        return cache;
    }

    /**
     * Load config file.
     *
     * @param replace the replace
     * @return the void
     */
    public String loadConfigFile(boolean replace, boolean... saveIncorect) {
        String wrongFile = "";
        if (saveIncorect != null && saveIncorect.length > 0 && saveIncorect[0]) {
            wrongFile = regenerateConfigFile();
        }
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", replace);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        matchConfig();
        loadConfigs();
        return wrongFile;
    }

    /**
     * Load config file.
     *
     * @param replace      the replace
     * @param saveIncorect true/false .. creating new config.
     * @param sender       true/false has sender
     * @return the void
     */
    public String loadConfigFile(boolean replace, CommandSender sender, boolean... saveIncorect) {
        String wrongFile = "";
        if (saveIncorect != null && saveIncorect.length > 0 && saveIncorect[0]) {
            wrongFile = regenerateConfigFile();
        }
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", replace);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        matchConfig();
        loadConfigs(sender);
        return wrongFile;
    }

    private String regenerateConfigFile() {
        String wrongFile = "";
        var c = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml")).saveToString();
        String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
        try {
            wrongFile = "broken_config_" + date + ".yml";
            Files.writeString(Path.of("./plugins/SkJson/", wrongFile), c, UTF_8);
            plugin.saveResource("config.yml", true);
        } catch (IOException fileException) {
            error(fileException);
        }
        return wrongFile;
    }

    private void matchConfig() {
        try {
            boolean hasUpdated = false;
            InputStream stream = plugin.getResource(configFile.getName());
            assert stream != null;
            InputStreamReader is = new InputStreamReader(stream);
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(is);
            for (String key : defConfig.getConfigurationSection("").getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defConfig.get(key));
                    hasUpdated = true;
                }
            }
            for (String key : config.getConfigurationSection("").getKeys(true)) {
                if (!defConfig.contains(key)) {
                    config.set(key, null);
                    hasUpdated = true;
                }
            }
            if (hasUpdated) config.save(configFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean getSetting(String setting) {
        return this.config.getBoolean("settings." + setting);
    }

    private String getString(String setting) {
        return this.config.getString("settings." + setting);
    }

    private int getInt(String setting) {
        return this.config.getInt("settings." + setting);
    }

    private long getLong(String setting) {
        return this.config.getLong("settings." + setting);
    }

    private double getDouble(String setting) {
        return this.config.getDouble("settings." + setting);
    }

    private String getPrefix(String setting) {
        return this.config.getString("settings.prefixes." + setting);
    }

    private Boolean getFeatures(String feature) {
        return this.config.getBoolean("settings.features." + feature);
    }

    private void loadConfigs(CommandSender... sender_) {
        var sender = sender_ != null && sender_.length > 0 && sender_[0] != null;
        try {
            CONFIG_VERSION = getDouble("version");
            PROJECT_DEBUG = getSetting("debug");
            LOGGING_LEVEL = getInt("logging-level");
            DEFAULT_WATCHER_INTERVAL = getLong("watcher-interval");
            PLUGIN_PREFIX = getPrefix("plugin");
            ERROR_PREFIX = getPrefix("error");
            WATCHER_PREFIX = getPrefix("watcher");
            REQUESTS_PREFIX = getPrefix("request");
            WEBHOOK_PREFIX = getPrefix("webhook");
            PATH_VARIABLE_DELIMITER = getString("path-delimiter");

            if (PATH_VARIABLE_DELIMITER.matches("[$#^\\[\\]{}_-]")) {
                info("The delimiter contains not allowed unicodes.. '$#^\\/[]{}_-'");
                simpleError("Restart server and change the path-delimiter to something what doesn't contains this characters '$#^\\/[]{}'");
                manager.disablePlugin(plugin);
            }
        } catch (Exception ignored) {
            warn("&e&lConfig.yaml was fixed... Cause missing entry");
            loadConfigFile(true);
        }
    }

    /**
     * Init.
     *
     * @return the void
     * @throws IOException the io exception
     */
    public void init() throws IOException {
        try {
            loadConfigFile(false);
            if (CONFIG_VERSION != SkJson.CONFIG_PRIMARY_VERSION) {
                var c = regenerateConfigFile();
                warn("&cThe config version are incorrect expected &7'%s'&c but given &7'%s'.\n\t\t  &cRegenerating Config... Saving wrong config to %s", SkJson.CONFIG_PRIMARY_VERSION, CONFIG_VERSION, c);
                loadConfigFile(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            InputStream stream = Config.getConfig().plugin.getResource("plugin.yml");
            if (stream != null) {
                InputStreamReader is = new InputStreamReader(stream);
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(is);
                Config.pluginYaml = yml;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        manager = Bukkit.getPluginManager();


        if (!NBT.preloadApi()) {
            ready = false;
            simpleError("&#adfa6eN&#53db88B&#00b797T&#009294A&#006c7eP&#2a4858I &r Wasn't load &successfully");
        } else {
            logger.info("[NBTAPI] Was loaded &asuccessfully.");
        }

        try {
            if (!versionError(Skript.getVersion(), new Version("2.7.3"), true, manager, plugin))
                return;

            ready = classesRegistration(plugin);
            String metricsPrefix = "&#e3e512M&#a6e247e&#6cda6et&#2ece8dr&#00bfa4i&#00afafc&#329dads&r ";
            setupMetrics(17374, (JavaPlugin) plugin);
            info("%s Was loaded &asuccessfully.", metricsPrefix);
        } catch (Exception ignored) {
            ready = false;
            errors.add("Couldn't initialize Metrics'");
        }
        ready = initializeSkript(manager, plugin.getDescription().getDepend().get(0));


        if (errors.size() > 0) {
            simpleError("&cFound errors while skJson starting, SkJson will be &cdisabled");
            for (int i = 0; i < errors.size(); i++) {
                String error = errors.get(i);
                info("&7→ %s. &c%s", i, error);
            }
            manager.disablePlugin(plugin);
        }

        try {
            JsonWatcher.init(this);
            watcherLog("was &ainitialized.");
        } catch (Exception ignored) {
            errors.add("JsonWatcher Couldn't been &cinitialized.");
        }

        ready = new UpdateCheck((JavaPlugin) plugin, this).getReady();
        ready = registerCommand(plugin, "skjson");
    }

    /**
     * Ready boolean.
     *
     * @return the boolean
     */
    public boolean ready() {
        return ready;
    }

    /**
     * Sets metrics.
     *
     * @param id     the id
     * @param plugin the plugin
     * @return the metrics
     */
    public void setupMetrics(int id, JavaPlugin plugin) {
        Metrics metrics = new Metrics(plugin, id);
        metrics.addCustomChart(new SimplePie("skript_version", () -> Skript.getVersion().toString()));
        metrics.addCustomChart(new SimplePie("skjson_version", () -> plugin.getPluginMeta().getVersion()));
    }

    /**
     * Initialize skript boolean.
     *
     * @param manager    the manager
     * @param dependency the dependency
     * @return the boolean
     */
    public boolean initializeSkript(PluginManager manager, String dependency) {
        Plugin plugin = manager.getPlugin(dependency);
        if (plugin == null) {
            errors.add("Dependency [Skript] weren't found. Check your /plugins folder");
            return false;
        } else if (!plugin.isEnabled()) {
            errors.add("&cOpps! Seems like SkJson was loaded before Skript, something delayed the start. Try restart your server");
            return false;
        }
        String skriptPrefix = "&#e3e512S&#9ae150k&#55d57br&#00c59ci&#00b2aep&#329dadt&r";
        info("%s was found and &ainitialized.", skriptPrefix);
        return true;
    }

    /**
     * Classes registration boolean.
     *
     * @param plugin the plugin
     * @return the boolean
     */
    public boolean classesRegistration(Plugin plugin) {
        try {
            SkriptAddon addon = Skript.registerAddon((JavaPlugin) plugin);
            addon.setLanguageFileDirectory("lang");
            addon.loadClasses("cz.coffee.skjson.skript");
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            errors.add("Skript cannot register SkJson classes!");
            return false;
        }
    }

    /**
     * Sets current version.
     *
     * @param version the version
     * @return the current version
     */
    public void setCurrentVersion(int version) {
        this.version = version;
    }

    /**
     * Register command boolean.
     *
     * @param plugin  the plugin
     * @param command the command
     * @return the boolean
     */
    public boolean registerCommand(JavaPlugin plugin, String command) {
        try {
            plugin.getCommand(command).setExecutor(new SkJsonCommand());
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}

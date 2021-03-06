package com.dumptruckman.bartersigns;

import com.dumptruckman.bartersigns.config.CommentedConfiguration;
import com.dumptruckman.bartersigns.listener.BarterSignsBlockListener;
import com.dumptruckman.bartersigns.listener.BarterSignsEntityListener;
import com.dumptruckman.bartersigns.listener.BarterSignsPlayerListener;
import com.dumptruckman.bartersigns.sign.BarterSignManager;
import com.dumptruckman.bartersigns.config.ConfigIO;
import com.dumptruckman.bartersigns.locale.Language;
import com.palmergames.bukkit.towny.Towny;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.io.*;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import static com.dumptruckman.bartersigns.config.ConfigPath.*;

/**
 * @author dumptruckman
 */
public class BarterSignsPlugin extends JavaPlugin {

    public static final Logger log = Logger.getLogger("Minecraft.BarterSigns");

    private final BarterSignsBlockListener blockListener = new BarterSignsBlockListener(this);
    private final BarterSignsPlayerListener playerListener = new BarterSignsPlayerListener(this);
    
    public CommentedConfiguration config;
    public CommentedConfiguration data;
    private Configuration items;
    public Language lang;
    public BarterSignManager signManager;
    public Towny towny = null;
    private int saveTaskId;

    final public void onEnable() {
        // Grab the PluginManager
        final PluginManager pm = getServer().getPluginManager();

        // Make the data folders that dChest uses
        getDataFolder().mkdirs();

        // Loads the configuration file
        reload(false);

        // Start save timer
        saveTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new BarterSignsSaveTimer(this),
                (long) (config.getInt(DATA_SAVE.getPath(), (Integer) DATA_SAVE.getDefault()) * 20),
                (long) (config.getInt(DATA_SAVE.getPath(), (Integer) DATA_SAVE.getDefault()) * 20));

        //if (config.getString(LANGUAGE_FILE.getPath())
        //        .equalsIgnoreCase(LANGUAGE_FILE.getDefault().toString())) {
            // Extracts default english language file
        extractFromJar("english.yml");
        //}
        File itemFile = new File(this.getDataFolder(), "items.yml");
        if (!itemFile.exists()) {
            extractFromJar("items.yml");
        }

        // Load up language file
        File langFile = new File(this.getDataFolder(), config.getString(LANGUAGE_FILE.getPath()));
        if (!langFile.exists()) {
            log.severe("Language file: " + langFile.getName() + " is missing!  Disabling "
                    + this.getDescription().getName());
            pm.disablePlugin(this);
            return;
        }
        lang = new Language(langFile);

        // Load up item file
        if (!itemFile.exists()) {
            log.severe("items.yml is missing!  Disabling "
                    + this.getDescription().getName());
            pm.disablePlugin(this);
            return;
        }
        items = new ConfigIO(itemFile).load();

        // Check for Towny
        try {
            towny = (Towny)pm.getPlugin("Towny");
        } catch (Exception ignore) {}

        // Register command executor for main plugin command

        // Register event listeners
        pm.registerEvents(blockListener, this);
        pm.registerEvents(playerListener, this);
        pm.registerEvents(new BarterSignsEntityListener(this), this);

        signManager = new BarterSignManager(this);
        
        // Display enable message/version info
        log.info(this.getDescription().getName() + " " + getDescription().getVersion() + " enabled.");
    }

    final public void onDisable() {
        getServer().getScheduler().cancelTask(saveTaskId);
        saveData();
        log.info(this.getDescription().getName() + " " + getDescription().getVersion() + " disabled.");
    }

    final public void reload(boolean announce) {
        config = new ConfigIO(new File(this.getDataFolder(), "config.yml")).load();
        data = new ConfigIO(new File(this.getDataFolder(), "data.yml")).load();

        config.setHeader("# You must reload the server for changes here to take effect");
        config.addComment("settings.languagefile", "This is where you specify the language file you wish to use.");
        if (config.getString("settings.languagefile") == null) {
            config.setProperty("settings.languagefile", "english.yml");
        }
        config.addComment(DATA_SAVE.getPath(), "This is how often (in seconds) user data is saved.");
        config.getInt(DATA_SAVE.getPath(), (Integer) DATA_SAVE.getDefault());
        config.addComment(USE_PERMS.getPath(), "This will enable/disable use of SuperPerms.",
                "If disabled, all users may create/use signs and OPs will be able to manage every sign.");
        config.getBoolean(USE_PERMS.getPath(), (Boolean) USE_PERMS.getDefault());
        config.addComment(SIGN_STORAGE_LIMIT.getPath(), "This is the total amount of stock the sign can hold");
        config.getInt(SIGN_STORAGE_LIMIT.getPath(), (Integer) SIGN_STORAGE_LIMIT.getDefault());
        config.addComment(SIGN_ENFORCE_MAX_STACK_SIZE.getPath(), "This will make all items dispensed by the sign obey max stack size.");
        config.getBoolean(SIGN_ENFORCE_MAX_STACK_SIZE.getPath(), (Boolean) SIGN_ENFORCE_MAX_STACK_SIZE.getDefault());
        config.addComment(SIGN_USE_NUM_STACKS.getPath(), "This will cause the stock limit to be in number of stacks.");
        config.getBoolean(SIGN_USE_NUM_STACKS.getPath(), (Boolean) SIGN_USE_NUM_STACKS.getDefault());
        config.addComment(SIGN_INDESTRUCTIBLE.getPath(), "This will make the signs completely indestructible except by owners/admin");
        config.getBoolean(SIGN_INDESTRUCTIBLE.getPath(), (Boolean) SIGN_INDESTRUCTIBLE.getDefault());
        config.addComment(SIGN_DROPS_ITEMS.getPath(), "This will cause the sign to drop all items it contains upon breaking");
        config.getBoolean(SIGN_DROPS_ITEMS.getPath(), (Boolean) SIGN_DROPS_ITEMS.getDefault());

        config.addComment(PLUGINS_OVERRIDE.getPath(), "This will cause BarterSigns signs to work regardless of other plugins and may cancel the effect of those plugins.", "Please keep in mind this is ONLY for signs IN USE by BarterSigns.");
        config.getBoolean(PLUGINS_OVERRIDE.getPath(), (Boolean) PLUGINS_OVERRIDE.getDefault());
        config.addComment(TOWNY_SHOP_PLOTS.getPath(), "If Towny is in use, this will make it so BarterSigns may only be placed in a Towny Shop Plot.");
        config.getBoolean(TOWNY_SHOP_PLOTS.getPath(), (Boolean) TOWNY_SHOP_PLOTS.getDefault());

        config.save();
    }

    private void extractFromJar(String fileName) {
        JarFile jar = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            String path = BarterSignsPlugin.class.getProtectionDomain().getCodeSource()
                    .getLocation().getPath();
            path = path.replaceAll("%20", " ");
            jar = new JarFile(path);
            ZipEntry entry = jar.getEntry(fileName);
            File efile = new File(getDataFolder(), entry.getName());

            in = new BufferedInputStream(jar.getInputStream(entry));
            out = new BufferedOutputStream(new FileOutputStream(efile));
            byte[] buffer = new byte[2048];
            for (; ; ) {
                int nBytes = in.read(buffer);
                if (nBytes <= 0) break;
                out.write(buffer, 0, nBytes);
            }
            out.flush();
            in.close();
            out.close();
        } catch (IOException e) {
            log.warning("Could not extract " + fileName + "! Reason: " + e.getMessage());
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    final public void saveConfig() {
        new ConfigIO(config).save();
    }

    final public void saveData() {
        new ConfigIO(data).save();
    }

    final public void saveFiles() {
        saveConfig();
        saveData();
    }

    public void signAndMessage(Sign sign, Player player, List<String> message) {
        for (int i = 0; i < 4; i++) {
            sign.setLine(i, message.get(0));
            message.remove(0);
        }
        if (player != null) {
            lang.sendMessage(message, player);
            player.sendBlockChange(sign.getBlock().getLocation(), 0, (byte) 0);
        }
        sign.update(true);
    }

    public void signAndMessage(Sign sign, Player player, String path, String... args) {
        List<String> message = lang.lang(path, args);
        signAndMessage(sign, player, message);
    }

    public void signAndMessage(SignChangeEvent event, Player player, List<String> message) {
        for (int i = 0; i < 4; i++) {
            event.setLine(i, message.get(0));
            message.remove(0);
        }
        lang.sendMessage(message, player);
    }

    public void sendMessage(CommandSender sender, String path, String... args) {
        lang.sendMessage(lang.lang(path, args), sender);
    }

    public String itemToDataString(ItemStack item) {
        return itemToDataString(item, true);
    }

    public String itemToDataString(ItemStack s, boolean withAmount) {
        String item = "";
        if (withAmount) {
            item += s.getAmount() + " ";
        }
        item += s.getTypeId() + "," + s.getDurability();
        return item;
    }

    public ItemStack stringToItem(String item) {
        String[] sellInfo = item.split("\\s");
        String[] itemData = sellInfo[1].split(",");
        return new ItemStack(Integer.valueOf(itemData[0]), Integer.valueOf(sellInfo[0]), Short.valueOf(itemData[1]));
    }

    public String itemToString(ItemStack item) {
        return itemToString(item, true);
    }

    public String itemToString(ItemStack s, boolean withAmount) {
        String item = "";
        if (withAmount) {
            item += s.getAmount() + " ";
        }
        item += getShortItemName(s);
        return item;
    }

    public String getShortItemName(ItemStack item) {
        String key = itemToDataString(item, false);
        String name = items.getString(key);
        if (name == null) {
            name = items.getString(item.getTypeId() + ",0");
        }
        if (name == null) {
            name = Integer.toString(item.getTypeId());
            if (item.getDurability() > 0) {
                name += "," + item.getDurability();
            }
            log.warning("Missing name for item: '" + name + "' in items.yml");
        }
        return name;
    }

    public boolean enforceMaxStackSize() {
        return config.getBoolean(SIGN_ENFORCE_MAX_STACK_SIZE.getPath(), (Boolean) SIGN_ENFORCE_MAX_STACK_SIZE.getDefault());
    }

    public boolean stockLimitUsesStacks() {
        return config.getBoolean(SIGN_USE_NUM_STACKS.getPath(), (Boolean) SIGN_USE_NUM_STACKS.getDefault());
    }
}

package dev.rosewood.rosestacker.manager;

import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.hook.PlaceholderAPIHook;
import dev.rosewood.rosestacker.locale.EnglishLocale;
import dev.rosewood.rosestacker.locale.Locale;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.utils.HexUtils;
import dev.rosewood.rosestacker.utils.StringPlaceholders;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LocaleManager extends Manager {

    private CommentedFileConfiguration locale;

    public LocaleManager(RoseStacker roseStacker) {
        super(roseStacker);
    }

    /**
     * Creates a .lang file if one doesn't exist
     * Cross merges values between files into the .lang file, the .lang values take priority
     *
     * @param locale The Locale to register
     */
    private void registerLocale(Locale locale) {
        File file = new File(this.roseStacker.getDataFolder() + "/locale", locale.getLocaleName() + ".lang");
        boolean newFile = false;
        if (!file.exists()) {
            try {
                file.createNewFile();
                newFile = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean changed = false;
        CommentedFileConfiguration configuration = CommentedFileConfiguration.loadConfiguration(this.roseStacker, file);
        if (newFile) {
            configuration.addComments(locale.getLocaleName() + " translation by " + locale.getTranslatorName());
            Map<String, Object> defaultLocaleStrings = locale.getDefaultLocaleValues();
            for (String key : defaultLocaleStrings.keySet()) {
                Object value = defaultLocaleStrings.get(key);
                if (key.startsWith("#")) {
                    configuration.addComments((String) value);
                } else {
                    configuration.set(key, value);
                }
            }
            changed = true;
        } else {
            Map<String, Object> defaultLocaleStrings = locale.getDefaultLocaleValues();
            for (String key : defaultLocaleStrings.keySet()) {
                if (key.startsWith("#"))
                    continue;

                Object value = defaultLocaleStrings.get(key);
                if (!configuration.contains(key)) {
                    configuration.set(key, value);
                    changed = true;
                }
            }
        }

        if (changed)
            configuration.save();
    }

    @Override
    public void reload() {
        File localeDirectory = new File(this.roseStacker.getDataFolder(), "locale");
        if (!localeDirectory.exists())
            localeDirectory.mkdirs();

        this.registerLocale(new EnglishLocale());

        File targetLocaleFile = new File(this.roseStacker.getDataFolder() + "/locale", Setting.LOCALE.getString() + ".lang");
        if (!targetLocaleFile.exists()) {
            targetLocaleFile = new File(this.roseStacker.getDataFolder() + "/locale", "en_US.lang");
            this.roseStacker.getLogger().severe("File " + targetLocaleFile.getName() + " does not exist. Defaulting to en_US.lang");
        }

        this.locale = CommentedFileConfiguration.loadConfiguration(this.roseStacker, targetLocaleFile);
    }

    @Override
    public void disable() {

    }

    /**
     * @return a map of acf-core messages and their values
     */
    public Map<String, String> getAcfCoreMessages() {
        return this.locale.getKeys(false).stream()
                .filter(x -> x.startsWith("acf-core"))
                .collect(Collectors.toMap(x -> x.replaceFirst("acf-core-", "").replaceAll("-", "_"), this.locale::getString));
    }

    /**
     * @return a map of acf-core minecraft messages and their values
     */
    public Map<String, String> getAcfMinecraftMessages() {
        return this.locale.getKeys(false).stream()
                .filter(x -> x.startsWith("acf-minecraft"))
                .collect(Collectors.toMap(x -> x.replaceFirst("acf-minecraft-", "").replaceAll("-", "_"), this.locale::getString));
    }

    /**
     * Gets a locale message
     *
     * @param messageKey The key of the message to get
     * @return The locale message
     */
    public String getLocaleMessage(String messageKey) {
        return this.getLocaleMessage(messageKey, StringPlaceholders.empty());
    }

    /**
     * Gets a locale message with the given placeholders applied
     *
     * @param messageKey The key of the message to get
     * @param stringPlaceholders The placeholders to apply
     * @return The locale message with the given placeholders applied
     */
    public String getLocaleMessage(String messageKey, StringPlaceholders stringPlaceholders) {
        String message = this.locale.getString(messageKey);
        if (message == null)
            return ChatColor.RED + "Missing message in locale file: " + messageKey;
        return HexUtils.colorify(stringPlaceholders.apply(message));
    }

    /**
     * Gets a gui locale message with the given placeholders applied
     *
     * @param messageKey The key of the message to get
     * @param stringPlaceholders The placeholders to apply
     * @return The locale message with the given placeholders applied
     */
    public List<String> getGuiLocaleMessage(String messageKey, StringPlaceholders stringPlaceholders) {
        List<String> message = this.locale.getStringList(messageKey);
        if (message.isEmpty())
            message.add(ChatColor.RED + "Missing message in locale file: " + messageKey);
        message.replaceAll(x -> HexUtils.colorify(stringPlaceholders.apply(x)));
        return message;
    }

    /**
     * Sends a message to a CommandSender with the prefix with placeholders applied
     *
     * @param sender The CommandSender to send to
     * @param messageKey The message key of the Locale to send
     * @param stringPlaceholders The placeholders to apply
     */
    public void sendMessage(CommandSender sender, String messageKey, StringPlaceholders stringPlaceholders) {
        this.sendParsedMessage(sender, this.getLocaleMessage("prefix") + this.getLocaleMessage(messageKey, stringPlaceholders));
    }

    /**
     * Sends a message to a CommandSender with the prefix
     *
     * @param sender The CommandSender to send to
     * @param messageKey The message key of the Locale to send
     */
    public void sendMessage(CommandSender sender, String messageKey) {
        this.sendMessage(sender, messageKey, StringPlaceholders.empty());
    }

    /**
     * Sends a message to a CommandSender with placeholders applied
     *
     * @param sender The CommandSender to send to
     * @param messageKey The message key of the Locale to send
     * @param stringPlaceholders The placeholders to apply
     */
    public void sendSimpleMessage(CommandSender sender, String messageKey, StringPlaceholders stringPlaceholders) {
        this.sendParsedMessage(sender, this.getLocaleMessage(messageKey, stringPlaceholders));
    }

    /**
     * Sends a message to a CommandSender
     *
     * @param sender The CommandSender to send to
     * @param messageKey The message key of the Locale to send
     */
    public void sendSimpleMessage(CommandSender sender, String messageKey) {
        this.sendSimpleMessage(sender, messageKey, StringPlaceholders.empty());
    }

    /**
     * Sends a custom message to a CommandSender
     *
     * @param sender The CommandSender to send to
     * @param message The message to send
     */
    public void sendCustomMessage(CommandSender sender, String message) {
        this.sendParsedMessage(sender, message);
    }

    /**
     * Replaces PlaceholderAPI placeholders if PlaceholderAPI is enabled
     *
     * @param sender The potential Player to replace with
     * @param message The message
     * @return A placeholder-replaced message
     */
    private String parsePlaceholders(CommandSender sender, String message) {
        if (sender instanceof Player)
            return PlaceholderAPIHook.applyPlaceholders((Player) sender, message);
        return message;
    }

    /**
     * Sends a message with placeholders and colors parsed to a CommandSender
     *
     * @param sender The sender to send the message to
     * @param message The message
     */
    private void sendParsedMessage(CommandSender sender, String message) {
        HexUtils.sendMessage(sender, this.parsePlaceholders(sender, message));
    }

}

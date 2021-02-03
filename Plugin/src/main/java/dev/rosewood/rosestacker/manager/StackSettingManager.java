package dev.rosewood.rosestacker.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosegarden.manager.Manager;
import dev.rosewood.rosestacker.stack.settings.BlockStackSettings;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.stack.settings.ItemStackSettings;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTags;
import dev.rosewood.rosestacker.utils.ClassUtils;
import dev.rosewood.rosestacker.utils.ItemUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;

public class StackSettingManager extends Manager {

    private static final String PACKAGE_PATH = "dev.rosewood.rosestacker.stack.settings.entity";

    private final Map<Material, BlockStackSettings> blockSettings;
    private final Map<EntityType, EntityStackSettings> entitySettings;
    private final Map<Material, ItemStackSettings> itemSettings;
    private final Map<EntityType, SpawnerStackSettings> spawnerSettings;

    public StackSettingManager(RosePlugin rosePlugin) {
        super(rosePlugin);

        this.blockSettings = new HashMap<>();
        this.entitySettings = new LinkedHashMap<>();
        this.itemSettings = new HashMap<>();
        this.spawnerSettings = new HashMap<>();
    }

    @Override
    public void reload() {
        // Settings files
        File blockSettingsFile = this.getBlockSettingsFile();
        File entitySettingsFile = this.getEntitySettingsFile();
        File itemSettingsFile = this.getItemSettingsFile();
        File spawnerSettingsFile = this.getSpawnerSettingsFile();

        // Flags for if we should save the files
        AtomicBoolean saveBlockSettingsFile = new AtomicBoolean(false);
        AtomicBoolean saveEntitySettingsFile = new AtomicBoolean(false);
        AtomicBoolean saveItemSettingsFile = new AtomicBoolean(false);
        AtomicBoolean saveSpawnerSettingsFile = new AtomicBoolean(false);

        // Load block settings
        CommentedFileConfiguration blockSettingsConfiguration = CommentedFileConfiguration.loadConfiguration(blockSettingsFile);
        StackerUtils.getPossibleStackableBlockMaterials().forEach(x -> {
            BlockStackSettings blockStackSettings = new BlockStackSettings(blockSettingsConfiguration, x);
            this.blockSettings.put(x, blockStackSettings);
            if (blockStackSettings.hasChanges())
                saveBlockSettingsFile.set(true);
        });

        // Load entity settings and data from entity_data.json
        CommentedFileConfiguration entitySettingsConfiguration = CommentedFileConfiguration.loadConfiguration(entitySettingsFile);
        try (InputStream entityDataStream = this.getClass().getResourceAsStream("/entity_data.json");
             Reader entityDataReader = new InputStreamReader(entityDataStream)) {
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject = jsonParser.parse(entityDataReader).getAsJsonObject();

            List<Class<EntityStackSettings>> classes = ClassUtils.getClassesOf(this.rosePlugin, PACKAGE_PATH, EntityStackSettings.class);
            List<String> ignoredLoading = new ArrayList<>();
            for (Class<EntityStackSettings> clazz : classes) {
                try {
                    EntityStackSettings entityStackSetting = clazz.getConstructor(CommentedFileConfiguration.class, JsonObject.class).newInstance(entitySettingsConfiguration, jsonObject);
                    this.entitySettings.put(entityStackSetting.getEntityType(), entityStackSetting);
                    if (entityStackSetting.hasChanges())
                        saveEntitySettingsFile.set(true);
                } catch (Exception e) {
                    // Log entity settings that failed to load
                    // This should only be caused by version incompatibilities
                    String className = clazz.getSimpleName();
                    ignoredLoading.add(className.substring(0, className.length() - 13));
                    e.printStackTrace();
                }
            }

            if (!ignoredLoading.isEmpty())
                this.rosePlugin.getLogger().warning("Ignored loading stack settings for entities: " + ignoredLoading);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load item settings
        CommentedFileConfiguration itemSettingsConfiguration = CommentedFileConfiguration.loadConfiguration(itemSettingsFile);
        Stream.of(Material.values()).sorted(Comparator.comparing(Enum::name)).forEach(x -> {
            ItemStackSettings itemStackSettings = new ItemStackSettings(itemSettingsConfiguration, x);
            this.itemSettings.put(x, itemStackSettings);
            if (itemStackSettings.hasChanges())
                saveItemSettingsFile.set(true);
        });

        // Load spawner settings
        boolean addSpawnerHeaderComments = !spawnerSettingsFile.exists();
        CommentedFileConfiguration spawnerSettingsConfiguration = CommentedFileConfiguration.loadConfiguration(spawnerSettingsFile);
        if (addSpawnerHeaderComments) {
            saveSpawnerSettingsFile.set(true);
            Map<String, String> conditionTags = ConditionTags.getTagDescriptionMap();
            spawnerSettingsConfiguration.addComments("Available Spawn Requirements:", "");
            for (Entry<String, String> entry : conditionTags.entrySet()) {
                String tag = entry.getKey();
                String description = entry.getValue();
                spawnerSettingsConfiguration.addComments(tag + " - " + description);
            }

            spawnerSettingsConfiguration.addComments(
                    "",
                    "Valid Blocks: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html",
                    "Valid Biomes: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/Biome.html"
            );
        }

        StackerUtils.getAlphabeticalStackableEntityTypes().forEach(x -> {
            SpawnerStackSettings spawnerStackSettings = new SpawnerStackSettings(spawnerSettingsConfiguration, x);
            this.spawnerSettings.put(x, spawnerStackSettings);
            if (spawnerStackSettings.hasChanges())
                saveSpawnerSettingsFile.set(true);
        });

        // Save files if changes were made
        if (saveBlockSettingsFile.get())
            blockSettingsConfiguration.save(true);
        if (saveEntitySettingsFile.get())
            entitySettingsConfiguration.save(true);
        if (saveItemSettingsFile.get())
            itemSettingsConfiguration.save(true);
        if (saveSpawnerSettingsFile.get())
            spawnerSettingsConfiguration.save(true);
    }

    @Override
    public void disable() {
        this.blockSettings.clear();
        this.entitySettings.clear();
        this.itemSettings.clear();
        this.spawnerSettings.clear();
    }

    public File getBlockSettingsFile() {
        return new File(this.rosePlugin.getDataFolder(), "block_settings.yml");
    }

    public File getEntitySettingsFile() {
        return new File(this.rosePlugin.getDataFolder(), "entity_settings.yml");
    }

    public File getItemSettingsFile() {
        return new File(this.rosePlugin.getDataFolder(), "item_settings.yml");
    }

    public File getSpawnerSettingsFile() {
        return new File(this.rosePlugin.getDataFolder(), "spawner_settings.yml");
    }

    /**
     * Gets the BlockStackSettings for a block type
     *
     * @param material The block material to get the settings of
     * @return The BlockStackSettings for the block type, or null if the block type is not stackable
     */
    public BlockStackSettings getBlockStackSettings(Material material) {
        return this.blockSettings.get(material);
    }

    /**
     * Gets the BlockStackSettings for a block
     *
     * @param block The block to get the settings of
     * @return The BlockStackSettings for the block, or null if the block type is not stackable
     */
    public BlockStackSettings getBlockStackSettings(Block block) {
        return this.getBlockStackSettings(block.getType());
    }

    /**
     * Gets the EntityStackSettings for an entity type
     *
     * @param entityType The entity type to get the settings of
     * @return The EntityStackSettings for the entity type
     */
    public EntityStackSettings getEntityStackSettings(EntityType entityType) {
        return this.entitySettings.get(entityType);
    }

    /**
     * Gets the EntityStackSettings for an entity
     *
     * @param entity The entity to get the settings of
     * @return The EntityStackSettings for the entity
     */
    public EntityStackSettings getEntityStackSettings(LivingEntity entity) {
        return this.getEntityStackSettings(entity.getType());
    }

    /**
     * Gets the EntityStackSettings for a spawn egg material
     *
     * @param material The spawn egg material to get the settings of
     * @return The EntityStackSettings for the spawn egg material, or null if the material is not a spawn egg
     */
    public EntityStackSettings getEntityStackSettings(Material material) {
        if (!ItemUtils.isSpawnEgg(material))
            return null;

        for (EntityStackSettings settings : this.entitySettings.values())
            if (settings.getEntityTypeData().getSpawnEggMaterial() == material)
                return settings;

        return null;
    }

    /**
     * Gets the ItemStackSettings for an item type
     *
     * @param material The item type to get the settings of
     * @return The ItemStackSettings for the item type
     */
    public ItemStackSettings getItemStackSettings(Material material) {
        return this.itemSettings.get(material);
    }

    /**
     * Gets the ItemStackSettings for an item
     *
     * @param item The item to get the settings of
     * @return The ItemStackSettings for the item
     */
    public ItemStackSettings getItemStackSettings(Item item) {
        return this.getItemStackSettings(item.getItemStack().getType());
    }

    /**
     * Gets the SpawnerStackSettings for a spawner entity type
     *
     * @param entityType The spawner entity type to get the settings of
     * @return The SpawnerStackSettings for the spawner entity type
     */
    public SpawnerStackSettings getSpawnerStackSettings(EntityType entityType) {
        return this.spawnerSettings.get(entityType);
    }

    /**
     * Gets the SpawnerStackSettings for a spawner
     *
     * @param creatureSpawner The spawner to get the settings of
     * @return The SpawnerStackSettings for the spawner
     */
    public SpawnerStackSettings getSpawnerStackSettings(CreatureSpawner creatureSpawner) {
        return this.getSpawnerStackSettings(creatureSpawner.getSpawnedType());
    }

    public Set<EntityType> getStackableEntityTypes() {
        return this.entitySettings.values().stream()
                .filter(EntityStackSettings::isStackingEnabled)
                .map(EntityStackSettings::getEntityType)
                .collect(Collectors.toSet());
    }

    public Set<Material> getStackableItemTypes() {
        return this.itemSettings.values().stream()
                .filter(ItemStackSettings::isStackingEnabled)
                .map(ItemStackSettings::getType)
                .collect(Collectors.toSet());
    }

    public Set<Material> getStackableBlockTypes() {
        return this.blockSettings.values().stream()
                .filter(BlockStackSettings::isStackingEnabled)
                .map(BlockStackSettings::getType)
                .collect(Collectors.toSet());
    }

    public Set<EntityType> getStackableSpawnerTypes() {
        return this.spawnerSettings.values().stream()
                .filter(SpawnerStackSettings::isStackingEnabled)
                .map(SpawnerStackSettings::getEntityType)
                .collect(Collectors.toSet());
    }

}

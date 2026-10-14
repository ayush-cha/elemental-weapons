package com.elementalweapons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import java.util.List;

public final class ElementalWeapons implements ModInitializer {
    public static final String MOD_ID = "elemental_weapons";

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static final TagKey<Block> INCORRECT_BLOCKS =
        TagKey.create(Registries.BLOCK, id("incorrect_for_elemental_tool"));
    private static final TagKey<Item> REPAIR_ITEMS =
        TagKey.create(Registries.ITEM, id("elemental_repair_items"));
    private static final ToolMaterial MATERIAL =
        new ToolMaterial(INCORRECT_BLOCKS, 500, 6.0F, 2.0F, 18, REPAIR_ITEMS);

    public static final Item FIRE_SWORD = sword("fire_sword", ElementalSwordItem.Element.FIRE);
    public static final Item WATER_SWORD = sword("water_sword", ElementalSwordItem.Element.WATER);
    public static final Item EARTH_SWORD = sword("earth_sword", ElementalSwordItem.Element.EARTH);
    public static final Item AIR_SWORD = sword("air_sword", ElementalSwordItem.Element.AIR);
    // Keep the original registry IDs so gems already in a saved world still work.
    public static final Item FIRE_GEM = gem("fire_stone");
    public static final Item WATER_GEM = gem("water_stone");
    public static final Item EARTH_GEM = gem("earth_stone");
    public static final Item AIR_GEM = gem("air_stone");

    private static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
        BuiltInRegistries.CREATIVE_MODE_TAB.key(), id("elemental_weapons"));
    private static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
        .icon(() -> new ItemStack(FIRE_GEM))
        .title(Component.translatable("creativeTab.elemental_weapons"))
        .displayItems((params, output) -> {
            output.accept(FIRE_GEM);
            output.accept(WATER_GEM);
            output.accept(EARTH_GEM);
            output.accept(AIR_GEM);
            output.accept(FIRE_SWORD);
            output.accept(WATER_SWORD);
            output.accept(EARTH_SWORD);
            output.accept(AIR_SWORD);
        })
        .build();

    private static Item sword(String name, ElementalSwordItem.Element element) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        Item item = new ElementalSwordItem(element, new Item.Properties()
            .setId(key)
            .sword(MATERIAL, 3.0F, -2.4F)
            .component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("lore.elemental_weapons." + name),
                Component.translatable("lore.elemental_weapons.cooldown." + name)
            ))));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item gem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        Item item = new Item(new Item.Properties().setId(key).stacksTo(16));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    @Override
    public void onInitialize() {
        ServerTickEvents.END_LEVEL_TICK.register(ElementalSwordItem::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(ElementalSwordItem::clearTemporaryEffects);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(tab -> {
            tab.accept(FIRE_SWORD);
            tab.accept(WATER_SWORD);
            tab.accept(EARTH_SWORD);
            tab.accept(AIR_SWORD);
        });

        // Every listed structure has a 25% chance to add one random elemental gem.
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin() || !isGemChest(key)) return;
            LootPool.Builder gems = LootPool.lootPool()
                .when(LootItemRandomChanceCondition.randomChance(0.25F))
                .add(LootItem.lootTableItem(FIRE_GEM))
                .add(LootItem.lootTableItem(WATER_GEM))
                .add(LootItem.lootTableItem(EARTH_GEM))
                .add(LootItem.lootTableItem(AIR_GEM));
            tableBuilder.withPool(gems);
        });

        AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (!(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND
                || player.isSpectator() || !(target instanceof LivingEntity living)) {
                return InteractionResult.PASS;
            }

            Item held = player.getMainHandItem().getItem();
            if (held == FIRE_SWORD) {
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
            } else if (held == WATER_SWORD) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0));
                player.clearFire();
            } else if (held == EARTH_SWORD) {
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            } else if (held == AIR_SWORD) {
                living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isGemChest(ResourceKey<LootTable> key) {
        return key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"))
            || key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "chests/jungle_temple"))
            || key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "chests/simple_dungeon"))
            || key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "chests/trial_chambers/supply"))
            || key.identifier().equals(Identifier.fromNamespaceAndPath("minecraft", "chests/trial_chambers/reward"));
    }
}

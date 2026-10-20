package com.kintil555.football.registry;

import com.kintil555.football.FootballMod;
import com.kintil555.football.item.FootballItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item SULFUR_GOO = register("sulfur_goo", Item::new, new Item.Properties());
    public static final Item FOOTBALL = register("football", FootballItem::new, new Item.Properties().stacksTo(16));

    private ModItems() {
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    private static ResourceKey<CreativeModeTab> vanillaTab(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(path));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(vanillaTab("ingredients")).register(output -> output.accept(SULFUR_GOO));
        CreativeModeTabEvents.modifyOutputEvent(vanillaTab("tools_and_utilities")).register(output -> output.accept(FOOTBALL));
    }
}

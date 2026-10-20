package com.kintil555.football.loot;

import com.kintil555.football.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public final class ModLoot {
    private static final ResourceKey<LootTable> SULFUR_CUBE_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/sulfur_cube"));

    private ModLoot() {
    }

    /** Sulfur cubes drop 1-3 sulfur goo when killed. */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (SULFUR_CUBE_LOOT.equals(key)) {
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ModItems.SULFUR_GOO)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F)))));
            }
        });
    }
}

package com.avatar.item;

import com.avatar.Avatar;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item SCROLL = Registry.register(Registries.ITEM, Avatar.id("ancient_scroll"),
            new ScrollItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
    public static final Item GEM = Registry.register(Registries.ITEM, Avatar.id("spirit_gem"),
            new GemItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(SCROLL);
            e.add(GEM);
        });
    }
}

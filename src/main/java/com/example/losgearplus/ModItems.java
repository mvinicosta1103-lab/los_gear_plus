package com.example.losgearplus;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	private ModItems() {}

	/** Item de exemplo: troque/duplique para criar os seus. */
	public static final Item EXAMPLE_ITEM = register("example_item", new Item(new Item.Properties().stacksTo(16)));

	public static final CreativeModeTab TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			LosGearPlus.id("main"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup." + LosGearPlus.MOD_ID))
					.icon(() -> new ItemStack(EXAMPLE_ITEM))
					.displayItems((params, output) -> {
						output.accept(EXAMPLE_ITEM);
						// adicione aqui os próximos itens
					})
					.build());

	private static <T extends Item> T register(String name, T item) {
		return Registry.register(BuiltInRegistries.ITEM, LosGearPlus.id(name), item);
	}

	/** Só existe para forçar o carregamento da classe (e portanto o registro). */
	public static void init() {}
}

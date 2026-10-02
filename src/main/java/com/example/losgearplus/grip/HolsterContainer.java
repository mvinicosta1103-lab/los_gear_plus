package com.example.losgearplus.grip;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Os 2 slots de arma do peitoral. No SERVIDOR é só uma janela para o Grip Storage (attachment
 * {@link GripStorage#STORE}: [0] = mão principal, [1] = mão secundária), que continua sendo a única fonte da
 * verdade. No CLIENTE guarda uma cópia local, preenchida pelos pacotes normais de slot do menu.
 */
public final class HolsterContainer implements Container {
	private final Player player;
	private final NonNullList<ItemStack> local = NonNullList.withSize(2, ItemStack.EMPTY);

	public HolsterContainer(Player player) {
		this.player = player;
	}

	private ServerPlayer server() {
		return player instanceof ServerPlayer sp ? sp : null;
	}

	@Override
	public int getContainerSize() {
		return 2;
	}

	@Override
	public boolean isEmpty() {
		return getItem(0).isEmpty() && getItem(1).isEmpty();
	}

	@Override
	public ItemStack getItem(int index) {
		if (index < 0 || index > 1) return ItemStack.EMPTY;
		ServerPlayer sp = server();
		if (sp == null) return local.get(index);
		List<ItemStack> stored = sp.getAttached(GripStorage.STORE);
		if (stored == null || index >= stored.size()) return ItemStack.EMPTY;
		ItemStack stack = stored.get(index);
		if (GripMarker.isBound(stack)) GripMarker.unmark(stack); // guardado vindo de versão antiga: volta a ser item comum
		return stack;
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		ItemStack current = getItem(index);
		if (current.isEmpty()) return ItemStack.EMPTY;
		ItemStack out = GripBlade.restore(current.copy()); // a lâmina recolhida volta junto com o grip
		setItem(index, ItemStack.EMPTY);
		return out;
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		return removeItem(index, 1);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		if (index < 0 || index > 1) return;
		ServerPlayer sp = server();
		if (sp == null) {
			local.set(index, stack);
			return;
		}
		List<ItemStack> stored = GripStorage.read(sp);
		ItemStack put = stack.isEmpty() ? ItemStack.EMPTY : GripMarker.unmark(stack.copy());
		stored.set(index, put);
		GripStorage.write(sp, stored);
		GripStorage.sync(sp); // holsters do corpo mostram o que mudou
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		setItem(0, ItemStack.EMPTY);
		setItem(1, ItemStack.EMPTY);
	}
}

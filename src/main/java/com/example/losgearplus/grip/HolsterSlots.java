package com.example.losgearplus.grip;

import com.example.losgearplus.compat.DaotBridge;
import com.example.losgearplus.mode.OdmgModeServer;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Os 2 slots de arma ao lado do peitoral (New ODM Uniform): guardam o par que vai nas mãos (0 = mão principal,
 * 1 = mão secundária). Mostram exatamente o que está no Grip Storage, então o que o jogador põe aqui aparece nos
 * holsters do corpo e é o que o ODMG Mode empunha.
 *
 * Só existem quando New ODM Uniform E New ODM Gear estão vestidos, e só com o ODMG Mode DESLIGADO (com o modo
 * ligado as armas estão nas mãos). Posições abaixo em pixels da tela de inventário; mexa aqui para ajustar.
 */
public final class HolsterSlots {
	private HolsterSlots() {}

	/** Inventário normal (sobrevivência): coluna à direita do boneco, na altura do peitoral (o slot do harness do DAOT fica em y=44). */
	public static final int SURVIVAL_X = 77;
	public static final int SURVIVAL_Y0 = 8;
	public static final int SURVIVAL_Y1 = 26;

	/** Aba de inventário do criativo: linha do peitoral (y=33), à direita das botas (x=108). O slot do harness do DAOT fica em (126, 6). */
	public static final int CREATIVE_X = 126;
	public static final int CREATIVE_Y = 33;
	public static final int CREATIVE_STEP = 18;

	/** Preenchido pelo cliente: o ODMG Mode do jogador local está ligado? (o servidor usa OdmgModeServer.) */
	public static volatile Predicate<Player> clientModeActive = p -> false;

	public static boolean modeOn(Player player) {
		return player.level().isClientSide ? clientModeActive.test(player) : OdmgModeServer.isActive(player);
	}

	/** Os slots estão disponíveis (visíveis e editáveis) para este jogador agora? */
	public static boolean available(Player player) {
		return DaotBridge.wearsUniformAndGear(player) && !modeOn(player);
	}

	/** Lado servidor do payload do criativo. Rejeição = reenvia o menu para o cliente desfazer o slot fantasma. */
	static void handleCreativeSet(ServerPlayer player, int slot, ItemStack stack) {
		if (!player.isCreative() || !available(player) || slot < 0 || slot > 1) {
			resync(player);
			return;
		}
		List<ItemStack> stored = GripStorage.read(player);
		ItemStack put = ItemStack.EMPTY;
		if (!stack.isEmpty()) {
			ItemStack other = stored.get(1 - slot);
			boolean fits = HolsterWeapons.fits(DaotBridge.loadout(player), stack)
					&& (other.isEmpty() || other.getItem() == stack.getItem());
			if (!fits) {
				resync(player);
				return;
			}
			put = GripMarker.unmark(stack.copy());
			put.setCount(1);
		}
		stored.set(slot, put);
		GripStorage.write(player, stored);
		GripStorage.sync(player);
	}

	private static void resync(ServerPlayer player) {
		player.inventoryMenu.sendAllDataToRemote();
	}
}

package com.example.losgearplus.hook;

import com.example.losgearplus.mode.OdmgModeServer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Estado autoritativo do ângulo dos ganchos (só em memória, igual ao OdmgModeServer: some ao sair).
 *
 * Mesma ideia do WoF (TDMGMovementCapabilityHandler): o cliente só PEDE um valor, o servidor
 * limita, valida e responde com o valor oficial. Aqui o limite é 0 a 180 (abertura total).
 *
 * Valem para o DAOT e para o los_gear: a elegibilidade vem do ODMG Mode, que já checa o ODM
 * equipado via DaotBridge (pernas ou peito).
 */
public final class HookAngleServer {
	private HookAngleServer() {}

	private static final Map<UUID, Integer> ANGLES = new HashMap<>();

	private static int stored(Player player) {
		return ANGLES.getOrDefault(player.getUUID(), 0);
	}

	/**
	 * Ângulo que vale AGORA para os disparos: o guardado se o jogador está em ODMG Mode, senão 0
	 * (equivale ao getHookAngle do WoF, que devolve 0 quando o equipamento não separa ganchos).
	 */
	public static int get(Player player) {
		return OdmgModeServer.isActive(player) ? stored(player) : 0;
	}

	/** Pedido do cliente. Só altera dentro do ODMG Mode; responde SEMPRE com o valor oficial. */
	public static void set(ServerPlayer player, int requested) {
		if (OdmgModeServer.isActive(player)) {
			ANGLES.put(player.getUUID(), HookAngles.clamp(requested));
		}
		ServerPlayNetworking.send(player, new HookAngleSyncPayload(stored(player)));
	}

	/** Remove sem enviar pacote (o jogador já saiu). */
	public static void forget(ServerPlayer player) {
		ANGLES.remove(player.getUUID());
	}

	// -----------------------------------------------------------------------------------------
	// API para o código de disparo do DAOT. Chame UMA vez por gancho, com a direção que o DAOT
	// já calculou, e use o vetor devolvido no lugar dela. Sem ângulo / fora do modo: devolve igual.
	// -----------------------------------------------------------------------------------------

	public static Vec3 applySpread(Player player, boolean rightHook, Vec3 aim) {
		return HookAngles.spread(aim, rightHook, get(player));
	}

	public static Vec3 applySpread(Player player, HumanoidArm arm, Vec3 aim) {
		return applySpread(player, arm == HumanoidArm.RIGHT, aim);
	}
}

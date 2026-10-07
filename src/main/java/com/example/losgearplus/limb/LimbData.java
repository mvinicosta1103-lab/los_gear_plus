package com.example.losgearplus.limb;

import com.example.losgearplus.LosGearPlus;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Onde o {@link LimbState} mora: Data Attachment (persistente) no jogador, no servidor; no cliente, um cache
 * preenchido pelo {@link LimbSyncPayload}. Sem copyOnDeath: morrer devolve todos os membros.
 */
public final class LimbData {
	private LimbData() {}

	public static final AttachmentType<LimbState> ATTACHMENT = AttachmentRegistry.create(
			LosGearPlus.id("limbs"),
			builder -> builder.persistent(LimbState.CODEC).initializer(LimbState::new));

	/** Só para forçar o registro durante a inicialização do mod. */
	public static void init() {}

	/** Leitura (nos dois lados). Nunca cria nada; para um jogador sem perdas devolve {@link LimbState#PRISTINE}. */
	public static LimbState of(Player player) {
		if (player.level().isClientSide) return LimbClientCache.get(player.getUUID());
		LimbState s = player.getAttached(ATTACHMENT);
		return s == null ? LimbState.PRISTINE : s;
	}

	/** Escrita (só servidor): cria o estado se ainda não existir. */
	public static LimbState edit(ServerPlayer player) {
		return player.getAttachedOrCreate(ATTACHMENT);
	}
}

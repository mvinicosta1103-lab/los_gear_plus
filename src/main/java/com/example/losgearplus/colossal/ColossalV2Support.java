package com.example.losgearplus.colossal;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Colossal V1 e Colossal V2 como identidades INDIVIDUAIS (exclusivas) sobre o mesmo poder do DAOT.
 *
 * <ul>
 *   <li><b>V1</b>: tag {@code colossal} (do DAOT), sem nenhuma tag de V2.</li>
 *   <li><b>V2</b>: tag {@code colossalv2} (ou a antiga {@code colossal_v2}). O DAOT continua dono dos poderes,
 *       então a tag base {@code colossal} também é mantida em quem é V2.</li>
 * </ul>
 * Quem é V2 nunca é V1 ao mesmo tempo: {@link #clearV2} é chamado quando se atribui o V1.
 * O estado V2 é sincronizado para TODOS os clientes (qualquer dimensão) ao entrar, ao atribuir e ao transformar.
 */
public final class ColossalV2Support {
	public static final String V2_TAG = "colossalv2";
	public static final String V2_TAG_LEGACY = "colossal_v2";
	public static final String DAOT_TAG = "colossal";

	private ColossalV2Support() {}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(ColossalV2SyncPayload.TYPE, ColossalV2SyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer joining = handler.player;
			// quem entrou recebe o estado de todos os V2 que já estão no servidor...
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player != joining && hasV2Tag(player)) {
					ServerPlayNetworking.send(joining, new ColossalV2SyncPayload(player.getUUID(), true));
				}
			}
			// ...e todos (inclusive ele mesmo) recebem o estado dele
			if (hasV2Tag(joining)) broadcast(joining, true);
		});
	}

	public static boolean hasV2Tag(ServerPlayer player) {
		return player.getTags().contains(V2_TAG) || player.getTags().contains(V2_TAG_LEGACY);
	}

	/** O DAOT ainda é dono dos poderes do Colossal; garante a tag base em quem é V2 e ressincroniza o visual. */
	public static void ensureDaotColossalTag(ServerPlayer player) {
		boolean v2 = hasV2Tag(player);
		if (v2 && hasOtherShifterTag(player)) {
			// Tag V2 velha: o jogador já foi setado para outro shifter. Não recoloca a tag base e limpa o V2.
			clearV2(player);
			return;
		}
		if (v2) player.addTag(DAOT_TAG);
		broadcast(player, v2);
	}

	/** O jogador tem a tag de algum shifter que NÃO é o Colossal (nem V1 nem V2)? */
	public static boolean hasOtherShifterTag(ServerPlayer player) {
		for (String tag : player.getTags()) {
			if (isOtherShifterTag(tag)) return true;
		}
		return false;
	}

	private static boolean isOtherShifterTag(String tag) {
		return com.example.losgearplus.shifter.ShifterTypes.TAGS.contains(tag)
				&& !DAOT_TAG.equals(tag) && !V2_TAG.equals(tag) && !V2_TAG_LEGACY.equals(tag);
	}

	/** Torna o jogador V2 (a tag base do DAOT é garantida). */
	public static void setV2(ServerPlayer player) {
		player.removeTag(V2_TAG_LEGACY);
		player.addTag(V2_TAG);
		ensureDaotColossalTag(player);
	}

	/** Torna o jogador Colossal V1 puro: remove qualquer marca de V2 e avisa todos os clientes. */
	public static void clearV2(ServerPlayer player) {
		player.removeTag(V2_TAG);
		player.removeTag(V2_TAG_LEGACY);
		broadcast(player, false);
	}

	/**
	 * Chamado depois de um {@code /daot shifter set|remove} que deu certo, para o V1 e o V2 não se misturarem:
	 * <ul>
	 *   <li>{@code colossal}: foi pedido o Colossal NORMAL do DAOT (ou ele foi removido) -> some qualquer V2;</li>
	 *   <li>outro tipo: o V2 só some se o jogador perdeu a tag base {@code colossal} (o DAOT a tira ao trocar de
	 *       poder; com "vários shifters" ligado ela fica e o V2 também).</li>
	 * </ul>
	 * O {@code /lgp shifter set <alvos> colossalv2} roda este mesmo comando do DAOT por dentro e logo depois
	 * atribui o V2 de novo, então o V2 dado pelo /lgp continua valendo.
	 */
	public static void onDaotShifterCommand(Iterable<ServerPlayer> targets, String type) {
		boolean colossalCommand = DAOT_TAG.equals(type);
		for (ServerPlayer player : targets) {
			if (!hasV2Tag(player)) continue;
			if (colossalCommand || !player.getTags().contains(DAOT_TAG)) clearV2(player);
		}
	}

	/**
	 * Chamado depois de um {@code /daot shifter set <alvos> <tipo>} que deu certo. "set" TROCA o shifter:
	 * se o tipo novo não é o Colossal (e é um shifter conhecido), o Colossal V1/V2 antigo sai do jogador
	 * (tag base {@code colossal} e tags V2), senão ele continuava valendo na transformação e no Partial Shifting.
	 */
	public static void onDaotShifterSet(Iterable<ServerPlayer> targets, String type) {
		if (isOtherShifterTag(type)) {
			for (ServerPlayer player : targets) {
				player.removeTag(DAOT_TAG);
				clearV2(player);
			}
		} else {
			onDaotShifterCommand(targets, type);
		}
	}

	/** Envia o estado V2 deste jogador a todos os jogadores do servidor (qualquer dimensão). */
	public static void broadcast(ServerPlayer player, boolean v2) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		ColossalV2SyncPayload payload = new ColossalV2SyncPayload(player.getUUID(), v2);
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(viewer, ColossalV2SyncPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}
}
package com.example.losgearplus.codex;

import com.example.losgearplus.LosGearPlus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes do Codex. Cliente -> servidor: {@link Request}, {@link Assign}. Servidor -> cliente: {@link Sync}, {@link Cooldown}. */
public final class CodexPayloads {
	private CodexPayloads() {}

	/** "Quero ver/atualizar o Codex." O servidor responde com {@link Sync} (open = true). Sem dados. */
	public record Request() implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(LosGearPlus.id("codex_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Equipa uma skill num slot. {@code skill}: id da skill, "" = padrão (original), "-" = vazio.
	 * {@code slot} = -1 restaura o titã inteiro. O servidor valida tudo (titã, slot, compatibilidade, desbloqueio).
	 */
	public record Assign(String titan, int slot, String skill) implements CustomPacketPayload {
		public static final Type<Assign> TYPE = new Type<>(LosGearPlus.id("codex_assign"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Assign> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Assign::titan,
				ByteBufCodecs.VAR_INT, Assign::slot,
				ByteBufCodecs.STRING_UTF8, Assign::skill,
				Assign::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Estado completo do Codex do jogador.
	 *
	 * @param open     true = abrir a tela (resposta ao Request); false = só atualizar o cache
	 * @param active   titã que o jogador está montando agora ("" se nenhum)
	 * @param titans   titãs que ele possui (as abas)
	 * @param loadouts uma linha por titã: {@code titan|slot1|...|slot9} ("" = padrão, "-" = vazio)
	 * @param unlocked skills liberadas (manualmente ou por stack)
	 * @param stacks   uma linha por stack do jogador: {@code titã|nível}
	 */
	public record Sync(boolean open, String active, List<String> titans, List<String> loadouts, List<String> unlocked,
			List<String> stacks) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(LosGearPlus.id("codex_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Sync::open,
				ByteBufCodecs.STRING_UTF8, Sync::active,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::titans,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::loadouts,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::unlocked,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::stacks,
				Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Apertou uma das 3 teclas de stack: usa a skill que está naquele slot (0 a 2) da aba de stacks. */
	public record UseStack(int slot) implements CustomPacketPayload {
		public static final Type<UseStack> TYPE = new Type<>(LosGearPlus.id("codex_use_stack"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UseStack> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, UseStack::slot,
				UseStack::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Uma skill própria entrou em recarga: o cliente desenha o contador por cima do slot. */
	public record Cooldown(String skill, int ticks) implements CustomPacketPayload {
		public static final Type<Cooldown> TYPE = new Type<>(LosGearPlus.id("codex_cooldown"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cooldown> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Cooldown::skill,
				ByteBufCodecs.VAR_INT, Cooldown::ticks,
				Cooldown::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}

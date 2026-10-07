package com.example.losgearplus.limb;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.shifter.ShifterMastery;
import com.example.losgearplus.shifter.ShifterTypes;
import com.example.losgearplus.steam.SteamHealServer;
import daot.ShifterTitan;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Lógica de servidor do Limb Dismemberment: perder/restaurar partes, regeneração (Steam Heal x maestria),
 * efeitos funcionais (mãos bloqueadas, lentidão, pulo, sem sprint) e sincronização.
 */
public final class LimbManager {
	private LimbManager() {}

	private static final ResourceLocation SPEED_ID = LosGearPlus.id("limb_speed");
	private static final ResourceLocation JUMP_ID = LosGearPlus.id("limb_jump");

	/** Jogadores que estão com modificadores de atributo nossos aplicados. */
	private static final Set<UUID> MODDED = new HashSet<>();

	/** Só cliente: último aviso de "mão sem braço" (para não spammar). */
	private static long lastWarn = -100;

	public static void init() {
		LimbData.init();
		PayloadTypeRegistry.playS2C().register(LimbSyncPayload.TYPE, LimbSyncPayload.CODEC);
		LimbSync.init();
		ServerTickEvents.END_SERVER_TICK.register(LimbManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MODDED.remove(handler.getPlayer().getUUID()));
		registerHandBlocks();
	}

	// ---- API ----------------------------------------------------------------------------------------------

	/** Faz o jogador perder a parte. {@code fx} = sangue, som e mensagem. True se algo mudou. */
	public static boolean lose(ServerPlayer p, LimbPart part, boolean fx) {
		LimbState st = LimbData.edit(p);
		if (!st.lose(part)) return false;
		MODDED.add(p.getUUID());
		applyAll(p, st);
		LimbSync.send(p, true);
		if (fx) effects(p, part);
		return true;
	}

	/** Restaura uma parte (ou tudo, se {@code part} for null) imediatamente. */
	public static void restore(ServerPlayer p, LimbPart part) {
		LimbState st = LimbData.edit(p);
		if (part == null) st.restoreAll(); else st.restore(part);
		applyAll(p, st);
		LimbSync.send(p, true);
	}

	private static void effects(ServerPlayer p, LimbPart part) {
		double y = part.isEye() ? 1.6 : part.isArm() ? 1.3 : 0.5;
		p.serverLevel().sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
				p.getX(), p.getY() + y, p.getZ(), 30, 0.25, 0.2, 0.25, 0.1);
		p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
		p.displayClientMessage(Component.translatable("los_gear_plus.limb.lost." + part.id()), true);
	}

	// ---- tick ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			LimbState st = p.getAttached(LimbData.ATTACHMENT);
			if (st == null || st.isPristine()) {
				if (MODDED.remove(id)) {
					clearMods(p);
					Entity v = p.getVehicle();
					if (v instanceof ShifterTitan && v instanceof LivingEntity le) clearMods(le);
					LimbSync.send(p, false);
				}
				continue;
			}

			boolean changed = false;
			if (p.isAlive() && ShifterTypes.isShifter(p)) {
				float steam = SteamHealServer.isActive(p) ? 1f : LimbRules.PASSIVE_REGROW;
				if (steam > 0f) {
					changed = st.regrow(steam * LimbRules.regrowSpeed(ShifterMastery.getLevel(id)));
				}
			}

			MODDED.add(id);
			if (changed || now % 10 == 0) applyAll(p, st);
			if (LimbRules.cannotSprint(st) && p.isSprinting()) p.setSprinting(false);
			LimbSync.tick(p, now);
		}
	}

	/** Aplica nos dois corpos: o jogador e, se estiver montado, o titã dele. */
	private static void applyAll(ServerPlayer p, LimbState st) {
		applyMods(p, st);
		Entity v = p.getVehicle();
		if (v instanceof ShifterTitan && v instanceof LivingEntity le) applyMods(le, st);
	}

	private static void applyMods(LivingEntity e, LimbState st) {
		setMod(e, Attributes.MOVEMENT_SPEED, SPEED_ID, LimbRules.speedMultiplier(st) - 1.0);
		setMod(e, Attributes.JUMP_STRENGTH, JUMP_ID, LimbRules.jumpMultiplier(st) - 1.0);
	}

	private static void clearMods(LivingEntity e) {
		setMod(e, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0);
		setMod(e, Attributes.JUMP_STRENGTH, JUMP_ID, 0.0);
	}

	private static void setMod(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double amount) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) return;
		AttributeModifier cur = inst.getModifier(id);
		if (Math.abs(amount) < 1.0E-4) {
			if (cur != null) inst.removeModifier(id);
			return;
		}
		if (cur != null && Math.abs(cur.amount() - amount) < 1.0E-4) return;
		inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	// ---- mãos bloqueadas (roda nos dois lados: o cliente também cancela, sem pacote) -------------------------

	private static void registerHandBlocks() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (blocked(player, world, hand)) return InteractionResultHolder.fail(player.getItemInHand(hand));
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				blocked(player, world, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				blocked(player, world, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				blocked(player, world, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				blocked(player, world, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	private static boolean blocked(Player player, Level world, InteractionHand hand) {
		if (player.isSpectator() || LimbRules.handUsable(player, hand)) return false;
		if (world.isClientSide) {
			long t = world.getGameTime();
			if (t - lastWarn >= 30) {
				lastWarn = t;
				player.displayClientMessage(Component.translatable("los_gear_plus.limb.hand_lost"), true);
			}
		}
		return true;
	}
}

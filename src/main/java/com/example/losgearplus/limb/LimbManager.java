package com.example.losgearplus.limb;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.shifter.ShifterMastery;
import com.example.losgearplus.shifter.ShifterTypes;
import com.example.losgearplus.steam.SteamHealServer;
import daot.ShifterTitan;
import daot.network.ModNetworking;
import java.lang.reflect.Method;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Lógica de servidor do Limb Dismemberment: perder/restaurar partes, regeneração (Steam Heal x maestria),
 * efeitos funcionais (mãos bloqueadas, lentidão, pulo, sem sprint) e sincronização.
 */
public final class LimbManager {
	private LimbManager() {}

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("los_gear_plus");
	private static Method guardHandler;

	private static final ResourceLocation SPEED_ID = LosGearPlus.id("limb_speed");
	private static final ResourceLocation JUMP_ID = LosGearPlus.id("limb_jump");

	/** Jogadores que estão com modificadores de atributo nossos aplicados. */
	private static final Set<UUID> MODDED = new HashSet<>();
	private static final java.util.Map<UUID, Integer> SIGNATURES = new java.util.HashMap<>();

	/** Último titã de shifter em que cada jogador estava montado ({@link #NO_TITAN} = nenhum). Detecta transformação nova. */
	private static final java.util.Map<UUID, UUID> LAST_TITAN = new java.util.HashMap<>();
	private static final UUID NO_TITAN = new UUID(0L, 0L);

	/** Só cliente: último aviso de "mão sem braço" (para não spammar). */
	private static long lastWarn = -100;

	public static void init() {
		LimbData.init();
		PayloadTypeRegistry.playS2C().register(LimbSyncPayload.TYPE, LimbSyncPayload.CODEC);
		LimbSync.init();
		ServerTickEvents.END_SERVER_TICK.register(LimbManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MODDED.remove(handler.getPlayer().getUUID());
			SIGNATURES.remove(handler.getPlayer().getUUID());
			LAST_TITAN.remove(handler.getPlayer().getUUID());
		});
		registerHandBlocks();
	}

	// ---- API ----------------------------------------------------------------------------------------------

	/** Faz o jogador perder a parte, com os efeitos no corpo do jogador. */
	public static boolean lose(ServerPlayer p, LimbPart part, boolean fx) {
		return lose(p, part, p, fx);
	}

	/**
	 * Faz o jogador perder a parte. {@code body} é onde acontece o corte (o próprio jogador ou o titã dele) e
	 * {@code fx} liga sangue, som e mensagem. True se algo mudou.
	 */
	public static boolean lose(ServerPlayer p, LimbPart part, Entity body, boolean fx) {
		LimbState st = LimbData.edit(p);
		if (!st.lose(part)) return false;
		MODDED.add(p.getUUID());
		evictDeadHands(p);
		if (part.isArm()) lowerTitanGuard(p);
		applyAll(p, st);
		LimbSync.send(p, true);
		if (fx) effects(p, body, part);
		return true;
	}

	/**
	 * Titã sem braço não fica em guarda: baixa a guarda que estiver levantada chamando o próprio handler do DAOT
	 * ({@code handleTitanArm(player, false)}, privado, por isso reflexão). Subir a guarda de novo é barrado por mixin.
	 */
	private static void lowerTitanGuard(ServerPlayer p) {
		if (!(p.getVehicle() instanceof ShifterTitan)) return;
		try {
			if (guardHandler == null) {
				guardHandler = ModNetworking.class.getDeclaredMethod("handleTitanArm", ServerPlayer.class, boolean.class);
				guardHandler.setAccessible(true);
			}
			guardHandler.invoke(null, p, false);
		} catch (Throwable t) {
			LOG.warn("Nao consegui baixar a guarda do titan: {}", t.toString());
		}
	}

	/** Restaura uma parte (ou tudo, se {@code part} for null) imediatamente. */
	public static void restore(ServerPlayer p, LimbPart part) {
		LimbState st = LimbData.edit(p);
		if (part == null) st.restoreAll(); else st.restore(part);
		applyAll(p, st);
		LimbSync.send(p, true);
	}

	private static void effects(ServerPlayer owner, Entity body, LimbPart part) {
		double h = body.getBbHeight();
		double y = body.getY() + h * (part.isEye() ? 0.9 : part.isArm() ? 0.65 : 0.25);
		int count = (int) Math.max(30, Math.min(220, 30 * h / 1.8));
		double spread = Math.max(0.25, body.getBbWidth() * 0.3);
		owner.serverLevel().sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
				body.getX(), y, body.getZ(), count, spread, spread * 0.8, spread, 0.1);
		owner.serverLevel().playSound(null, body.getX(), y, body.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, (float) Math.min(4.0, 1.0 + h / 4.0), 0.6f);
		owner.displayClientMessage(Component.translatable("los_gear_plus.limb.lost." + part.id()), true);
	}

	/**
	 * Mão sem braço não segura nada: o que estiver nela vai para o inventário (ou cai no chão). Assim nenhum
	 * item/lâmina fica "utilizável" numa mão que não existe, nem para o DAOT, que lê as mãos direto.
	 */
	private static void evictDeadHands(ServerPlayer p) {
		if (p.getVehicle() instanceof ShifterTitan) return; // em forma titã as mãos humanas não seguram nada
		for (InteractionHand hand : InteractionHand.values()) {
			if (LimbRules.handUsable(p, hand)) continue;
			ItemStack held = p.getItemInHand(hand);
			if (held.isEmpty()) continue;
			if (com.example.losgearplus.grip.GripMarker.isBound(held)) continue; // grip do ODMG: o GripStorage o estaciona
			p.setItemInHand(hand, ItemStack.EMPTY);
			if (!p.getInventory().add(held)) p.drop(held, false);
		}
	}

	// ---- tick ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			trackTransformation(p);
			LimbState st = p.getAttached(LimbData.ATTACHMENT);
			if (st == null || st.isPristine()) {
				SIGNATURES.remove(id);
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
				boolean steamOn = SteamHealServer.isActive(p);
				Entity ride = p.getVehicle();
				LivingEntity titanBody = ride instanceof ShifterTitan && ride instanceof LivingEntity le ? le : null;
				// Passiva só na forma de titã; na forma humana só o Steam Heal regenera.
				float steam = steamOn ? 1f : (titanBody != null ? LimbRules.PASSIVE_REGROW : 0f);
				if (steam > 0f) {
					changed = regrowWithStamina(p, st, steam, now);
					if (changed && !steamOn && now % LimbRules.PASSIVE_SMOKE_INTERVAL_TICKS == 0) {
						SteamHealServer.passiveSmoke(p.serverLevel(), titanBody);
					}
				}
			}

			MODDED.add(id);
			// atributos só mudam quando muda QUEM está faltando; reaplica de vez em quando por segurança
			int sig = st.statusSignature();
			Integer prevSig = SIGNATURES.put(id, sig);
			if (prevSig == null || prevSig != sig || now % 20 == 0) applyAll(p, st);
			if (now % 5 == 0) evictDeadHands(p);
			if (LimbRules.cannotSprint(st) && p.isSprinting()) p.setSprinting(false);
			LimbSync.tick(p);
		}
	}

	/**
	 * Transformar de novo cria um titã NOVO: o corpo é refeito por inteiro, então todos os membros perdidos voltam
	 * (no titã e no corpo humano, que usam o mesmo estado). Detecta pelo UUID do titã em que o jogador monta; a
	 * primeira observação do jogador (entrou no servidor) só registra, para não restaurar ao reentrar já montado.
	 */
	private static void trackTransformation(ServerPlayer p) {
		Entity v = p.getVehicle();
		UUID current = v instanceof ShifterTitan ? v.getUUID() : NO_TITAN;
		UUID previous = LAST_TITAN.put(p.getUUID(), current);
		if (previous == null || previous.equals(current) || current.equals(NO_TITAN)) return;
		LimbState st = p.getAttached(LimbData.ATTACHMENT);
		if (st != null && !st.isPristine()) restore(p, null);
	}

	/**
	 * Um tick de regeneração. Abaixo da maestria 9 cada parte que cresce gasta stamina shifter (o custo cai com a
	 * maestria); sem stamina suficiente a regeneração pausa. Nível 9 (e o Cart Titan) regenera de graça.
	 */
	private static boolean regrowWithStamina(ServerPlayer p, LimbState st, float steam, long now) {
		UUID id = p.getUUID();
		float speed = steam * LimbRules.regrowSpeed(ShifterMastery.getLevel(id));
		float fraction = st.regrowCost(speed) * LimbRules.staminaFactor(ShifterMastery.ruleLevel(id));
		float amount = 0f;
		if (fraction > 0f) {
			amount = fraction * ModNetworking.getMaxStaminaForUUID(id);
			if (ModNetworking.getStamina(id) < amount) {
				if (now % 100 == 0) p.displayClientMessage(Component.translatable("los_gear_plus.limb.no_stamina"), true);
				return false;
			}
		}
		boolean changed = st.regrow(speed);
		if (changed && amount > 0f) ModNetworking.drainStamina(id, amount);
		return changed;
	}

	/** Aplica nos dois corpos: o jogador e, se estiver montado, o titã dele. */
	private static void applyAll(ServerPlayer p, LimbState st) {
		applyMods(p, st);
		Entity v = p.getVehicle();
		if (v instanceof ShifterTitan && v instanceof LivingEntity le) {
			// o titã usa a regra própria: ajoelhado quase não anda (o pulo/esquiva são bloqueados por mixin)
			setMod(le, Attributes.MOVEMENT_SPEED, SPEED_ID, LimbRules.titanSpeedMultiplier(st) - 1.0);
		}
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
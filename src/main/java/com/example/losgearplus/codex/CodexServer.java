package com.example.losgearplus.codex;

import com.example.losgearplus.limb.LimbLookup;
import com.example.losgearplus.limb.LimbRules;
import com.example.losgearplus.limb.LimbState;
import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.shifter.ShifterMastery;
import daot.ShifterTitan;
import daot.network.ModNetworking;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lado servidor do Titan Ability Codex: pacotes, sincronização, comandos e o ROTEAMENTO das teclas de habilidade.
 *
 * <p>O roteamento é chamado pelo mixin {@code DaotTitanAbilityLoadoutMixin} no começo de
 * {@code ModNetworking.handleTitanAbility}: o jogador apertou a tecla N; aqui descobrimos qual skill ele colocou
 * no slot N e o que fazer ({@link #PASS}, {@link #CANCEL} ou chamar o DAOT com outro número).
 */
public final class CodexServer {
	private CodexServer() {}

	/** Deixa o DAOT tratar a tecla normalmente (slot padrão, titã não suportado...). */
	public static final int PASS = -1;
	/** Engole a tecla (slot vazio, skill bloqueada ou skill própria já executada). */
	public static final int CANCEL = 0;

	public static void init() {
		TitanCodex.init();
		SmokeCurtain.init();
		CodexScheduler.init();

		PayloadTypeRegistry.playC2S().register(CodexPayloads.Request.TYPE, CodexPayloads.Request.CODEC);
		PayloadTypeRegistry.playC2S().register(CodexPayloads.Assign.TYPE, CodexPayloads.Assign.CODEC);
		PayloadTypeRegistry.playS2C().register(CodexPayloads.Sync.TYPE, CodexPayloads.Sync.CODEC);
		PayloadTypeRegistry.playS2C().register(CodexPayloads.Cooldown.TYPE, CodexPayloads.Cooldown.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(CodexPayloads.Request.TYPE,
				(payload, context) -> sync(context.player(), true));
		ServerPlayNetworking.registerGlobalReceiver(CodexPayloads.Assign.TYPE,
				(payload, context) -> assign(context.player(), payload));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer(), false));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CodexCooldowns.clear(handler.getPlayer().getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer, false));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	// ---- roteamento das teclas ---------------------------------------------------------------------------

	/**
	 * @param pressed número da tecla (1..9), o mesmo que o DAOT recebe
	 * @return {@link #PASS}, {@link #CANCEL} ou um número (>0) com que o handler do DAOT deve ser chamado no lugar
	 */
	public static int route(ServerPlayer player, int pressed) {
		Entity vehicle = player.getVehicle();
		String titan = TitanIds.of(vehicle);
		if (titan == null || !(vehicle instanceof ShifterTitan shifterTitan) || !(vehicle instanceof LivingEntity living)) return PASS;
		// Mesma regra do DAOT: só o dono do titã usa as habilidades dele.
		if (!player.getUUID().equals(shifterTitan.getShifterUUID())) return PASS;

		int slot = pressed - 1;
		if (slot < 0 || slot >= TitanIds.slotCount(titan)) return PASS;

		String skillId = CodexData.getSlot(player, titan, slot);
		if (skillId.equals(CodexData.DEFAULT)) return PASS;
		if (skillId.equals(CodexData.EMPTY)) return CANCEL;

		TitanSkill skill = TitanCodex.find(skillId).orElse(null);
		if (skill == null || !skill.compatibleWith(titan)) return PASS; // skill removida de uma versão antiga: volta ao padrão

		if (!CodexData.isUnlocked(player, skill)) {
			player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.locked"), true);
			return CANCEL;
		}
		if (skill instanceof CustomTitanSkill custom) {
			runCustom(player, living, custom);
			return CANCEL;
		}
		int original = skill.daotAbilityNumber();
		return original == pressed ? PASS : original;
	}

	private static void runCustom(ServerPlayer player, LivingEntity titan, CustomTitanSkill skill) {
		UUID id = player.getUUID();
		boolean creative = player.isCreative();

		if (!creative) {
			int left = CodexCooldowns.remaining(player, skill.id());
			if (left > 0) {
				player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.cooldown", (left + 19) / 20), true);
				return;
			}
		}
		if (skill.needsArms()) {
			LimbState limbs = LimbLookup.riderState(player);
			if (limbs != null && !LimbRules.titanAbilitiesOk(limbs)) {
				player.displayClientMessage(Component.translatable("los_gear_plus.limb.titan_no_arms"), true);
				return;
			}
		}

		// Maestria 9 não gasta stamina (igual ao resto do mod), nem o criativo.
		float cost = skill.staminaFraction() * ModNetworking.getMaxStaminaForUUID(id);
		boolean free = creative || cost <= 0f || ShifterMastery.ruleLevel(id) >= ShifterMastery.MAX_LEVEL;
		if (!free && ModNetworking.getStamina(id) < cost) {
			player.displayClientMessage(Component.translatable("los_gear_plus.codex.msg.no_stamina"), true);
			return;
		}

		boolean done;
		try {
			done = skill.activate(player, titan);
		} catch (Throwable t) {
			LosGearPlus.LOGGER.error("Falha ao executar a skill {}", skill.id(), t);
			return;
		}
		if (!done) return;
		if (!free) ModNetworking.drainStamina(id, cost);
		if (!creative) CodexCooldowns.start(player, skill.id(), skill.cooldownTicks());
	}

	// ---- pacotes -----------------------------------------------------------------------------------------

	private static void assign(ServerPlayer player, CodexPayloads.Assign p) {
		String titan = p.titan();
		if (!TitanIds.isSupported(titan) || !TitanIds.owned(player).contains(titan)) return;

		if (p.slot() == -1) {
			CodexData.resetTitan(player, titan);
			sync(player, false);
			return;
		}
		if (p.slot() < 0 || p.slot() >= TitanIds.slotCount(titan)) return;

		String skillId = p.skill();
		if (!skillId.equals(CodexData.DEFAULT) && !skillId.equals(CodexData.EMPTY)) {
			TitanSkill skill = TitanCodex.find(skillId).orElse(null);
			if (skill == null || !skill.compatibleWith(titan) || !CodexData.isUnlocked(player, skill)) {
				sync(player, false); // pedido inválido: reenvia o estado real para o cliente desfazer a tela
				return;
			}
		}
		CodexData.setSlot(player, titan, p.slot(), skillId);
		sync(player, false);
	}

	public static void sync(ServerPlayer player, boolean open) {
		if (!ServerPlayNetworking.canSend(player, CodexPayloads.Sync.TYPE)) return;
		List<String> titans = TitanIds.owned(player);
		String active = TitanIds.of(player.getVehicle());
		if (active == null) active = titans.isEmpty() ? "" : titans.get(0);

		List<String> lines = new ArrayList<>();
		for (String titan : titans) lines.add(titan + "|" + String.join("|", CodexData.slots(player, titan)));

		ServerPlayNetworking.send(player, new CodexPayloads.Sync(open, active, titans, lines, CodexData.unlockedList(player)));
	}

	// ---- comandos (permissão 2) --------------------------------------------------------------------------

	/**
	 * <pre>
	 *  /codex list                         lista as skills registradas
	 *  /codex unlock &lt;players&gt; &lt;skill&gt;     libera uma skill bloqueada (unlockedByDefault = false)
	 *  /codex lock &lt;players&gt; &lt;skill&gt;       bloqueia de novo
	 *  /codex reset &lt;players&gt;              volta TODOS os slots às habilidades originais
	 * </pre>
	 */
	private static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("codex")
				.requires(src -> src.hasPermission(2))
				.then(Commands.literal("list").executes(ctx -> {
					for (TitanSkill s : TitanCodex.all()) {
						String who = s.isCustom() ? "custom" : "DAOT #" + s.daotAbilityNumber();
						ctx.getSource().sendSuccess(() -> Component.literal(s.id() + "  [" + s.titanId() + ", " + who
								+ (s.unlockedByDefault() ? "" : ", locked") + "]"), false);
					}
					return TitanCodex.all().size();
				}))
				.then(Commands.literal("unlock").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_IDS)
								.executes(ctx -> setLock(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
										ResourceLocationArgument.getId(ctx, "skill"), true)))))
				.then(Commands.literal("lock").then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_IDS)
								.executes(ctx -> setLock(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
										ResourceLocationArgument.getId(ctx, "skill"), false)))))
				.then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> {
							Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
							for (ServerPlayer p : targets) {
								CodexData.resetAll(p);
								sync(p, false);
							}
							ctx.getSource().sendSuccess(() -> Component.literal("Codex reset for " + targets.size() + " player(s)."), true);
							return targets.size();
						}))));
	}

	private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SKILL_IDS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(TitanCodex.all().stream().map(TitanSkill::id), builder);

	private static int setLock(CommandSourceStack src, Collection<ServerPlayer> targets, ResourceLocation skill, boolean unlock) {
		String id = skill.toString();
		if (TitanCodex.find(id).isEmpty()) {
			src.sendFailure(Component.literal("Unknown skill: " + id));
			return 0;
		}
		int changed = 0;
		for (ServerPlayer p : targets) {
			if (unlock ? CodexData.unlock(p, id) : CodexData.lock(p, id)) changed++;
			sync(p, false);
		}
		int n = changed;
		src.sendSuccess(() -> Component.literal((unlock ? "Unlocked " : "Locked ") + id + " for " + n + " player(s)."), true);
		return changed;
	}
}

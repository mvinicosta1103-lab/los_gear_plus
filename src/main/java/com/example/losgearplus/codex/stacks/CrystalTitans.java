package com.example.losgearplus.codex.stacks;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.codex.CodexPayloads;
import com.mojang.serialization.Codec;
import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.CartCannonShellEntity;
import daot.FemaleTitanEntity;
import daot.RockProjectileEntity;
import daot.ThunderSpearEntity;
import daot.WarhammerTitanEntity;
import daot.gun.AntiTitanRifleItem;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Titã cristalizado (a skill Iron Skin): depois de ejetar o piloto, o corpo do titã, que o DAOT deixa ajoelhado,
 * vira uma estátua de cristal: imóvel, que não some (sem fumaça nem som de evaporação) e INDESTRUTÍVEL. Titãs puros
 * comuns não o machucam. Só estas fontes o estilhaçam (e, para os admins, o /kill e o modo criativo):
 * Thunder Spear, tiro da Anti-Titan Rifle, tiro de canhão do Cart, pedras do Beast, golpes do Armored, do Warhammer e
 * do Jaw (ataques e mordidas) e golpes do Attack/Female com o punho/pé endurecido (cristalizado).
 *
 * <ul>
 *   <li>O estado fica NO titã (Data Attachment persistente), então sobrevive a reiniciar o servidor.</li>
 *   <li>Imóvel: {@code noAi} + velocidade zerada + o contador de sumiço do DAOT ({@code despawnAtGameTime}) zerado.
 *       Como rede de segurança, o {@code EntityRemoveMixin} impede qualquer {@code discard}/kill enquanto cristalizado.</li>
 *   <li>Indestrutível: o dano é cancelado em {@link #guard}, chamado pelo {@code DaotTitanHooksMixin} (os titãs do
 *       DAOT têm o próprio {@code hurt}) e pelo evento ALLOW_DAMAGE (os que não têm).</li>
 *   <li>A textura de cristal é desenhada no cliente ({@code CrystalLayer}); aqui só avisamos quem está vendo.</li>
 * </ul>
 */
public final class CrystalTitans {
	private CrystalTitans() {}

	/** Tick de jogo em que a cristalização começou. Presente = o titã está cristalizado. */
	public static final AttachmentType<Long> CRYSTAL_START = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.buildAndRegister(LosGearPlus.id("crystal_titan"));

	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Map<Class<?>, Field> DESPAWN_FIELDS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CrystalTitans::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
		// Titã cristalizado que carrega de novo (o chunk volta a ser carregado): volta a ficar imóvel.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (isCrystal(entity)) adopt(entity);
		});
		// Quem passa a ver o titã depois precisa saber que ele é de cristal.
		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			Long start = entity.getAttached(CRYSTAL_START);
			if (start != null) sendTo(player, entity, CodexPayloads.CrystalFx.FULL, start, 0L);
		});
		// Titãs que NÃO têm hurt próprio (herdam o de LivingEntity): o evento basta.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !guard(entity, source));
	}

	public static boolean isCrystal(Entity entity) {
		return entity.hasAttached(CRYSTAL_START);
	}

	/** Cristaliza o corpo do titã (já sem piloto). */
	public static void start(LivingEntity titan) {
		if (!(titan.level() instanceof ServerLevel level) || isCrystal(titan)) return;
		long now = level.getGameTime();
		titan.setAttached(CRYSTAL_START, now);
		adopt(titan);
		broadcast(titan, CodexPayloads.CrystalFx.FULL, now, 0L);

		double w = Math.max(1.0, titan.getBbWidth()), h = Math.max(2.0, titan.getBbHeight());
		level.playSound(null, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 6.0f, 0.5f);
		level.sendParticles(ParticleTypes.END_ROD, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), (int) Math.min(300, 40 + h * 4), w * 0.5, h * 0.5, w * 0.5, 0.05);
	}

	/** Deixa o titã como uma estátua: sem IA, sem se mover, sem sumir. */
	private static void adopt(Entity entity) {
		ACTIVE.add(entity.getUUID());
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		entity.setDeltaMovement(Vec3.ZERO);
		neutralizeDespawn(entity);
	}

	/**
	 * O DAOT agenda o sumiço do titã vazio ({@code despawnAtGameTime}, +60 s). Zeramos esse campo (privado) por
	 * reflexão; se ele não existir mais, o {@code EntityRemoveMixin} ainda segura a remoção.
	 */
	private static void neutralizeDespawn(Entity entity) {
		Field field = despawnField(entity.getClass());
		if (field == null) return;
		try {
			field.setLong(entity, -1L);
		} catch (IllegalAccessException | RuntimeException ignored) {
			// sem acesso: fica a rede de segurança
		}
	}

	private static Field despawnField(Class<?> type) {
		if (DESPAWN_FIELDS.containsKey(type)) return DESPAWN_FIELDS.get(type);
		Field found = null;
		for (Class<?> c = type; c != null && c != Object.class && found == null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField("despawnAtGameTime");
				if (f.getType() == long.class) {
					f.setAccessible(true);
					found = f;
				}
			} catch (NoSuchFieldException | RuntimeException ignored) {
				// tenta a superclasse
			}
		}
		DESPAWN_FIELDS.put(type, found);
		return found;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 10 != 0 || ACTIVE.isEmpty()) return;
		for (Iterator<UUID> it = ACTIVE.iterator(); it.hasNext();) {
			UUID id = it.next();
			Entity entity = find(server, id);
			if (entity == null) continue; // descarregado: continua na lista para quando voltar
			if (entity.isRemoved() || !isCrystal(entity)) {
				it.remove();
				continue;
			}
			if (entity instanceof Mob mob && !mob.isNoAi()) mob.setNoAi(true);
			Vec3 v = entity.getDeltaMovement();
			entity.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
			entity.clearFire();
			neutralizeDespawn(entity);
			if (now % 40 == 0 && entity.level() instanceof ServerLevel level) { // brilhos de cristal
				double w = Math.max(1.0, entity.getBbWidth()), h = Math.max(2.0, entity.getBbHeight());
				level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + h * 0.5, entity.getZ(), 6, w * 0.5, h * 0.5, w * 0.5, 0.01);
			}
		}
	}

	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) return e;
		}
		return null;
	}

	// ---- dano ----------------------------------------------------------------------------------------------

	/**
	 * @return true = este titã está cristalizado e o dano deve ser CANCELADO. Se a fonte é um Thunder Spear (ou admin),
	 *         o titã é estilhaçado.
	 */
	public static boolean guard(LivingEntity titan, DamageSource source) {
		if (titan.level().isClientSide || !isCrystal(titan)) return false;
		if (breaksCrystal(source)) shatter(titan);
		return true;
	}

	private static boolean breaksCrystal(DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity owner = source.getEntity();

		// O DAOT reconhece o Thunder Spear assim: dano de explosão cuja entidade direta é o ThunderSpearEntity.
		if (direct instanceof ThunderSpearEntity || owner instanceof ThunderSpearEntity) return true;
		if (direct instanceof RockProjectileEntity) return true; // pedras do Beast
		if (direct instanceof CartCannonShellEntity) return true; // tiro de canhão do Cart
		if (isRifleShot(owner, direct)) return true; // Anti-Titan Rifle

		// Golpes de titã de shifter (o atacante é o corpo do titã que o jogador está pilotando).
		Entity attacker = owner != null ? owner : direct;
		if (attacker instanceof ArmoredTitanEntity || attacker instanceof WarhammerTitanEntity) return true;
		if (attacker instanceof daot.TestShifterTitanEntity) return true; // Jaw: ataques e mordidas
		if (attacker instanceof FemaleTitanEntity f) return f.getHardeningMode() != 0; // só com punho/pé endurecido
		if (attacker instanceof AttackTitanEntity a && StackSource.ofTitan(a) == StackSource.ATTACK) {
			return a.getHardeningMode() != 0; // só o Attack puro, e só endurecido
		}

		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true; // /kill e vazio
		return owner instanceof Player p && p.getAbilities().instabuild; // criativo
	}

	/** O tiro da arma é dano de jogador (playerAttack): o atacante e a entidade direta são o próprio atirador com a rifle na mão. */
	private static boolean isRifleShot(Entity owner, Entity direct) {
		if (!(owner instanceof Player p) || direct != owner) return false;
		return p.getMainHandItem().getItem() instanceof AntiTitanRifleItem || p.getOffhandItem().getItem() instanceof AntiTitanRifleItem;
	}

	/** Estilhaça: partículas, sons e remove o titã. */
	public static void shatter(LivingEntity titan) {
		if (!(titan.level() instanceof ServerLevel level) || !isCrystal(titan)) return;
		titan.removeAttached(CRYSTAL_START); // antes de remover: o EntityRemoveMixin só segura quem ainda é cristal
		ACTIVE.remove(titan.getUUID());
		broadcast(titan, CodexPayloads.CrystalFx.CLEAR, 0L, 0L);

		double w = Math.max(1.0, titan.getBbWidth()), h = Math.max(2.0, titan.getBbHeight());
		int n = (int) Math.min(400, 80 + h * 8);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
				titan.getX(), titan.getY() + h * 0.5, titan.getZ(), n, w * 0.5, h * 0.4, w * 0.5, 0.25);
		level.sendParticles(ParticleTypes.END_ROD, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), n / 2, w * 0.5, h * 0.4, w * 0.5, 0.2);
		level.playSound(null, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 8.0f, 0.5f);
		level.playSound(null, titan.getX(), titan.getY() + h * 0.5, titan.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 8.0f, 0.6f);
		titan.discard();
	}

	// ---- rede ----------------------------------------------------------------------------------------------

	/** Avisa quem está vendo o titã (ex.: {@code ARMS} para os punhos endurecidos, ou {@code FULL} para a estátua). */
	public static void broadcast(LivingEntity titan, int mode, long start, long end) {
		for (ServerPlayer p : PlayerLookup.tracking(titan)) sendTo(p, titan, mode, start, end);
	}

	private static void sendTo(ServerPlayer player, Entity entity, int mode, long start, long end) {
		if (ServerPlayNetworking.canSend(player, CodexPayloads.CrystalFx.TYPE)) {
			ServerPlayNetworking.send(player, new CodexPayloads.CrystalFx(entity.getId(), mode, start, end));
		}
	}
}
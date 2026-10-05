package com.example.losgearplus.client.mode;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.client.grip.OdmTankFollow;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Corrida do ODMG: com o ODMG Mode ligado, sprintar no chão toca a animação "odmrun" (Player Animation Library)
 * no lugar do sprint vanilla.
 *
 * Por que substitui de verdade: a PAL copia a pose vanilla para os ossos e depois sobrescreve os ossos que a
 * animação mexe. O odmrun anima body, torso, head, right_arm, left_arm, right_leg e left_leg, então o balanço
 * de membros do sprint vanilla é totalmente trocado enquanto a camada estiver ativa.
 *
 * Arquivo da animação: assets/los_gear_plus/player_animations/odmrun.json (id = los_gear_plus:odmrun).
 *
 * Por enquanto só o jogador local: o estado do ODMG Mode só é sincronizado com o próprio dono
 * (OdmgModeSyncPayload), então os outros jogadores não têm como saber se você está com o modo ligado.
 */
public final class OdmgRunAnimation {
	private OdmgRunAnimation() {}

	/** Id do arquivo de animação (namespace da pasta + nome da animação dentro do JSON). */
	private static final ResourceLocation ANIMATION_ID = LosGearPlus.id("odmrun");
	/** Id da camada desta animação na PAL. */
	private static final ResourceLocation LAYER_ID = LosGearPlus.id("odmg_run");
	/** Prioridade da camada (maior = aplicada por cima). Ajuste se outra camada (ex.: DAOT) cobrir esta. */
	private static final int PRIORITY = 1500;

	private static Animation cachedAnimation;
	private static RawAnimation cachedRaw;

	/** Chame em OdmgModeClient.init(). */
	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, PRIORITY,
				player -> new PlayerAnimationController(player, OdmgRunAnimation::handle));
	}

	private static PlayState handle(AnimationController controller, AnimationData state,
			AnimationController.AnimationSetter setter) {
		if (controller instanceof PlayerAnimationController pac && shouldRun(pac.getPlayer())) {
			RawAnimation raw = loopingAnimation();
			if (raw != null) {
				return setter.setAnimation(raw);
			}
		}
		// Sem isto, depois de um STOP a PAL não recomeçaria a mesma animação (ela só recarrega se for outra
		// ou se houver reset), e a corrida só tocaria uma vez.
		controller.forceAnimationReset();
		return PlayState.STOP;
	}

	private static boolean shouldRun(AbstractClientPlayer player) {
		if (player != Minecraft.getInstance().player) return false;
		return OdmgModeClient.isActive()
				&& player.isSprinting()
				&& player.onGround()
				&& !player.isPassenger()
				&& !player.isInWater()
				&& !player.isSwimming()
				&& !player.isFallFlying()
				&& !player.isSleeping()
				&& !player.onClimbable()
				&& !OdmTankFollow.isOdmAnimating(player.getUUID()); // em voo de ODM quem anima é o DAOT
	}

	/** RawAnimation em loop, reaproveitada enquanto o recurso não for recarregado (F3+T). */
	private static RawAnimation loopingAnimation() {
		Animation animation = PlayerAnimResources.getAnimation(ANIMATION_ID);
		if (animation == null) return null; // json não carregou (veja o log da PAL)
		if (animation != cachedAnimation) {
			cachedAnimation = animation;
			cachedRaw = RawAnimation.begin().thenLoop(animation);
		}
		return cachedRaw;
	}
}

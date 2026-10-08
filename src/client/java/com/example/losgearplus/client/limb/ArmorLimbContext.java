package com.example.losgearplus.client.limb;

import net.minecraft.world.entity.LivingEntity;

/** Quem está vestindo a armadura que o {@code HumanoidArmorLayer} está desenhando agora (só na thread de render). */
public final class ArmorLimbContext {
	private ArmorLimbContext() {}

	public static final ThreadLocal<LivingEntity> ENTITY = new ThreadLocal<>();
}

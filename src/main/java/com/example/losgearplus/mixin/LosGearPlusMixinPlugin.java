package com.example.losgearplus.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LosGearPlusMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOG = LoggerFactory.getLogger("los_gear_plus/mixin-plugin");

	private boolean intermediary = true;

	@Override
	public void onLoad(String mixinPackage) {
		String ns = "?";
		boolean byNamespace;
		try {
			ns = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
			byNamespace = "intermediary".equals(ns);
		} catch (Throwable t) {
			byNamespace = true; // sem resolvedor: assume o jogo normal
		}

		boolean mojangNames = false;
		try {
			ClassLoader cl = LosGearPlusMixinPlugin.class.getClassLoader();
			mojangNames = cl != null && cl.getResource("net/minecraft/world/entity/LivingEntity.class") != null;
		} catch (Throwable ignored) {
			// mantém false: decide só pelo namespace
		}

		intermediary = byNamespace && !mojangNames;
		LOG.info("[los_gear_plus] namespace='{}', classes com nome Mojang={} -> usando mixins {}",
				ns, mojangNames, intermediary ? "INTERMEDIARY" : "NAMED");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith("IntermediaryMixin")) return intermediary;
		if (mixinClassName.endsWith("NamedMixin")) return !intermediary;
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
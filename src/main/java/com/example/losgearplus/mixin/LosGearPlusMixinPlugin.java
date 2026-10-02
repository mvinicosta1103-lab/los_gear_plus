package com.example.losgearplus.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Escolhe, entre os mixins que dependem do nome (intermediary x Mojang) de classes do Minecraft no descritor
 * de um método do DAOT, o que combina com o ambiente em execução. Assim nunca sobra um alvo inexistente
 * (que, com require = 1, derrubaria o jogo).
 */
public final class LosGearPlusMixinPlugin implements IMixinConfigPlugin {
	private boolean intermediary = true;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			String ns = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
			intermediary = "intermediary".equals(ns);
		} catch (Throwable t) {
			intermediary = true; // sem resolvedor: assume o jogo normal
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".DaotOdmHarnessIntermediaryMixin")) return intermediary;
		if (mixinClassName.endsWith(".DaotOdmHarnessNamedMixin")) return !intermediary;
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

package com.example.losgearplus.mixin;

import com.example.losgearplus.codex.CodexServer;
import com.example.losgearplus.codex.stacks.StackData;
import com.example.losgearplus.codex.stacks.StackSource;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armor Potion do DAOT + Iron Skin (Pele de Ferro):
 * <ul>
 *   <li>Quem o DAOT já deixa beber (tags female/attack/jaw): bebe normal (Hardening do DAOT) e GANHA a Iron Skin.</li>
 *   <li>QUALQUER OUTRO shifter (Armored, Colossal, Beast, Warhammer, Cart, Ogre...) agora também pode beber: ganha só a
 *       Iron Skin (sem a tag {@code has_hardening}, que é do Hardening do DAOT), e a poção é consumida.</li>
 * </ul>
 * A Iron Skin é a única skill do stack do Ogre, então o prêmio é o stack do Ogre nível I. Se o jogador já tem esse
 * stack, os outros shifters continuam sem poder beber (a recusa original do DAOT). {@code require = 0}: se o DAOT mudar
 * os métodos, nada quebra, só deixa de valer.
 */
@Mixin(targets = "daot.ArmorPotionItem", remap = false)
public abstract class DaotArmorPotionIronSkinMixin {
	/** Quem o DAOT já deixa beber a poção (a checagem original dele). */
	private static boolean los_gear_plus$daotEligible(Player p) {
		return p.getTags().contains("female") || p.getTags().contains("attack") || p.getTags().contains("jaw");
	}

	/** Outro shifter qualquer que ainda não tem a Iron Skin: passa a poder beber. */
	private static boolean los_gear_plus$extraDrinker(Player p) {
		return !los_gear_plus$daotEligible(p) && StackSource.isShifter(p)
				&& p instanceof ServerPlayer sp && StackData.level(sp, StackSource.OGRE) == 0;
	}

	/** Ao USAR a poção (servidor): a checagem "pode usar Hardening?" passa também para os outros shifters. */
	@ModifyExpressionValue(method = {"use", "method_7836"},
			at = @At(value = "INVOKE", target = "Ldaot/ArmorPotionItem;canUseHardening"), remap = false, require = 0)
	private boolean los_gear_plus$anyShifterMayDrink(boolean original, @Local(argsOnly = true) Player player) {
		return original || los_gear_plus$extraDrinker(player);
	}

	/** Ao TERMINAR de beber (servidor): entrega a Iron Skin. */
	@Inject(method = {"finishUsingItem", "method_7861"}, at = @At("TAIL"), remap = false, require = 0)
	private void los_gear_plus$grantIronSkin(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
		if (StackData.level(player, StackSource.OGRE) > 0) return; // já tem a Pele de Ferro

		boolean ok;
		if (los_gear_plus$daotEligible(player)) {
			ok = player.getTags().contains("has_hardening"); // o DAOT já tratou a bebida; só vale se deu certo
		} else {
			ok = StackSource.isShifter(player);
			if (ok && !player.getAbilities().instabuild) stack.shrink(1); // o DAOT não consome para esses; consumimos aqui
		}
		if (!ok) return;

		StackData.set(player, StackSource.OGRE, 1);
		CodexServer.sync(player, false);
		player.displayClientMessage(Component.translatable("los_gear_plus.codex.potion.iron_skin"), false);
	}
}
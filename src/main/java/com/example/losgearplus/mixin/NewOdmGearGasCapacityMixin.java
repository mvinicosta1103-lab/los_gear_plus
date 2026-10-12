package com.example.losgearplus.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raises only the Los Gear New ODM Gear tank capacity to 1000. */
@Mixin(targets = "cn.blockforge.royalattire.item.OdmGearItem", remap = false)
public abstract class NewOdmGearGasCapacityMixin {
    @Inject(method = "maxGas", at = @At("HEAD"), cancellable = true, remap = false)
    private void losgearplus$increaseNewGearCapacity(CallbackInfoReturnable<Integer> cir) {
        if (losgearplus$isNewOdmGear((Item) (Object) this)) {
            cir.setReturnValue(1000);
        }
    }

    @Inject(method = "maxGasOf", at = @At("HEAD"), cancellable = true, remap = false)
    private static void losgearplus$increaseNewGearStackCapacity(ItemStack stack,
                                                                 CallbackInfoReturnable<Integer> cir) {
        if (stack != null && !stack.isEmpty() && losgearplus$isNewOdmGear(stack.getItem())) {
            cir.setReturnValue(1000);
        }
    }

    private static boolean losgearplus$isNewOdmGear(Item item) {
        return "los_gear:new_odm_gear".equals(BuiltInRegistries.ITEM.getKey(item).toString());
    }
}

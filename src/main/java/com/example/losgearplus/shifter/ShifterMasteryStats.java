package com.example.losgearplus.shifter;

import daot.ShifterTitan;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Mastery bonuses (level 0..9):
 * <pre>
 *  Human form : base 40 HP (20 hearts) + HP_PER_LEVEL per level
 *  Titan form : HP x(1 + TITAN_HP_PER_LEVEL*level), already compensating the damage reduction (effective HP)
 *  Strength   : damage dealt x(1 + STRENGTH_PER_LEVEL*level), human and titan (via LivingEntityMasteryMixin)
 *  Reduction  : human damage taken -REDUCTION_PER_LEVEL*level (via LivingEntityMasteryMixin)
 * </pre>
 * Tune the constants below to balance.
 */
public final class ShifterMasteryStats {
    public static final double BASE_HP_BONUS = 20.0;        // 20 + 20 = 40 HP
    public static final double HP_PER_LEVEL = 4.0;          // level 9 = 76 HP
    public static final double TITAN_HP_PER_LEVEL = 0.10;   // level 9 = +90% titan HP
    public static final float STRENGTH_PER_LEVEL = 0.08f;   // level 9 = +72% damage
    public static final float REDUCTION_PER_LEVEL = 0.05f;  // level 9 = 45% less damage

    private static final ResourceLocation HP_ID =
            ResourceLocation.fromNamespaceAndPath("los_gear_plus", "mastery_hp");

    private ShifterMasteryStats() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) apply(p);
        });
    }

    /** Damage reduction (0..1) for a shifter in human form. */
    public static float damageReduction(ServerPlayer p) {
        if (!ShifterTypes.isShifter(p)) return 0f;
        return ShifterMastery.getLevel(p.getUUID()) * REDUCTION_PER_LEVEL;
    }

    /** Damage multiplier for attacks made by a shifter (or by his titan). */
    public static float strengthMultiplier(UUID shifter) {
        if (shifter == null) return 1f;
        return 1f + ShifterMastery.getLevel(shifter) * STRENGTH_PER_LEVEL;
    }

    public static void apply(ServerPlayer p) {
        int level = ShifterMastery.getLevel(p.getUUID());

        // Human form
        if (ShifterTypes.isShifter(p)) {
            setModifier(p, Attributes.MAX_HEALTH,
                    BASE_HP_BONUS + HP_PER_LEVEL * level, AttributeModifier.Operation.ADD_VALUE);
        } else {
            removeModifier(p, Attributes.MAX_HEALTH);
        }

        // Titan form (the entity the player is riding)
        if (p.getVehicle() instanceof LivingEntity titan && titan instanceof ShifterTitan) {
            double reduction = level * REDUCTION_PER_LEVEL;
            double factor = (1.0 + TITAN_HP_PER_LEVEL * level) / (1.0 - reduction);
            setModifier(titan, Attributes.MAX_HEALTH,
                    factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    private static void setModifier(LivingEntity e, Holder<Attribute> attr, double amount,
                                    AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(HP_ID);
        if (cur != null && cur.amount() == amount && cur.operation() == op) return;
        float before = e.getMaxHealth();
        inst.addOrUpdateTransientModifier(new AttributeModifier(HP_ID, amount, op));
        float after = e.getMaxHealth();
        if (after > before) e.heal(after - before);
    }

    private static void removeModifier(LivingEntity e, Holder<Attribute> attr) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(HP_ID);
    }
}
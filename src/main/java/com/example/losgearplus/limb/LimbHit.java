package com.example.losgearplus.limb;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Descobre ONDE um golpe atingiu um titã e qual parte do corpo fica ali (só servidor).
 *
 * <p>O ponto do golpe vem da origem do dano: projétil/explosão = a posição dele; golpe de quem tem olhos (jogador,
 * mob) = onde o olhar cruza a caixa de colisão do titã; golpe de titã contra titã = o ponto da caixa mais perto do
 * meio do corpo do atacante. A altura relativa e o lado (esquerdo/direito do PRÓPRIO titã, pela direção do corpo)
 * escolhem a parte; as faixas ficam em {@link LimbRules} ({@code HIT_*}).
 */
public final class LimbHit {
    private LimbHit() {}

    /**
     * A parte atingida, ou null se o golpe pegou o tronco/cabeça/uma parte já perdida (ou ainda crescendo).
     *
     * @param titanAttacker o atacante é um titã (usa o centro do corpo dele, não o olhar)
     * @param eyes          olhos podem ser decepados (golpe de lâmina)
     */
    public static LimbPart partHit(LivingEntity victim, DamageSource src, boolean titanAttacker, boolean eyes, LimbState st) {
        AABB box = victim.getBoundingBox();
        double height = box.getYsize();
        if (height <= 0.01) return null;
        Vec3 p = clampInto(box, hitPoint(victim, src, box, titanAttacker));

        float h = Mth.clamp((float) ((p.y - box.minY) / height), 0f, 1f);

        // esquerda/direita e frente/trás do PRÓPRIO titã (yaw 0 = olhando para +Z; a esquerda dele é +X)
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 c = box.getCenter();
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        double half = Math.max(0.01, victim.getBbWidth() * 0.5);
        float side = Mth.clamp((float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / half), -1f, 1f);
        boolean front = dx * -Math.sin(yaw) + dz * Math.cos(yaw) > 0;
        boolean left = (side >= 0f) != LimbRules.HIT_SWAP_SIDES;

        LimbPart part;
        if (h >= LimbRules.HIT_HEAD_MIN) {
            if (!eyes || !front) return null;
            part = left ? LimbPart.EYE_LEFT : LimbPart.EYE_RIGHT;
        } else if (h >= LimbRules.HIT_ARM_MIN) {
            if (Math.abs(side) < LimbRules.HIT_ARM_SIDE_MIN) return null; // tronco
            part = LimbPart.of(LimbPart.Kind.ARM, left, h >= LimbRules.HIT_ARM_UPPER_MIN);
        } else {
            part = LimbPart.of(LimbPart.Kind.LEG, left, h >= LimbRules.HIT_LEG_UPPER_MIN);
        }
        return st.isIntact(part) ? part : null;
    }

    private static Vec3 hitPoint(LivingEntity victim, DamageSource src, AABB box, boolean titanAttacker) {
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();

        // projétil / explosão: onde ele estava
        Vec3 at = src.getSourcePosition();
        if (at != null && (direct == null || direct != attacker)) return at;

        if (attacker == null) return box.getCenter();

        // titã contra titã: o ponto da caixa mais perto do meio do corpo do atacante
        if (titanAttacker) return attacker.getBoundingBox().getCenter();

        // golpe corpo a corpo: onde o olhar do atacante cruza a caixa do titã
        Vec3 eye = attacker.getEyePosition();
        double reach = attacker.distanceTo(victim) + victim.getBbWidth() + attacker.getBbWidth() + 2.0;
        Vec3 end = eye.add(attacker.getViewVector(1.0f).scale(reach));
        return box.clip(eye, end).orElse(eye);
    }

    private static Vec3 clampInto(AABB b, Vec3 v) {
        return new Vec3(
                Mth.clamp(v.x, b.minX, b.maxX),
                Mth.clamp(v.y, b.minY, b.maxY),
                Mth.clamp(v.z, b.minZ, b.maxZ));
    }
}
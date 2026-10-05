package com.example.losgearplus.client.grip;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reproduz como o DAOT 2.5.0 move as caixas (tanques) do ODM Gear, para os grips com lâmina
 * acompanharem a caixa em vez de ficarem presos 100% à perna.
 *
 * No DAOT 2.5.0 (daot.ODMHarnessArmorRenderer.blendTankLeg) a caixa NÃO segue mais só a perna:
 *   transformação da caixa = lerp(legWeight, transformação do TORSO, transformação da PERNA)
 *   legWeight = 0.1 + 0.9 * blend      (0.1 = WALK_LEG_INFLUENCE)
 *   blend     = 1 quando o jogador está montado OU em animação de ODM (ODMAnimationHandler.isOdmAnimating),
 *               0 caso contrário; suavizado com taxa 8/s.
 * Ou seja: andando/parado a caixa quase só acompanha o torso (90%); usando ODM ela volta a seguir a perna.
 *
 * Aqui: applyHipFrame() monta o mesmo "quadro do quadril" misturado (posição e rotação), e o layer
 * aplica os offsets gx/gy/gz em cima dele. Com weight = 1 o resultado é idêntico ao antigo
 * leg.translateAndRotate(), então a calibração /gripholster continua valendo para o ODM em uso.
 *
 * As constantes abaixo são cópias das privadas do DAOT; se o DAOT mudar, atualize aqui.
 */
public final class OdmTankFollow {
    private OdmTankFollow() {}

    /** = ODMHarnessArmorRenderer.WALK_LEG_INFLUENCE */
    private static final float WALK_LEG_INFLUENCE = 0.1f;
    /** = taxa de suavização do blend no DAOT (por segundo) */
    private static final float BLEND_RATE = 8.0f;

    private static final MethodHandle IS_ODM_ANIMATING = findIsOdmAnimating();
    private static final Map<Integer, State> STATES = new HashMap<>();

    private static final class State {
        float toLegs;
        long lastNanos;
    }

    private static MethodHandle findIsOdmAnimating() {
        try {
            Class<?> cls = Class.forName("daot.ODMAnimationHandler");
            Method m = cls.getMethod("isOdmAnimating", UUID.class);
            return MethodHandles.publicLookup().unreflect(m);
        } catch (Throwable t) {
            return null; // DAOT < 2.5.0 ou mudou: cai no comportamento "só montado"
        }
    }

    public static boolean isOdmAnimating(UUID id) {
        if (IS_ODM_ANIMATING == null) return false;
        try {
            return (boolean) IS_ODM_ANIMATING.invoke(id);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Peso da perna (0.1..1.0) igual ao que o DAOT usa na caixa deste jogador neste frame. */
    public static float legWeight(AbstractClientPlayer player) {
        boolean onLegs = player.isPassenger() || isOdmAnimating(player.getUUID());
        long now = System.nanoTime();
        State s = STATES.get(player.getId());
        if (s == null) {
            if (STATES.size() > 256) STATES.clear();
            s = new State();
            s.toLegs = onLegs ? 1.0f : 0.0f;
            s.lastNanos = now;
            STATES.put(player.getId(), s);
        } else {
            float dt = Math.min(0.25f, (now - s.lastNanos) / 1.0E9f);
            s.lastNanos = now;
            float step = 1.0f - (float) Math.exp(-BLEND_RATE * dt);
            s.toLegs += ((onLegs ? 1.0f : 0.0f) - s.toLegs) * step;
        }
        return WALK_LEG_INFLUENCE + (1.0f - WALK_LEG_INFLUENCE) * s.toLegs;
    }

    /**
     * Aplica ao PoseStack o quadro do quadril da perna {@code leg} misturado com o do torso.
     * weight = 1 -> igual a leg.translateAndRotate; weight = 0 -> quadril preso rigidamente ao torso.
     */
    public static void applyHipFrame(PoseStack pose, ModelPart body, ModelPart leg, float weight) {
        // Onde o quadril (posição de repouso da perna) vai parar se for carregado só pelo torso.
        PartPose bodyInit = body.getInitialPose();
        PartPose legInit = leg.getInitialPose();
        Vector3f hipLocal = new Vector3f(legInit.x - bodyInit.x, legInit.y - bodyInit.y, legInit.z - bodyInit.z);
        Vector3f pBody = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot).transform(hipLocal);
        pBody.add(body.x, body.y, body.z);

        float px = Mth.lerp(weight, pBody.x, leg.x);
        float py = Mth.lerp(weight, pBody.y, leg.y);
        float pz = Mth.lerp(weight, pBody.z, leg.z);
        float rx = Mth.lerp(weight, body.xRot, leg.xRot);
        float ry = Mth.lerp(weight, body.yRot, leg.yRot);
        float rz = Mth.lerp(weight, body.zRot, leg.zRot);

        pose.translate(px / 16.0f, py / 16.0f, pz / 16.0f);
        if (rx != 0.0f || ry != 0.0f || rz != 0.0f) {
            pose.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
        }
    }
}
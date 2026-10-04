package com.example.losgearplus.client.hange;

import com.example.losgearplus.LosGearPlus;
import com.example.losgearplus.compat.LosGearCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Toca um SFX quando os óculos da Hange (los_gear) avistam titan(s) — junto com o brilho que o los_gear já aplica.
 *
 * Usa o mesmo critério do los_gear: mesmo alcance (48 no óculos novo, 32 no velho), mesma caixa de busca e o mesmo
 * isTitan. Uma horda avistada de uma vez toca UMA vez só; um titan só dispara de novo depois de ficar
 * {@link #FORGET_AFTER_TICKS} sem ser visto. Tudo é do lado do cliente (funciona em servidor sem o addon).
 */
public final class HangeTitanAlert {
    public static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(LosGearPlus.id("hange_titan_spotted"));

    /** De quanto em quanto tempo varre (10 ticks = 0,5 s). */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Quanto tempo um titan precisa ficar fora do alcance para voltar a contar como "recém-avistado" (5 s). */
    private static final int FORGET_AFTER_TICKS = 100;
    /** Intervalo mínimo entre dois toques do SFX (3 s). */
    private static final int COOLDOWN_TICKS = 60;

    private static final boolean LOS_GEAR_LOADED = FabricLoader.getInstance().isModLoaded("los_gear");

    /** id da entidade -> tick (do nosso contador) em que foi vista pela última vez. */
    private static final Map<Integer, Long> seen = new HashMap<>();
    private static long now = 0;
    private static long lastPlayed = -COOLDOWN_TICKS;

    private HangeTitanAlert() {}

    public static void init() {
        if (!LOS_GEAR_LOADED) return;
        ClientTickEvents.END_CLIENT_TICK.register(HangeTitanAlert::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void playTest() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SOUND, 1.0F, 1.0F));
    }

    private static void reset() {
        seen.clear();
        lastPlayed = now - COOLDOWN_TICKS;
    }

    private static void tick(Minecraft mc) {
        now++;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            if (!seen.isEmpty()) reset();
            return;
        }
        if (now % SCAN_INTERVAL_TICKS != 0) return;

        double range = LosGearCompat.hangeScanRange(player);
        if (range <= 0) {
            // Sem óculos: esquece tudo, assim ao colocá-los com titans por perto o aviso toca.
            seen.clear();
            return;
        }

        AABB box = player.getBoundingBox().inflate(range);
        Entity vehicle = player.getVehicle();
        List<Entity> titans = level.getEntitiesOfClass(Entity.class, box,
                e -> e != player && e != vehicle && e.isAlive() && LosGearCompat.isTitan(e));

        // Primeiro esquece quem sumiu há tempo, depois marca quem está à vista agora.
        seen.values().removeIf(last -> now - last > FORGET_AFTER_TICKS);
        boolean fresh = false;
        for (Entity titan : titans) {
            if (seen.put(titan.getId(), now) == null) fresh = true;
        }

        // O rastreamento roda sempre; só o toque depende de estar ligado (assim, ao ligar de novo
        // com titans já à vista, não dispara na hora).
        if (fresh && HangeSfxConfig.isEnabled() && now - lastPlayed >= COOLDOWN_TICKS) {
            lastPlayed = now;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SOUND, 1.0F, 1.0F));
        }
    }
}

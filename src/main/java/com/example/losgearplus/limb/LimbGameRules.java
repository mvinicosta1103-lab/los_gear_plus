package com.example.losgearplus.limb;

import com.example.losgearplus.shifter.ShifterMastery;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

/**
 * Gamerules da regeneração passiva de membros do titã de shifter.
 *
 * <pre>
 *   /gamerule losGearPlusPassiveRegen true|false        liga/desliga a regeneração passiva (padrão: true)
 *   /gamerule losGearPlusPassiveRegenMastery 0..9        maestria mínima para a passiva começar (padrão: 5)
 * </pre>
 *
 * Com a passiva desligada, ou abaixo da maestria mínima, o membro perdido NÃO volta (nem começa a crescer, só
 * solta fumaça) até o jogador ligar o Steam Heal (J), na forma humana ou de titã. A passiva em si só existe na forma de titã. O Steam Heal funciona em qualquer maestria
 * e, a partir da maestria mínima, acelera a passiva.
 */
public final class LimbGameRules {
    private LimbGameRules() {}

    public static final String PASSIVE_REGEN_NAME = "losGearPlusPassiveRegen";
    public static final String PASSIVE_REGEN_MASTERY_NAME = "losGearPlusPassiveRegenMastery";

    /** Maestria mínima padrão para a regeneração passiva. */
    public static final int DEFAULT_PASSIVE_MASTERY = 5;

    /** Liga/desliga a regeneração passiva de membros (Steam Heal continua funcionando). */
    public static final GameRules.Key<GameRules.BooleanValue> PASSIVE_REGEN =
            GameRuleRegistry.register(PASSIVE_REGEN_NAME, GameRules.Category.PLAYER,
                    GameRuleFactory.createBooleanRule(true));

    /** Maestria mínima (0..9) para a passiva. 0 = vale para todos. */
    public static final GameRules.Key<GameRules.IntegerValue> PASSIVE_REGEN_MASTERY =
            GameRuleRegistry.register(PASSIVE_REGEN_MASTERY_NAME, GameRules.Category.PLAYER,
                    GameRuleFactory.createIntRule(DEFAULT_PASSIVE_MASTERY, 0, ShifterMastery.MAX_LEVEL));

    /** Força o carregamento da classe (registra as gamerules). Chame uma vez no init. */
    public static void init() {}
}
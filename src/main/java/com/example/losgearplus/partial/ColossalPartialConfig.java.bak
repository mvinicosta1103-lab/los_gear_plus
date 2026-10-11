package com.example.losgearplus.partial;

/**
 * Valores do Colossal parcial. Mexa só aqui.
 *
 * <p>Unidades: blocos (o modelo está em 1/16 de bloco: 16 unidades do Blockbench = 1 bloco). O torso do modelo tem
 * ~38 blocos de altura e ~21 de largura (com os braços abertos), por isso tudo aqui é bem maior que nos outros parciais.
 */
public final class ColossalPartialConfig {
    private ColossalPartialConfig() {}

    // ---- tamanho / posição ------------------------------------------------------------------------------
    /** Hitbox sólida. A altura é do torso acima do chão (o modelo tem ~11 blocos de costelas abaixo do chão). */
    public static final float WIDTH = 14.0f;
    public static final float HEIGHT = 30.0f;
    public static final float SHADOW_RADIUS = 12.0f;

    /** Subida do chão: duração e quantos blocos o modelo começa enterrado. */
    public static final int RISE_TICKS = 60;
    public static final float RISE_DEPTH = 32.0f;

    /** Onde o jogador fica dentro do titã (peito) e quando emerge pela nuca (osso nape_hitbox: y~22,5 / z~+1,6 do modelo). */
    public static final double SEAT_Y = 16.0;
    public static final double SEAT_FORWARD = 0.0;
    public static final double EMERGE_Y = 23.0;
    public static final double EMERGE_FORWARD = -1.6;

    /** Saída total / queda: distância (para trás) onde o jogador é colocado. Maior que nos outros, o titã é enorme. */
    public static final double GROUND_EXIT_BACK = 16.0;

    // ---- ataque (botão de ataque do mouse enquanto montado, não emergido) -------------------------------
    /** Alcance do golpe à frente. O do DAOT (50) é para o titã de 60 blocos inteiro; o torso parcial usa menos. */
    public static final double ATTACK_RANGE = 28.0;
    /** Dano = colossalTitanAttackDamage (config do DAOT) x este fator. */
    public static final double ATTACK_DAMAGE_FACTOR = 0.6;
    /** attack_1 dura 2,75 s e attack_2 3,0 s; o golpe acerta no tick indicado (contado desde o início da animação). */
    public static final int ATTACK_1_TICKS = 55;
    public static final int ATTACK_1_HIT_TICK = 44;
    public static final int ATTACK_2_TICKS = 60;
    public static final int ATTACK_2_HIT_TICK = 50;
    /** Folga depois da animação antes de poder atacar de novo. */
    public static final int ATTACK_EXTRA_COOLDOWN = 8;

    // ---- habilidades do Colossal (mesmas teclas 1 e 4 do DAOT) ------------------------------------------
    /** Habilidade 1: vapor (empurra todo mundo em volta). */
    public static final int STEAM_TICKS = 100;
    public static final int STEAM_EFFECT_START_TICK = 20;
    public static final double STEAM_RADIUS = 45.0;
    public static final int STEAM_COOLDOWN = 400;
    /** Habilidade 4: calor infernal (vapor com fogo: incendeia quem está no raio). */
    public static final int INFERNAL_TICKS = 160;
    public static final int INFERNAL_COOLDOWN = 700;
    public static final int INFERNAL_FIRE_TICKS = 80;

    // ---- queda (titã derrotado ou shifter saiu do partial) ----------------------------------------------
    /** Duração da animação fall (11,5 s). Ao fim, o corpo passa a evaporar (EvaporationRules). */
    public static final int FALL_TOTAL_TICKS = 230;
    /** Tick da animação em que o corpo bate no chão (a queda forte do body acontece de 2,0 s a 3,08 s). */
    public static final int FALL_IMPACT_TICK = 62;
    public static final double FALL_RADIUS = 45.0;
    public static final float FALL_DAMAGE = 80.0f;
    public static final double FALL_KNOCKBACK = 3.0;
    /** Quem está a menos de (raio x este fator) do impacto também pega fogo. */
    public static final double FALL_FIRE_RADIUS_FACTOR = 0.6;
    public static final int FALL_FIRE_TICKS = 100;
    /** Tentativas de acender fogo no chão em volta do impacto (respeita a gamerule mobGriefing). */
    public static final int FALL_FIRE_PATCHES = 70;
    /** true = o impacto também explode blocos (usa mobGriefing). Padrão false: só fogo, sem destruir o terreno. */
    public static final boolean FALL_BREAKS_BLOCKS = false;
    public static final float FALL_EXPLOSION_POWER = 8.0f;

    /**
     * Ajuste vertical do modelo durante a queda, em UNIDADES do Blockbench (16 = 1 bloco; positivo sobe).
     * A animação fall que você mandou levanta o body 926 unidades (~58 blocos) desde o primeiro frame. Se na prática
     * o torso aparecer flutuando alto demais, ponha aqui -926 (ou o valor que ficar certo).
     */
    public static final float FALL_RENDER_Y_OFFSET_UNITS = 0.0f;
}

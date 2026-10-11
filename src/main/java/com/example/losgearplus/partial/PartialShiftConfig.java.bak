package com.example.losgearplus.partial;

/**
 * Valores de balanceamento do Partial Shifting. Mexa só aqui.
 * (Se quiser, depois dá para ligar isto a gamerules, como o sistema de membros faz.)
 */
public final class PartialShiftConfig {
    private PartialShiftConfig() {}

    /** Fração da vida do titã COMPLETO (config do DAOT) que o titã parcial tem. */
    public static final double HEALTH_FRACTION = 0.5;

    /** Ticks da animação de "subir do chão" (20 ticks = 1 s). O fim é a evaporação padrão (EvaporationRules). */
    public static final int RISE_TICKS = 30;
    /** Quantos blocos o modelo fica enterrado no começo da subida. */
    public static final float RISE_DEPTH = 4.6f;

    /** Saída total: distância (para trás) do chão onde o jogador é colocado. */
    public static final double GROUND_EXIT_BACK = 2.8;

    /** Emergido pela nuca: altura e distância à frente do centro onde o jogador fica (nuca = costas da cabeça). */
    public static final double EMERGE_Y = 2.9;
    public static final double EMERGE_FORWARD = -0.2;
    /** Ticks entre alternar dentro/emergido (20 como o DAOT). */
    public static final int EMERGE_COOLDOWN_TICKS = 20;

    /** Recarga após transformar (600 = 30 s). Criativo ignora. */
    public static final int COOLDOWN_TICKS = 600;

    /** Hitbox (sólida: bloqueia golpes, projéteis e passagem). */
    public static final float WIDTH = 3.6f;
    public static final float HEIGHT = 4.4f;

    /** Onde o jogador fica (visão em primeira pessoa): altura e distância à frente do centro. Ajuste olhando o jogo. */
    public static final double SEAT_Y = 2.0;
    public static final double SEAT_FORWARD = 0.7;

    /** "Dano de transformação": explosão ao surgir (o do Attack Titan no DAOT é raio 10 / dano 20 / empurrão 2). */
    public static final double EXPLOSION_RADIUS = 7.0;
    public static final float EXPLOSION_DAMAGE = 16.0f;
    public static final double EXPLOSION_KNOCKBACK = 1.5;

    /** Dano de contato em mobs hostis colados no titã (defesa de quem está perto). */
    public static final float CONTACT_DAMAGE = 6.0f;
    public static final int CONTACT_INTERVAL_TICKS = 10;
    public static final double CONTACT_REACH = 1.2;

    /** Regeneração passiva da vida do titã parcial (o DAOT usa 0.05/tick nos titãs dele). */
    public static final float PASSIVE_HEAL_PER_TICK = 0.05f;

    /** Distância em que a barra de vida aparece para outros jogadores. */
    public static final double BOSSBAR_RANGE = 128.0;

    // =====================================================================================================
    // Colossal Titan parcial (modelo grande, ataca, usa habilidades e desaba ao sair/ser derrotado)
    // =====================================================================================================

    /** Hitbox: o modelo tem ~27 blocos de altura e o torso ~15 de largura (os braços passam disso). */
    public static final float COLOSSAL_WIDTH = 14.0f;
    public static final float COLOSSAL_HEIGHT = 26.0f;

    public static final int COLOSSAL_RISE_TICKS = 60;
    public static final float COLOSSAL_RISE_DEPTH = 28.0f;
    /** Saída total: o corpo cai de lado (até ~15 blocos), então o dono é colocado bem atrás. */
    public static final double COLOSSAL_GROUND_EXIT_BACK = 12.0;

    /** Assento (dentro da cabeça/torso) e nuca (do bone nape_hitbox do modelo: ~22,5 blocos, ~1,6 atrás do centro). */
    public static final double COLOSSAL_SEAT_Y = 22.0;
    public static final double COLOSSAL_SEAT_FORWARD = 0.5;
    public static final double COLOSSAL_EMERGE_Y = 23.5;
    public static final double COLOSSAL_EMERGE_FORWARD = -1.6;

    /** Ataques (animações attack_1 / attack_2): duração e tick do golpe, medidos nos keyframes das animações. */
    public static final int COLOSSAL_ATTACK1_TICKS = 55;   // 2.75 s
    public static final int COLOSSAL_ATTACK1_IMPACT = 44;  // ~2.2 s: varredura do braço esquerdo
    public static final int COLOSSAL_ATTACK2_TICKS = 60;   // 3.0 s
    public static final int COLOSSAL_ATTACK2_IMPACT = 52;  // ~2.6 s: golpe do braço direito
    public static final int COLOSSAL_ATTACK_EXTRA_COOLDOWN = 5;
    /** Alcance horizontal do golpe (o DAOT usa 50 no titã completo de 60 blocos). */
    public static final double COLOSSAL_ATTACK_RANGE = 26.0;
    /** Multiplicador sobre colossalTitanAttackDamage do DAOT (padrão 30). */
    public static final float COLOSSAL_ATTACK_DAMAGE_SCALE = 1.0f;
    public static final double COLOSSAL_ATTACK_KNOCKBACK = 3.0;
    public static final double COLOSSAL_ATTACK_KNOCKBACK_UP = 1.2;

    /** Habilidade 1 (vapor). */
    public static final int COLOSSAL_STEAM_TICKS = 160;
    public static final int COLOSSAL_STEAM_WARMUP = 20;
    public static final double COLOSSAL_STEAM_RADIUS = 45.0;
    public static final int COLOSSAL_STEAM_COOLDOWN = 500;
    /** Habilidade 4 (calor infernal): liga/desliga, com duração máxima e recarga (parcial não gasta stamina). */
    public static final int COLOSSAL_INFERNAL_MAX_TICKS = 300;
    public static final int COLOSSAL_INFERNAL_WARMUP = 30;
    public static final double COLOSSAL_INFERNAL_RADIUS = 40.0;
    public static final int COLOSSAL_INFERNAL_COOLDOWN = 400;

    /**
     * Queda (animation "fall", 11.5 s). A animação foi feita com o modelo ~926 unidades (57,875 blocos) acima da
     * pose de repouso; o renderer subtrai isso na fase de queda. Ponha 0 se corrigir isso no Blockbench.
     */
    public static final double COLOSSAL_FALL_BASE_LIFT_BLOCKS = 926.0 / 16.0;
    /** Tick da animação em que o corpo bate no chão (~10 s: rotação z chega a 90°). */
    public static final int COLOSSAL_FALL_IMPACT_TICK = 200;
    public static final int COLOSSAL_FALL_TOTAL_TICKS = 232;
    public static final double COLOSSAL_FALL_RADIUS = 38.0;
    public static final float COLOSSAL_FALL_DAMAGE = 45.0f;
    public static final double COLOSSAL_FALL_KNOCKBACK = 2.5;

    /** Câmera em 3ª pessoa montado no Colossal parcial: distância total atrás (blocos; o vanilla usa 4) e altura extra. */
    public static final float COLOSSAL_CAMERA_DISTANCE = 42.0f;
    public static final float COLOSSAL_CAMERA_UP = 5.0f;
}

package com.example.losgearplus.partial;

/**
 * Valores de balanceamento do Partial Shifting. Mexa só aqui.
 * (Se quiser, depois dá para ligar isto a gamerules, como o sistema de membros faz.)
 */
public final class PartialShiftConfig {
    private PartialShiftConfig() {}

    /** Fração da vida do titã COMPLETO (config do DAOT) que o titã parcial tem. */
    public static final double HEALTH_FRACTION = 0.5;

    /** Ticks da animação de "subir do chão" e de "afundar" (20 ticks = 1 s). */
    public static final int RISE_TICKS = 30;
    public static final int SINK_TICKS = 30;
    /** Quantos blocos o modelo fica enterrado no começo da subida / no fim do afundar. */
    public static final float RISE_DEPTH = 4.6f;

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
}

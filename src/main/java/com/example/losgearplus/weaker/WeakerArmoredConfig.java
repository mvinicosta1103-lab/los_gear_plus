package com.example.losgearplus.weaker;

/** Números do Armored Titan "Weaker". Ajuste aqui para balancear. */
public final class WeakerArmoredConfig {
    private WeakerArmoredConfig() {}

    /** Tag do DAOT que identifica o shifter do Armored. */
    public static final String ARMORED_TAG = "armored";
    /** Tag (de jogador) que guarda a escolha "Weaker". Sem ela vale o Armored normal. */
    public static final String WEAKER_TAG = "los_gear_plus_armored_weaker";

    /** Vida do Weaker = fração da vida configurada no DAOT para o Armored. */
    public static double HEALTH_FRACTION = 0.6;
    /** Fração do dreno de stamina que ainda é cobrada no Weaker (0.5 = metade). */
    public static float STAMINA_DRAIN_FACTOR = 0.5f;
    /** Fração do custo de transformação que ainda é cobrada no Weaker. */
    public static float SHIFT_COST_FACTOR = 0.5f;

    /** Corte de tendão (golpe na perna): dano = fração da vida máxima, nunca mata. */
    public static float TENDON_DAMAGE_FRACTION = 0.04f;
    public static int TENDON_COOLDOWN_TICKS = 10;
    /** Duração do "manco": sem correr e mais lento. */
    public static int CRIPPLE_TICKS = 100;
    /** Modificador multiplicativo de velocidade durante o manco (-0.4 = 40% mais lento). */
    public static double CRIPPLE_SPEED = -0.4;
}

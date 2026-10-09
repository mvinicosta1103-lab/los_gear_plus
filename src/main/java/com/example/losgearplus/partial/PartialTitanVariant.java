package com.example.losgearplus.partial;

import daot.ModConfig;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/**
 * Qual titã parcial cada shifter recebe. A ordem das constantes é a prioridade (a mesma do DAOT quando o jogador
 * tem mais de uma tag): o primeiro cuja tag o jogador tiver vence.
 *
 * <p>Hoje só existe o modelo parcial do Attack Titan, então todas as variantes usam o mesmo "partial_titan_attack". Quando tiver
 * outros modelos, crie {@code geo/<nome>.geo.json} + {@code textures/entity/<nome>.png} e troque o nome da variante.
 */
public enum PartialTitanVariant {
    JAW("jaw", "jawTitanHealth", "partial_titan_attack", 0, "jaw"),
    CART("cart_shifter", "cartTitanHealth", "partial_titan_attack", 0, "jaw"),
    OGRE("ogre_shifter", null, "partial_titan_attack", 1, "attack"),
    FOUNDER("founder", null, "partial_titan_attack", 1, "attack"),
    TRIPLE_T("triple_t", null, "partial_titan_attack", 1, "attack"),
    ATTACK("attack", "attackTitanHealth", "partial_titan_attack", 1, "attack"),
    FEMALE("female", "femaleTitanHealth", "partial_titan_attack", 3, "female"),
    ARMORED("armored", "armoredTitanHealth", "partial_titan_attack", 2, "armored"),
    BEAST("beast", "beastTitanHealth", "partial_titan_attack", 4, "beast"),
    WARHAMMER("warhammer", "warhammerTitanHealth", "partial_titan_attack", 5, "warhammer"),
    COLOSSAL("colossal", "colossalTitanHealth", "partial_titan_attack", 6, "colossal");

    private static final double DEFAULT_FULL_HEALTH = 400.0;

    private final String tag;
    private final String healthField;
    private final String modelName;
    private final int spawnType;
    private final String markType;

    PartialTitanVariant(String tag, String healthField, String modelName, int spawnType, String markType) {
        this.tag = tag;
        this.healthField = healthField;
        this.modelName = modelName;
        this.spawnType = spawnType;
        this.markType = markType;
    }

    /** Tipo enviado no TitanSpawnPayload do DAOT (define som/partículas da transformação). Igual ao do titã completo. */
    public int spawnType() { return spawnType; }

    /** Tipo da shifter mark (ShifterMarkTracker) que o titã completo deste shifter daria; null = sem marca. */
    public String markType() { return markType; }

    public String tag() { return tag; }

    /** Id estável (usado na sincronização cliente/servidor). */
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public String modelName() { return modelName; }

    /** Chave de tradução do nome do titã. */
    public String nameKey() { return "los_gear_plus.partial.titan." + id(); }

    /** Vida do titã parcial: fração da vida configurada no DAOT para o titã completo. */
    public double partialMaxHealth() {
        double full = DEFAULT_FULL_HEALTH;
        if (healthField != null) {
            try {
                Object cfg = ModConfig.get();
                full = ((Number) cfg.getClass().getField(healthField).get(cfg)).doubleValue();
            } catch (Throwable ignored) {
                // campo ausente/renomeado numa versão do DAOT: usa o padrão
            }
        }
        return Math.max(20.0, full * PartialShiftConfig.HEALTH_FRACTION);
    }

    public static PartialTitanVariant byId(String id) {
        for (PartialTitanVariant v : values()) {
            if (v.id().equals(id)) return v;
        }
        return ATTACK;
    }

    /** Variante do jogador (shifter). Tag de addon desconhecida cai no Attack. */
    public static PartialTitanVariant of(ServerPlayer player) {
        Set<String> tags = player.getTags();
        for (PartialTitanVariant v : values()) {
            if (tags.contains(v.tag)) return v;
        }
        return ATTACK;
    }
}

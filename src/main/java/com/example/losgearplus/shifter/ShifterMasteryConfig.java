package com.example.losgearplus.shifter;

import com.example.losgearplus.LosGearPlus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuração do servidor para a maestria dos shifters. Arquivo: {@code config/los_gear_plus-mastery.json}
 * (criado com os valores padrão na primeira vez). Edite o arquivo e use {@code /shiftermastery reload} (ou reinicie
 * o servidor) para aplicar. Só o servidor lê esse arquivo; os clientes recebem tudo pela sincronização.
 *
 * <p>Os valores abaixo são os padrões, iguais ao comportamento que o mod já tinha antes da config existir.
 */
public final class ShifterMasteryConfig {
    private ShifterMasteryConfig() {}

    /** Os campos públicos são exatamente o que aparece no arquivo JSON. */
    public static final class Data {
        public String _readme =
                "Tabelas por nivel de maestria: a lista tem 10 numeros, do nivel 0 ao nivel 9 (nessa ordem). "
                        + "maxTransformsPerLevel: -1 = ilimitado. Porcentagens: 100 = normal, 0 = nao gasta/nao drena. "
                        + "Depois de editar, use /shiftermastery reload.";

        // ---- nivel maximo --------------------------------------------------------------------------------
        /** Nivel 9: transformacoes ilimitadas, nenhum gasto de stamina (nem para transformar, nem em forma de tita, nem nas skills). */
        public boolean maxLevelInfinite = true;
        /** O Cart Titan sempre age como nivel maximo (ilimitado e sem stamina), seja qual for o nivel real dele. */
        public boolean cartAlwaysInfinite = true;

        // ---- transformacoes e stamina (indice = nivel 0..9) -------------------------------------------
        public int[] maxTransformsPerLevel = {1, 1, 1, 3, 3, 3, 6, 6, 6, -1};
        /** Quanto da stamina o ato de transformar custa (100 = custo normal do DAOT, 55 = custa 55%, 0 = de graca). */
        public int[] transformCostPercentPerLevel = {100, 100, 100, 100, 100, 100, 75, 65, 55, 0};
        /** Velocidade com que a stamina acaba em forma de tita (100 = base, 200 = acaba 2x mais rapido, 0 = nao acaba). */
        public int[] titanDrainPercentPerLevel = {200, 170, 145, 125, 110, 100, 80, 65, 50, 0};
        /** Segundos que a barra cheia dura em forma de tita com drenagem de 100%. */
        public double baseTitanSeconds = 120.0;
        /** Segundos ate as transformacoes usadas voltarem a zero (600 = 10 minutos). */
        public int transformCooldownSeconds = 600;

        // ---- experiencia ---------------------------------------------------------------------------------
        public int xpPerTransformation = 50;
        public int xpPerSecondTransformed = 1;
        /** XP total necessario para ALCANCAR cada nivel (indice = nivel; o nivel 0 e sempre 0). */
        public int[] xpToReachLevel = {0, 100, 300, 600, 1000, 1600, 2400, 3400, 4800, 7000};

        // ---- coracoes e bonus (1 coracao = 2 de vida) ----------------------------------------------------
        /** Coracoes totais do shifter na forma humana, so por ser shifter (sem maestria). O normal do Minecraft e 10. */
        public double shifterBaseHearts = 20.0;
        /** Coracoes extras ganhos a cada nivel de maestria (nivel 9 = 9x este valor). */
        public double heartsPerMasteryLevel = 1.0;
        /** Limite de coracoes totais na forma humana (0 = sem limite). */
        public double maxHearts = 0.0;
        /** Vida extra do tita por nivel, em % (10 = nivel 9 tem +90%). */
        public double titanHpPercentPerLevel = 10.0;
        /** Dano causado a mais por nivel, em % (8 = nivel 9 causa +72%). */
        public double damageDealtPercentPerLevel = 8.0;
        /** Dano recebido a menos por nivel na forma humana, em % (5 = nivel 9 recebe 45% a menos). */
        public double damageReductionPercentPerLevel = 5.0;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "los_gear_plus-mastery.json";

    private static Data current = new Data();

    public static Data get() {
        return current;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /**
     * Lê o arquivo (criando-o com os padrões se não existir) e aplica nos números do mod. Em caso de erro no JSON,
     * mantém os valores anteriores e avisa no log, sem apagar o arquivo.
     *
     * @return true se leu e aplicou sem erro
     */
    public static boolean load() {
        Path file = path();
        if (!Files.exists(file)) {
            current = new Data();
            save(file);
            apply(current);
            return true;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data read = GSON.fromJson(in, Data.class);
            if (read == null) throw new IOException("arquivo vazio");
            current = read;
            apply(current);
            return true;
        } catch (Exception e) {
            LosGearPlus.LOGGER.error("[mastery] Nao consegui ler {} ({}). Mantendo os valores anteriores.", file, e.toString());
            apply(current);
            return false;
        }
    }

    private static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new Data(), out);
            }
        } catch (IOException e) {
            LosGearPlus.LOGGER.error("[mastery] Nao consegui criar {}: {}", file, e.toString());
        }
    }

    // ---- aplicar nos números do mod -------------------------------------------------------------------------

    private static int[] fit(int[] src, int[] fallback) {
        int[] out = fallback.clone();
        if (src != null) System.arraycopy(src, 0, out, 0, Math.min(src.length, out.length)); // faltou item: fica o padrão
        return out;
    }

    private static void apply(Data d) {
        int n = ShifterMastery.MAX_LEVEL + 1;
        Data def = new Data();

        int[] transforms = fit(d.maxTransformsPerLevel, def.maxTransformsPerLevel);
        int[] costPct = fit(d.transformCostPercentPerLevel, def.transformCostPercentPerLevel);
        int[] drainPct = fit(d.titanDrainPercentPerLevel, def.titanDrainPercentPerLevel);
        int[] xp = fit(d.xpToReachLevel, def.xpToReachLevel);

        int[] maxT = new int[n];
        float[] cost = new float[n];
        float[] drain = new float[n];
        for (int i = 0; i < n; i++) {
            maxT[i] = transforms[i] < 0 ? ShifterMastery.UNLIMITED : Math.max(1, transforms[i]);
            cost[i] = Math.max(0, costPct[i]) / 100f;
            drain[i] = Math.max(0, drainPct[i]) / 100f;
        }
        int top = n - 1;
        if (d.maxLevelInfinite) {
            maxT[top] = ShifterMastery.UNLIMITED;
            cost[top] = 0f;
            drain[top] = 0f;
        } else if (maxT[top] == ShifterMastery.UNLIMITED && cost[top] == 0f && drain[top] == 0f) {
            // "infinito" desligado mas a tabela do nivel 9 ainda e a infinita: usa os valores do nivel 8.
            maxT[top] = maxT[top - 1];
            cost[top] = cost[top - 1];
            drain[top] = drain[top - 1];
        }

        // XP: o nivel 0 e sempre 0 e a lista nunca diminui (senao o nivel poderia "voltar").
        xp[0] = 0;
        for (int i = 1; i < n; i++) xp[i] = Math.max(xp[i], xp[i - 1] + 1);

        ShifterMastery.applyTables(maxT, cost, drain, xp);
        ShifterMastery.BASE_TITAN_SECONDS = (float) Math.max(1.0, d.baseTitanSeconds);
        ShifterMastery.RESET_TICKS = Math.max(0L, d.transformCooldownSeconds) * 20L;
        ShifterMastery.XP_PER_TRANSFORMATION = Math.max(0, d.xpPerTransformation);
        ShifterMastery.XP_PER_SECOND_TRANSFORMED = Math.max(0, d.xpPerSecondTransformed);
        ShifterMastery.cartAlwaysInfinite = d.cartAlwaysInfinite;
        ShifterMastery.maxLevelInfinite = d.maxLevelInfinite;

        // Coracoes: 1 coracao = 2 de vida; o jogador comum tem 20 de vida (10 coracoes).
        ShifterMasteryStats.BASE_HP_BONUS = Math.max(0.0, d.shifterBaseHearts) * 2.0 - 20.0;
        ShifterMasteryStats.HP_PER_LEVEL = Math.max(0.0, d.heartsPerMasteryLevel) * 2.0;
        ShifterMasteryStats.MAX_HEARTS = Math.max(0.0, d.maxHearts);
        ShifterMasteryStats.TITAN_HP_PER_LEVEL = Math.max(0.0, d.titanHpPercentPerLevel) / 100.0;
        ShifterMasteryStats.STRENGTH_PER_LEVEL = (float) (Math.max(0.0, d.damageDealtPercentPerLevel) / 100.0);
        // A reducao total (nivel 9) nunca passa de 90%, para o dano nunca zerar de vez.
        double maxPerLevel = 90.0 / ShifterMastery.MAX_LEVEL;
        ShifterMasteryStats.REDUCTION_PER_LEVEL = (float) (Math.min(maxPerLevel, Math.max(0.0, d.damageReductionPercentPerLevel)) / 100.0);
    }
}
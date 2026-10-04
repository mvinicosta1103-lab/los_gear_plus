package com.example.losgearplus.client.hange;

import com.example.losgearplus.LosGearPlus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Preferência (só do cliente) de tocar ou não o SFX dos óculos da Hange. Salva em config/los_gear_plus_hange_sfx.properties. */
public final class HangeSfxConfig {
    private static final String KEY = "enabled";
    private static boolean enabled = true;

    private HangeSfxConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("los_gear_plus_hange_sfx.properties");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            enabled = Boolean.parseBoolean(props.getProperty(KEY, "true"));
        } catch (IOException e) {
            LosGearPlus.LOGGER.warn("Não consegui ler {}: {}", file, e.toString());
        }
    }

    private static void save() {
        Path file = file();
        try (OutputStream out = Files.newOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty(KEY, Boolean.toString(enabled));
            props.store(out, "LOS Gear Plus - SFX dos oculos da Hange (true/false)");
        } catch (IOException e) {
            LosGearPlus.LOGGER.warn("Não consegui salvar {}: {}", file, e.toString());
        }
    }
}

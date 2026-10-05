package com.example.losgearplus.client.shifter;

import com.example.losgearplus.LosGearPlus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only preference: show or hide the Shifter Mastery HUD. Saved in config/los_gear_plus_mastery_hud.properties. */
public final class ShifterMasteryHudConfig {
	private static final String KEY = "visible";
	private static boolean visible = true;

	private ShifterMasteryHudConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("los_gear_plus_mastery_hud.properties");
	}

	public static boolean isVisible() {
		return visible;
	}

	public static void setVisible(boolean value) {
		visible = value;
		save();
	}

	public static void load() {
		Path file = file();
		if (!Files.exists(file)) return;
		try (InputStream in = Files.newInputStream(file)) {
			Properties props = new Properties();
			props.load(in);
			visible = Boolean.parseBoolean(props.getProperty(KEY, "true"));
		} catch (IOException e) {
			LosGearPlus.LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}

	private static void save() {
		Path file = file();
		try (OutputStream out = Files.newOutputStream(file)) {
			Properties props = new Properties();
			props.setProperty(KEY, Boolean.toString(visible));
			props.store(out, "LOS Gear Plus - Shifter Mastery HUD (true/false)");
		} catch (IOException e) {
			LosGearPlus.LOGGER.warn("Could not save {}: {}", file, e.toString());
		}
	}
}

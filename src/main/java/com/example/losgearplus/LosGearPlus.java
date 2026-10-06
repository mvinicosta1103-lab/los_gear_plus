package com.example.losgearplus;

import com.example.losgearplus.compat.LosGearCompat;
import com.example.losgearplus.mode.OdmgModeNetworking;
import com.example.losgearplus.shifter.ShifterForceShift;
import com.example.losgearplus.shifter.ShifterMastery;
import com.example.losgearplus.shifter.ShifterMasteryCommand;
import com.example.losgearplus.shifter.ShifterMasteryStats;
import com.example.losgearplus.shifter.ShifterMasterySync;
import com.example.losgearplus.steam.SteamHealServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LosGearPlus implements ModInitializer {
	public static final String MOD_ID = "los_gear_plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		OdmgModeNetworking.init();
		SteamHealServer.init();
		ShifterMastery.init();
		ShifterMasteryCommand.init();
		ShifterMasterySync.init();
		ShifterMasteryStats.init();
		ShifterForceShift.init();

		// los_gear é "recommends": só toca nas classes dele se estiver instalado.
		if (FabricLoader.getInstance().isModLoaded("los_gear")) {
			LosGearCompat.init();
		}

		LOGGER.info("LOS Gear Plus carregado (DAOT presente: {})",
				FabricLoader.getInstance().isModLoaded("dannys-aot"));
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
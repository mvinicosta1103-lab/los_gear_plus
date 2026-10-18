package com.example.losgearplus.shifter;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Packets of the Shifter Mastery config menu. Server -> client: {@link Open}. Client -> server: {@link Save}. */
public final class MasteryConfigPayloads {
	private MasteryConfigPayloads() {}

	private static final int MAX_JSON = 32767;

	/** "Open the menu with these values." The JSON is the current config. */
	public record Open(String json) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(LosGearPlus.id("mastery_config_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
				StreamCodec.of((buf, p) -> buf.writeUtf(p.json, MAX_JSON), buf -> new Open(buf.readUtf(MAX_JSON)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** "Save these values." The server only accepts it from operators and re-validates everything. */
	public record Save(String json) implements CustomPacketPayload {
		public static final Type<Save> TYPE = new Type<>(LosGearPlus.id("mastery_config_save"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC =
				StreamCodec.of((buf, p) -> buf.writeUtf(p.json, MAX_JSON), buf -> new Save(buf.readUtf(MAX_JSON)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}

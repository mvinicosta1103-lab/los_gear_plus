package com.example.losgearplus.shifter;

import com.example.losgearplus.LosGearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: Shifter Mastery state for the HUD.
 *
 * @param level      current level (0..9)
 * @param used       transformations used in the current window
 * @param max        transformation limit of the level; -1 = unlimited
 * @param resetTicks ticks left until used transformations reset (the client counts down from here)
 * @param shifter    whether the player has any shifter tag (tags do not exist on the client, so it travels here)
 * @param xp         mastery XP earned inside the current level
 * @param xpNeeded   XP needed to finish the current level; -1 at the maximum level
 */
public record ShifterMasterySyncPayload(int level, int used, int max, int resetTicks, boolean shifter, int xp, int xpNeeded)
		implements CustomPacketPayload {
	public static final Type<ShifterMasterySyncPayload> TYPE = new Type<>(LosGearPlus.id("shifter_mastery_sync"));

	// 7 fields: more than StreamCodec.composite supports, so it is written by hand.
	public static final StreamCodec<RegistryFriendlyByteBuf, ShifterMasterySyncPayload> CODEC =
			StreamCodec.of(ShifterMasterySyncPayload::write, ShifterMasterySyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, ShifterMasterySyncPayload p) {
		buf.writeVarInt(p.level);
		buf.writeVarInt(p.used);
		buf.writeInt(p.max);
		buf.writeVarInt(p.resetTicks);
		buf.writeBoolean(p.shifter);
		buf.writeVarInt(p.xp);
		buf.writeInt(p.xpNeeded);
	}

	private static ShifterMasterySyncPayload read(RegistryFriendlyByteBuf buf) {
		return new ShifterMasterySyncPayload(
				buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readVarInt(), buf.readInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

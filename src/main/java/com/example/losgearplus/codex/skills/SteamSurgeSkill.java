package com.example.losgearplus.codex.skills;

import com.example.losgearplus.codex.SmokeCurtain;
import com.example.losgearplus.codex.TitanEject;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Steam Surge: o titã solta uma grande cortina de fumaça que EJETA o shifter (igual ao Smoke Screen do Cart Titan)
 * e o protege: por 10 segundos nenhum golpe o atinge.
 *
 * <p>A ejeção (dismount do DAOT + arremesso + animação do Cart) está em {@link TitanEject}; a cortina e a proteção
 * em {@link SmokeCurtain}. O resto (imunidade à queda, o titã evaporando) o próprio DAOT faz quando o piloto sai.
 * Serve para todos os titãs do Codex. O ID continua {@code los_gear_plus:steam_surge}.
 */
public final class SteamSurgeSkill extends AbstractCustomSkill {
	public SteamSurgeSkill() {
		super("steam_surge", Set.of(), 20 * 60, 0.15f);
	}

	@Override
	public boolean activate(ServerPlayer player, LivingEntity titan) {
		double radius = Math.min(24.0, Math.max(5.0, titan.getBbWidth() * 1.2));
		double height = Math.min(40.0, Math.max(10.0, titan.getBbHeight() * 0.6));
		if (!TitanEject.eject(player, titan, true)) return false;
		SmokeCurtain.start(player, titan.getX(), titan.getY(), titan.getZ(), radius, height);
		return true;
	}
}

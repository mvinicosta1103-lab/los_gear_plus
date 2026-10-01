package com.example.losgearplus.hook;

import net.minecraft.world.phys.Vec3;

/**
 * Matemática do ângulo dos ganchos (sem estado, sem rede).
 *
 * O valor do ângulo é a ABERTURA TOTAL entre os dois ganchos, de 0 a 180 graus:
 *  - 0   -> os dois ganchos saem retos, na direção da mira;
 *  - 90  -> cada gancho desvia 45 graus para o seu lado (abertura total de 90);
 *  - 180 -> cada gancho desvia 90 graus (um para cada lado, perpendiculares à mira).
 *
 * Por que "abertura total" e não "desvio por lado" (como o número mostrado no WoF)? Porque assim
 * quanto maior o número, mais espaçados ficam os ganchos, de forma contínua até 180. No WoF o
 * desvio por lado vai de 0 a 90, ou seja, a mesma faixa física (0 a 180 de abertura).
 */
public final class HookAngles {
	private HookAngles() {}

	/** Abertura total máxima, em graus. */
	public static final int MAX_SPREAD = 180;

	/** Quantos graus cada "clique" do scroll muda (o WoF usa 10 por padrão, configurável de 1 a 90). */
	public static final int STEP = 10;

	public static int clamp(int degrees) {
		return Math.max(0, Math.min(MAX_SPREAD, degrees));
	}

	/**
	 * Desvia a direção da mira para o lado do gancho: o direito vira para a direita e o esquerdo para a
	 * esquerda, cada um metade da abertura. Só gira em torno do eixo Y (leque horizontal, como no WoF),
	 * então a inclinação vertical da mira e o comprimento do vetor se mantêm.
	 *
	 * Conta: no Minecraft o yaw cresce no sentido horário visto de cima e a direção é
	 * (x, z) = (-sen(yaw), cos(yaw)); virar à direita por d é yaw + d, o que dá
	 *   x' = x*cos(d) - z*sen(d)   e   z' = z*cos(d) + x*sen(d).
	 * (Equivale ao Vec3.yRot(-d) do Minecraft; o WoF faz o mesmo: direito -ângulo, esquerdo +ângulo.)
	 */
	public static Vec3 spread(Vec3 aim, boolean rightHook, int spreadDegrees) {
		int spread = clamp(spreadDegrees);
		if (spread == 0) return aim;
		double d = Math.toRadians(spread / 2.0);
		double cos = Math.cos(d);
		double sin = rightHook ? Math.sin(d) : -Math.sin(d);
		return new Vec3(aim.x * cos - aim.z * sin, aim.y, aim.z * cos + aim.x * sin);
	}
}

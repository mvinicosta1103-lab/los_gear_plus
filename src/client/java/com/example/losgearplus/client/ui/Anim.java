package com.example.losgearplus.client.ui;

/** Funções de easing e suavização usadas por todas as animações de UI. */
public final class Anim {
	private Anim() {}

	public static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static float easeOutCubic(float t) {
		float u = 1f - clamp01(t);
		return 1f - u * u * u;
	}

	/** Passa um pouco do alvo e volta (efeito "pop"). */
	public static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f, c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	/** Aproximação exponencial independente de FPS: k maior = mais rápido. dt em segundos. */
	public static float smooth(float current, float target, float k, float dt) {
		return current + (target - current) * (1f - (float) Math.exp(-k * dt));
	}
}

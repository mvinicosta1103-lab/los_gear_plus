package com.example.losgearplus.limb;

/** Estado de uma parte: inteira, perdida (sem nada) ou crescendo (toco que vai tomando forma). */
public enum LimbStatus {
	INTACT, LOST, REGROWING;

	public static LimbStatus byId(int id) {
		LimbStatus[] v = values();
		return id >= 0 && id < v.length ? v[id] : INTACT;
	}
}

package com.example.losgearplus.weaker;

/** Formas de um shifter (o Weaker só existe para o Armored). PARTIAL usa a tecla própria do Partial Shifting (não é guardada). */
public enum ShifterForm {
    NORMAL, WEAKER, PARTIAL;

    public static ShifterForm byId(int id) {
        ShifterForm[] v = values();
        return id >= 0 && id < v.length ? v[id] : NORMAL;
    }
}

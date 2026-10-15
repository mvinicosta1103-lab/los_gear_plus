package com.example.losgearplus.codex;

import com.example.losgearplus.LosGearPlus;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Agenda código para daqui a N ticks do servidor (ex.: o impacto de uma animação). Só em memória. */
public final class CodexScheduler {
	private CodexScheduler() {}

	private record Task(int dueTick, Runnable run) {}

	private static final List<Task> TASKS = new ArrayList<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CodexScheduler::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear()); // não vaza para o próximo mundo
	}

	/** Roda {@code run} daqui a {@code delayTicks} ticks (mínimo 1), na thread do servidor. */
	public static void runLater(MinecraftServer server, int delayTicks, Runnable run) {
		TASKS.add(new Task(server.getTickCount() + Math.max(1, delayTicks), run));
	}

	private static void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) return;
		int now = server.getTickCount();
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
			Task t = it.next();
			if (t.dueTick() <= now) {
				due.add(t.run());
				it.remove();
			}
		}
		// Executa depois de varrer a lista: uma tarefa pode agendar outra sem mexer na lista durante a iteração.
		for (Runnable r : due) {
			try {
				r.run();
			} catch (Throwable t) {
				LosGearPlus.LOGGER.error("Falha numa tarefa agendada do Codex", t);
			}
		}
	}
}

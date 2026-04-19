package com.hfstudio.bqapi.runtime;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.BQApi;

import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;

public final class BQReinjector {

    private static final long DEBOUNCE_MS = 50L;
    private static final AtomicLong lastReinjectMs = new AtomicLong(0L);

    private BQReinjector() {}

    public static void reinject(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long prev = lastReinjectMs.get();
        if (now - prev < DEBOUNCE_MS) {
            return;
        }
        if (!lastReinjectMs.compareAndSet(prev, now)) {
            return;
        }

        BQApi.reinject(server);
        NetSettingSync.sendSync(null);
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }
}

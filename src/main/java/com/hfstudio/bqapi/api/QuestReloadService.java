package com.hfstudio.bqapi.api;

/**
 * SPI contract for dynamic quest reloading.
 *
 * <p>
 * Implementations are discovered through Java {@link java.util.ServiceLoader}
 * using a service descriptor file:
 * {@code META-INF/services/com.hfstudio.bqapi.api.QuestReloadService}
 * with one implementation class name per line.
 *
 * <p>
 * Every discovered implementation is invoked at the beginning of each
 * {@link com.hfstudio.bqapi.BQApi#reinject} cycle.
 */
public interface QuestReloadService {

    /**
     * Called before definitions are applied to BetterQuesting in a reinject cycle.
     */
    void reloadQuest();
}

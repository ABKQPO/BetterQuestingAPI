package com.hfstudio.bqapi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.api.QuestReloadService;
import com.hfstudio.bqapi.api.QuestReloader;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolder;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolders;
import com.hfstudio.bqapi.runtime.BQDefinitionRegistry;
import com.hfstudio.bqapi.runtime.BQRegistrationInfo;
import com.hfstudio.bqapi.runtime.BQReloaderRegistry;
import com.hfstudio.bqapi.runtime.BQRuntimeApplier;
import com.hfstudio.bqapi.runtime.BQStableIdFactory;
import com.hfstudio.bqapi.runtime.importer.BQImportedQuestLoader;

public final class BQApi {

    private static final BQDefinitionRegistry REGISTRY = new BQDefinitionRegistry();
    private static final BQReloaderRegistry RELOADER_REGISTRY = new BQReloaderRegistry();

    static {
        // Auto-discover SPI reload participants from classpath.
        RELOADER_REGISTRY.registerFromServiceLoader(BQApi.class.getClassLoader());
    }

    private BQApi() {}

    // ---- Registration ----

    public static void register(ChapterDefinition chapter) {
        REGISTRY.register(chapter);
    }

    public static List<ChapterDefinition> importFolder(String modId, String rootPath) {
        return importFolder(
            ImportedQuestFolders.folder(modId, rootPath)
                .build());
    }

    public static List<ChapterDefinition> importFolder(ImportedQuestFolder folder) {
        return new BQImportedQuestLoader().importFolder(folder);
    }

    public static void registerImportedFolder(String modId, String rootPath) {
        registerImportedFolder(
            ImportedQuestFolders.folder(modId, rootPath)
                .build());
    }

    public static void registerImportedFolder(ImportedQuestFolder folder) {
        for (ChapterDefinition chapter : importFolder(folder)) {
            register(chapter);
        }
    }

    // ---- Query: chapters ----

    /**
     * Returns the registered chapter with the given string ID,
     * or {@link Optional#empty()} if no such chapter exists.
     */
    public static Optional<ChapterDefinition> getChapter(String chapterId) {
        return REGISTRY.getChapter(chapterId);
    }

    /**
     * Returns the registered chapter with the given UUID,
     * or {@link Optional#empty()} if no such chapter exists.
     */
    public static Optional<ChapterDefinition> getChapter(UUID chapterUuid) {
        return REGISTRY.getChapter(chapterUuid);
    }

    /** Returns {@code true} if a chapter with the given string ID has been registered. */
    public static boolean hasChapter(String chapterId) {
        return REGISTRY.hasChapter(chapterId);
    }

    /** Returns {@code true} if a chapter with the given UUID has been registered. */
    public static boolean hasChapter(UUID chapterUuid) {
        return REGISTRY.hasChapter(chapterUuid);
    }

    // ---- Query: quests ----

    /**
     * Returns the registered quest with the given UUID,
     * or {@link Optional#empty()} if no such quest exists.
     */
    public static Optional<QuestDefinition> getQuest(UUID questUuid) {
        return REGISTRY.getQuest(questUuid);
    }

    /**
     * Returns the registered quest with the given string ID,
     * or {@link Optional#empty()} if no such quest exists.
     * <p>
     * The UUID is derived via {@link com.hfstudio.bqapi.runtime.BQStableIdFactory#quest(String)}.
     */
    public static Optional<QuestDefinition> getQuest(String questId) {
        return REGISTRY.getQuest(questId);
    }

    /** Returns {@code true} if a quest with the given UUID has been registered. */
    public static boolean hasQuest(UUID questUuid) {
        return REGISTRY.hasQuest(questUuid);
    }

    /** Returns {@code true} if a quest with the given string ID has been registered. */
    public static boolean hasQuest(String questId) {
        return REGISTRY.hasQuest(questId);
    }

    public static Set<UUID> getRegisteredChapterUuids() {
        return REGISTRY.getRegisteredChapterUuids();
    }

    public static Set<UUID> getRegisteredQuestUuids() {
        return REGISTRY.getRegisteredQuestUuids();
    }

    /** Returns an unmodifiable view of all registered {@link ChapterDefinition}s. */
    public static Collection<ChapterDefinition> getRegisteredChapters() {
        return REGISTRY.getChapters();
    }

    // ---- Debug: registration metadata ----

    /**
     * Returns the {@link BQRegistrationInfo} captured when the chapter with the given string ID
     * was most recently registered via {@link #register}, or {@link Optional#empty()} if the
     * chapter is not registered.
     */
    public static Optional<BQRegistrationInfo> getChapterRegistrationInfo(String chapterId) {
        return REGISTRY.getChapterRegistrationInfo(chapterId);
    }

    /**
     * Returns the {@link BQRegistrationInfo} for the chapter identified by {@code chapterUuid},
     * or {@link Optional#empty()} if the chapter is not registered.
     */
    public static Optional<BQRegistrationInfo> getChapterRegistrationInfo(UUID chapterUuid) {
        return REGISTRY.getChapterRegistrationInfo(chapterUuid);
    }

    /**
     * Returns the {@link BQRegistrationInfo} of the chapter that contains the quest with the
     * given UUID, or {@link Optional#empty()} if the quest is not registered.
     */
    public static Optional<BQRegistrationInfo> getQuestRegistrationInfo(UUID questUuid) {
        return REGISTRY.getQuestChapterId(questUuid)
            .flatMap(REGISTRY::getChapterRegistrationInfo);
    }

    /**
     * Returns the {@link BQRegistrationInfo} of the chapter that contains the quest with the
     * given string ID (UUID derived via {@link BQStableIdFactory#quest(String)}), or
     * {@link Optional#empty()} if the quest is not registered.
     */
    public static Optional<BQRegistrationInfo> getQuestRegistrationInfo(String questId) {
        return getQuestRegistrationInfo(BQStableIdFactory.quest(questId));
    }

    /**
     * Returns all {@link BQRegistrationInfo} snapshots captured each time
     * {@link #patchQuest(UUID, java.util.function.UnaryOperator)} was called for the given quest UUID,
     * in registration order. Returns an empty list if no patches have been registered.
     */
    public static List<BQRegistrationInfo> getQuestPatchRegistrationInfos(UUID questUuid) {
        return REGISTRY.getPatchRegistrationInfos(questUuid);
    }

    /** Returns the number of quests that have at least one runtime patch registered. */
    public static int getPatchedQuestCount() {
        return REGISTRY.getPatchedQuestCount();
    }

    // ---- Runtime patches ----

    /**
     * Registers a runtime patch for the quest with the given UUID.
     *
     * <p>
     * On every {@link #reinject} call the original {@link QuestDefinition} is passed to
     * {@code patchFn} and the returned value is written to BetterQuesting. The original
     * registered data is never modified. Multiple patches on the same quest are applied in
     * registration order.
     *
     * <p>
     * Typical usage (together with {@link com.hfstudio.bqapi.api.builder.Quests#copyOf}):
     * 
     * <pre>
     * {@code
     * BQApi.patchQuest(myQuestUuid, def ->
     *     Quests.copyOf(def)
     *         .task(TaskBuilders.retrieval().item(...).build())
     *         .build());
     * }
     * </pre>
     *
     * <p>
     * <strong>Register patches once at startup</strong>, not inside a
     * {@link QuestReloader#reloadQuest()} method — patches are persistent and accumulate
     * across reinject cycles.
     *
     * @param questUuid the UUID of the target quest
     * @param patchFn   function that receives the current definition and returns the patched
     *                  one; must not return {@code null}
     */
    public static void patchQuest(UUID questUuid, UnaryOperator<QuestDefinition> patchFn) {
        REGISTRY.registerQuestPatch(questUuid, patchFn);
    }

    /**
     * Registers a runtime patch for the quest with the given string ID
     * (UUID derived via {@link BQStableIdFactory#quest(String)}).
     *
     * @see #patchQuest(UUID, UnaryOperator)
     */
    public static void patchQuest(String questId, UnaryOperator<QuestDefinition> patchFn) {
        patchQuest(BQStableIdFactory.quest(questId), patchFn);
    }

    // ---- Reload participants ----

    /**
     * Registers a class as a reload participant. The class must:
     * <ul>
     * <li>Be annotated with {@link QuestReloader}.</li>
     * <li>Declare a {@code public static void reloadQuest()} method.</li>
     * </ul>
     *
     * <p>
     * {@code reloadQuest()} is invoked at the start of every {@link #reinject} cycle,
     * before definitions are written to BetterQuesting. Registering the same class more than
     * once is a no-op.
     *
     * @param clazz the class to register
     * @throws IllegalArgumentException if the class does not satisfy the contract
     */
    public static void registerReloader(Class<?> clazz) {
        RELOADER_REGISTRY.register(clazz);
    }

    /**
     * Registers a SPI reloader instance manually.
     *
     * <p>
     * Most mods should prefer Java SPI auto-discovery via
     * {@code META-INF/services/com.hfstudio.bqapi.api.QuestReloadService}.
     */
    public static void registerReloaderService(QuestReloadService service) {
        RELOADER_REGISTRY.registerService(service);
    }

    // ---- Apply ----

    public static void reinject(MinecraftServer server) {
        // Invoke reload participants first so they can update registrations dynamically.
        RELOADER_REGISTRY.invokeAll();
        new BQRuntimeApplier(REGISTRY).applyAll(server);
    }
}

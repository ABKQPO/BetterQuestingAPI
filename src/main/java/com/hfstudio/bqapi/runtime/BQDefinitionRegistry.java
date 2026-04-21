package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;

public final class BQDefinitionRegistry {

    private final Map<String, ChapterDefinition> chapters = new LinkedHashMap<>();
    private final Map<UUID, String> chapterIdsByUuid = new LinkedHashMap<>();

    // Quest index: rebuilt whenever a new chapter is registered.
    private final Map<UUID, QuestDefinition> questsByUuid = new LinkedHashMap<>();
    private final Map<String, QuestDefinition> questsById = new LinkedHashMap<>();

    // Runtime patch lists, applied in registration order during each reinject cycle.
    private final Map<UUID, List<UnaryOperator<QuestDefinition>>> questPatches = new LinkedHashMap<>();

    public synchronized void register(ChapterDefinition chapter) {
        ChapterDefinition value = Objects.requireNonNull(chapter, "chapter");
        String existingId = chapterIdsByUuid.get(value.getUuid());
        if (existingId != null && !existingId.equals(value.getId())) {
            chapters.remove(existingId);
        }
        ChapterDefinition previous = chapters.put(value.getId(), value);
        if (previous != null && !previous.getUuid()
            .equals(value.getUuid())) {
            chapterIdsByUuid.remove(previous.getUuid());
        }
        chapterIdsByUuid.put(value.getUuid(), value.getId());
        rebuildQuestIndex();
    }

    /**
     * Registers a runtime patch for the quest identified by {@code questUuid}.
     * Patches are applied in registration order during each reinject cycle and
     * do not modify the original registered data. Multiple patches may be added
     * for the same quest.
     */
    public synchronized void registerQuestPatch(UUID questUuid, UnaryOperator<QuestDefinition> patch) {
        Objects.requireNonNull(questUuid, "questUuid");
        Objects.requireNonNull(patch, "patch");
        questPatches.computeIfAbsent(questUuid, k -> new ArrayList<>())
            .add(patch);
    }

    /**
     * Applies all registered patches for the given quest in registration order and
     * returns the final result. Returns the quest unchanged if no patches are registered.
     */
    public synchronized QuestDefinition applyPatchesTo(QuestDefinition quest) {
        Objects.requireNonNull(quest, "quest");
        List<UnaryOperator<QuestDefinition>> patches = questPatches.get(quest.getUuid());
        if (patches == null || patches.isEmpty()) {
            return quest;
        }
        QuestDefinition result = quest;
        for (UnaryOperator<QuestDefinition> p : patches) {
            result = Objects.requireNonNull(p.apply(result), "quest patch must not return null");
        }
        return result;
    }

    public synchronized Optional<ChapterDefinition> getChapter(String id) {
        return Optional.ofNullable(chapters.get(id));
    }

    public synchronized Optional<ChapterDefinition> getChapter(UUID uuid) {
        String id = chapterIdsByUuid.get(uuid);
        return id == null ? Optional.empty() : Optional.ofNullable(chapters.get(id));
    }

    public synchronized boolean hasChapter(String id) {
        return chapters.containsKey(id);
    }

    public synchronized boolean hasChapter(UUID uuid) {
        return chapterIdsByUuid.containsKey(uuid);
    }

    public synchronized Optional<QuestDefinition> getQuest(UUID uuid) {
        return Optional.ofNullable(questsByUuid.get(uuid));
    }

    public synchronized Optional<QuestDefinition> getQuest(String id) {
        return Optional.ofNullable(questsById.get(id));
    }

    public synchronized boolean hasQuest(UUID uuid) {
        return questsByUuid.containsKey(uuid);
    }

    public synchronized boolean hasQuest(String id) {
        return questsById.containsKey(id);
    }

    public synchronized Collection<ChapterDefinition> getChapters() {
        return Collections.unmodifiableCollection(chapters.values());
    }

    public synchronized Set<UUID> getRegisteredChapterUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(chapterIdsByUuid.keySet()));
    }

    public synchronized Set<UUID> getRegisteredQuestUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(questsByUuid.keySet()));
    }

    private void rebuildQuestIndex() {
        questsByUuid.clear();
        questsById.clear();
        for (ChapterDefinition chapter : chapters.values()) {
            for (QuestPlacementDefinition placement : chapter.getPlacements()) {
                QuestDefinition q = placement.getQuest();
                questsByUuid.put(q.getUuid(), q);
                questsById.put(q.getId(), q);
            }
        }
    }
}

package com.hfstudio.bqapi.api.builder;

import com.hfstudio.bqapi.api.definition.QuestDefinition;

public final class Quests {

    private Quests() {}

    public static QuestBuilder quest(String id) {
        return QuestBuilder.quest(id);
    }

    /**
     * Creates a copy builder from an existing {@link QuestDefinition}, preserving all
     * fields (including the UUID). Typical usage is inside a
     * {@link com.hfstudio.bqapi.BQApi#patchQuest} lambda to produce a modified copy:
     *
     * <pre>
     * {@code
     * BQApi.patchQuest("my.quest", def ->
     *     Quests.copyOf(def)
     *         .task(TaskBuilders.retrieval().item(...).build())
     *         .build());
     * }
     * </pre>
     */
    public static QuestBuilder copyOf(QuestDefinition definition) {
        return QuestBuilder.from(definition);
    }
}

package com.hfstudio.bqapi.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;
import com.hfstudio.bqapi.runtime.apply.ChapterApplier;
import com.hfstudio.bqapi.runtime.apply.QuestApplier;
import com.hfstudio.bqapi.runtime.apply.TaskApplier;

public final class BQRuntimeApplier {

    private final BQDefinitionRegistry registry;
    private final QuestApplier questApplier = new QuestApplier(new TaskApplier());
    private final ChapterApplier chapterApplier = new ChapterApplier();

    public BQRuntimeApplier(BQDefinitionRegistry registry) {
        this.registry = registry;
    }

    public void applyAll(MinecraftServer server) {
        Collection<ChapterDefinition> chapters = registry.getChapters();
        Map<UUID, QuestDefinition> quests = new LinkedHashMap<>();
        Map<UUID, Boolean> questPatched = new LinkedHashMap<>();

        for (ChapterDefinition chapter : chapters) {
            for (QuestPlacementDefinition placement : chapter.getPlacements()) {
                QuestDefinition originalQuest = placement.getQuest();
                // Apply any runtime patches registered by callers; patches do not
                // modify the original registered data and are only effective in this apply run.
                QuestDefinition quest = registry.applyPatchesTo(originalQuest);
                boolean patched = quest != originalQuest;
                quests.put(quest.getUuid(), quest);
                questPatched.put(quest.getUuid(), patched);
            }
        }

        for (Map.Entry<UUID, QuestDefinition> entry : quests.entrySet()) {
            questApplier.apply(entry.getValue(), questPatched.getOrDefault(entry.getKey(), false));
        }

        for (ChapterDefinition chapter : chapters) {
            chapterApplier.apply(chapter);
        }
    }
}

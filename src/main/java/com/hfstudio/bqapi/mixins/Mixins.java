package com.hfstudio.bqapi.mixins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.common.ModAPIManager;

public enum Mixins {

    BETTER_QUESTING_RUNTIME("betterquesting", "BetterQuesting.MixinQuestCommandDefaults");

    private final String requiredModId;
    private final String mixinClass;

    Mixins(String requiredModId, String mixinClass) {
        this.requiredModId = requiredModId;
        this.mixinClass = mixinClass;
    }

    public static List<String> collectLateMixins(Set<String> loadedMods) {
        Set<String> presentMods = loadedMods == null ? Collections.emptySet() : loadedMods;
        List<String> result = new ArrayList<>();
        for (Mixins mixin : values()) {
            if (presentMods.contains(mixin.requiredModId) || ModAPIManager.INSTANCE.hasAPI(mixin.requiredModId)) {
                result.add(mixin.mixinClass);
            }
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }
}

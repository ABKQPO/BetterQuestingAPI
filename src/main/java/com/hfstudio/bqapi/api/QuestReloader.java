package com.hfstudio.bqapi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a BetterQuestingAPI reload participant.
 *
 * <p>
 * Classes annotated with {@code @QuestReloader} must declare a
 * {@code public static void reloadQuest()} method. BetterQuestingAPI will
 * invoke that method on every reinject cycle (including after
 * {@code /bq_admin default load} reloads BetterQuesting's database).
 *
 * <p>
 * The class must be explicitly registered once (typically during
 * {@code FMLInitializationEvent} or {@code FMLPostInitializationEvent}):
 * 
 * <pre>
 * {@code
 * BQApi.registerReloader(MyQuestSetup.class);
 * }
 * </pre>
 *
 * <p>
 * The {@code reloadQuest()} method is called <em>before</em>
 * {@link com.hfstudio.bqapi.runtime.BQRuntimeApplier} writes definitions into
 * BetterQuesting, so any chapters or quests registered inside it will be
 * applied in the same reinject cycle.
 *
 * <p>
 * <strong>Important</strong>: do <em>not</em> call
 * {@link com.hfstudio.bqapi.BQApi#patchQuest} inside {@code reloadQuest()}.
 * Patches are persistent and accumulate across calls; registering the same
 * patch repeatedly will apply it multiple times per reinject. Register patches
 * once at startup instead.
 *
 * <p>
 * Example:
 * 
 * <pre>
 * {@code
 * {@literal @}QuestReloader
 * public class DynamicQuestSetup {
 *
 *     public static void reloadQuest() {
 *         // Re-register chapters that depend on runtime state
 *         if (Loader.isModLoaded("somemod")) {
 *             BQApi.register(buildCompatChapter());
 *         }
 *     }
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QuestReloader {}

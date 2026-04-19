# BetterQuestingAPI

中文文档: [README.zh-CN.md](README.zh-CN.md)

BetterQuestingAPI is a Minecraft Forge 1.7.10 runtime API mod for registering and applying BetterQuesting chapters, quests, tasks, and rewards from code or imported resources.

## What This Project Provides

- Stable Java API for quest definition and registration.
- Runtime apply pipeline to inject registered definitions into BetterQuesting.
- Optional importer flow to load chapter/line structures from resources.
- Mixin integration for runtime behavior extensions.

Core entry points:

- Mod bootstrap: [src/main/java/com/hfstudio/bqapi/BQApiMod.java](src/main/java/com/hfstudio/bqapi/BQApiMod.java)
- Public facade: [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java)
- Builders: [src/main/java/com/hfstudio/bqapi/api/builder](src/main/java/com/hfstudio/bqapi/api/builder)
- Definitions: [src/main/java/com/hfstudio/bqapi/api/definition](src/main/java/com/hfstudio/bqapi/api/definition)
- Importer API: [src/main/java/com/hfstudio/bqapi/api/importer](src/main/java/com/hfstudio/bqapi/api/importer)
- Runtime apply: [src/main/java/com/hfstudio/bqapi/runtime](src/main/java/com/hfstudio/bqapi/runtime)
- Mixin loader/config: [src/main/java/com/hfstudio/bqapi/mixins](src/main/java/com/hfstudio/bqapi/mixins), [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json), [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)

## Build

Windows:

```bash
gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

Tests:

```bash
gradlew.bat test
```

## Artifacts

`build` now keeps the normal mod outputs and also produces an additional API-only jar with classifier `api`.

Expected outputs include:

- Main mod jar (contains mod bootstrap and mixin metadata).
- Dev/sources artifacts from GTNH conventions.
- Additional API jar: `*-api.jar`.

The API jar is intentionally built without:

- Mod bootstrap class: `com.hfstudio.bqapi.BQApiMod`.
- Mixin implementation package: `com.hfstudio.bqapi.mixins`.
- Mixin config resources: `mixins.bqapi.json`, `mixins.bqapi.late.json`.

This keeps runtime initialization and mixin behavior in the normal mod jar unchanged, while exposing a lightweight compile/jar-in-jar API artifact for other mods.

## API Tutorial

### 1. Register Quests in Code

The primary facade is [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java).

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.RewardBuilders;
import com.hfstudio.bqapi.api.builder.TaskBuilders;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;

public final class ExampleQuestRegistration {

	public static void registerAll() {
		QuestDefinition starterQuest = Quests.quest("starter_collect_logs")
			.task(TaskBuilders.retrieval("collect_logs")
				.item("minecraft:log", 16, 0)
				.consume(true)
				.build())
			.reward(RewardBuilders.item("starter_reward")
				.item("minecraft:iron_ingot", 4, 0)
				.build())
			.build();

		ChapterDefinition chapter = Chapters.chapter("chapter_getting_started")
			.icon("minecraft:book", 1, 0)
			.quest(new QuestPlacementDefinition(starterQuest, 0, 0, 2, 2))
			.build();

		BQApi.register(chapter);
	}
}
```

Recommended lifecycle usage:

- Register chapters/quests during your mod init phase.
- If your definitions are added after server start, call `BQApi.reinject(server)` manually.

### 2. Import Quest Resources

You can import chapters from resources under your own assets path.

```java
import com.hfstudio.bqapi.BQApi;

public final class ExampleImportRegistration {

	public static void registerImported() {
		BQApi.registerImportedFolder("yourmodid", "bqapi/quests");
	}
}
```

This resolves resources from:

- `assets/yourmodid/bqapi/quests`

For advanced control (custom classloader, explicit line directory, UUID behavior), use:

- [src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java](src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java)
- [src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java](src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java)

### 3. Runtime Reinjection

Default runtime reinjection on server start is handled by [src/main/java/com/hfstudio/bqapi/BQApiMod.java](src/main/java/com/hfstudio/bqapi/BQApiMod.java).

If your mod dynamically changes quest definitions after startup, trigger reinjection:

```java
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;
import com.hfstudio.bqapi.BQApi;

MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
if (server != null) {
	BQApi.reinject(server);
}
```

### 4. Query Existing Chapters and Quests

At any point after registration you can look up definitions by ID or UUID:

```java
import java.util.Optional;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;

// Check existence
boolean exists = BQApi.hasQuest("mymod.chapter1.intro");

// Retrieve by string ID
Optional<QuestDefinition> quest = BQApi.getQuest("mymod.chapter1.intro");
quest.ifPresent(q -> System.out.println("UUID: " + q.getUuid()));

// Retrieve by UUID
Optional<ChapterDefinition> chapter = BQApi.getChapter("mymod.chapter1");
```

### 5. Runtime Quest Patching

Register a patch once at startup (e.g. `FMLPostInitializationEvent`). The patch is
applied on every `reinject` call without modifying the original registered definition:

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.TaskBuilders;

// Append an extra retrieval task to a quest registered by another mod
BQApi.patchQuest("othermod.some_quest", def ->
	Quests.copyOf(def)
		.task(TaskBuilders.retrieval()
			.item("minecraft:diamond", 1)
			.build())
		.build());
```

Multiple patches on the same quest are applied in registration order.

### 6. Dynamic Reload with `@QuestReloader`

Use `@QuestReloader` to re-register chapters or definitions on every reload
(including `/bq_admin default load`). Register the class once at startup:

```java
// In your @Mod event handler:
BQApi.registerReloader(MyQuestSetup.class);
```

```java
import com.hfstudio.bqapi.api.QuestReloader;
import com.hfstudio.bqapi.BQApi;
import cpw.mods.fml.common.Loader;

@QuestReloader
public class MyQuestSetup {

	/**
	 * Called automatically before every reinject cycle.
	 * Re-register chapters that depend on runtime state.
	 */
	public static void reloadQuest() {
		if (Loader.isModLoaded("somemod")) {
			BQApi.register(buildCompatChapter());
		}
	}
}
```

> **Note**: do **not** call `BQApi.patchQuest()` inside `reloadQuest()`. Patches are
> persistent and accumulate across calls; register them once at startup instead.

Use the classified `api` artifact as your compile/jar-in-jar dependency.

### Gradle Dependency (Template)

Use the published coordinates for this project and request classifier `api`.

```gradle
dependencies {
	// Replace with actual group:artifact:version from published coordinates
	implementation("<group>:<artifact>:<version>:api")
}
```

If your build uses GTNH deobf helpers, apply your usual deobf wrapper around the same `:api` coordinate.

### Forge API Annotation Identity

The project now declares a Forge `@API` annotation on the API package with API id `bqapi|api`:

- Declaration: [src/main/java/com/hfstudio/bqapi/api/package-info.java](src/main/java/com/hfstudio/bqapi/api/package-info.java)
- `owner = bqapi`
- `provides = bqapi|api`

This allows FML to expose the API as an API container for dependency/optional lookups.

### Jar-in-Jar Packaging

When packaging your own mod:

- Include the `api` jar as your embedded library (jar-in-jar), not the full mod bootstrap jar.
- Keep BetterQuestingAPI main mod jar in modpacks/servers that need runtime bootstrap and mixin loading.

Practical rule:

- `*-api.jar`: for compile-time and embedding into dependent mods.
- Main BetterQuestingAPI mod jar: for runtime bootstrap (`@Mod`) and mixins.

## Mixin Safety Notes

The mixin selection logic now supports both "loaded mod" and "declared API" checks:

- Implementation: [src/main/java/com/hfstudio/bqapi/mixins/Mixins.java](src/main/java/com/hfstudio/bqapi/mixins/Mixins.java)
- Condition: `loadedMods.contains(modId) || ModAPIManager.INSTANCE.hasAPI(modId)`

This guarantees API-aware matching in this project. External libraries (for example GTNHLib) will only be API-aware if their own checks also query `ModAPIManager`.

Do not remove these from the normal mod jar:

- [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json)
- [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)
- [src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java](src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java)

The API-only jar intentionally excludes them.

## Verification Checklist

After changing build/publishing setup:

1. Run `gradlew.bat clean build`.
2. Confirm `build/libs` contains an additional `*-api.jar`.
3. Inspect normal mod jar and verify `BQApiMod` and mixin resources are still present.
4. Inspect `*-api.jar` and verify `BQApiMod` and mixin package/resources are absent.
5. In a downstream test mod, depend on the `:api` artifact and verify compile + jar-in-jar packaging.

---

## Downstream Migration Guide: From Embedded Sources to API Jar Dependency

This section is for downstream mods that have **copied all BetterQuestingAPI source files directly into their own project**. Follow these steps to delete those redundant copies and instead depend on the official API jar.

### Background: "Jar-in-Jar" and Minecraft 1.7.10

> GTNH Convention on 1.7.10 uses RetroFuturaGradle. The equivalent "embed" mechanism is **Shadow (class merging)**.

Two migration methods are described below — choose the one that fits your setup.

---

### Method A: Shadow Embed (for mods that want no separate BetterQuestingAPI runtime jar)

This method merges the API jar's classes directly into your mod jar. **No separate BetterQuestingAPI main mod jar is needed at runtime.** However, your mod must retain its own mixin injection (see step 4).

#### Step 1: Build and publish the API jar from BetterQuestingAPI

In the BetterQuestingAPI project directory, run:

```
gradlew.bat clean build
```

This produces two files under `build/libs/`:

```
BetterQuestingAPI-<version>.jar       ← Main mod jar (includes BQApiMod + mixins)
BetterQuestingAPI-<version>-api.jar   ← API-only jar (excludes BQApiMod + mixins)
```

To publish via JitPack, update `jitpack.yml` with an explicit `install` step:

```yaml
before_install:
  - ./gradlew setupCIWorkspace
install:
  - ./gradlew clean build
```

JitPack will then publish the `api` classifier artifact. Dependency coordinate:

```
com.github.<username>:BetterQuestingAPI:<version>:api
```

#### Step 2: Delete the embedded BetterQuestingAPI source files

Remove this directory from your downstream project:

```
src/main/java/com/hfstudio/bqapi/
```

> This includes `BQApi.java`, `BQApiMod.java`, `BQApiLangKeys.java`, `Tags.java`, and all
> `api/builder/`, `api/definition/`, `api/importer/`, `runtime/` contents.

Also delete `mixins.bqapi.json` / `mixins.bqapi.late.json` from `resources` if present.

#### Step 3: Enable shadow in `gradle.properties`

Add (or confirm) the following in your `gradle.properties`:

```properties
usesShadowedDependencies = true
```

#### Step 4: Add the dependency in `dependencies.gradle`

```groovy
// Embed API classes into your mod jar (shadow merge)
shadowCompile("com.github.<username>:BetterQuestingAPI:<version>:api") {
    transitive = false
}
// Dev runtime (optional: for local testing with BetterQuesting main mod)
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### Step 5: Keep your mod's own mixin injection

Because the shadow method does not bundle the BetterQuestingAPI main mod's mixin, your mod **must retain its own mixin injection**, for example:

```java
// Keep com.<yourmod>.mixins.late.BetterQuesting.MixinQuestCommandDefaults
// It injects into QuestCommandDefaults.load() and loadLegacy()
// BQReinjector is available because it was shadow-merged from the api jar
```

Keep the corresponding enum entry in `Mixins.java` unchanged:

```java
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

---

### Method B: External Mod Dependency (recommended for modpack distribution — simplest)

This method embeds no classes. The downstream mod references the API jar at compile time; the BetterQuestingAPI main mod jar is provided by the modpack at runtime, and mixin injection is handled entirely by it.

#### Step 1: Delete the embedded source files (same as Method A Step 2)

Remove `src/main/java/com/hfstudio/bqapi/` entirely.

#### Step 2: Add compile dependency in `dependencies.gradle`

```groovy
// Compile-only reference (main mod jar provided by modpack at runtime)
compileOnly("com.github.<username>:BetterQuestingAPI:<version>:api") {
    transitive = false
}
// Dev environment runtime (includes main mod jar — provides mixin + init logic)
devOnlyNonPublishable("com.github.<username>:BetterQuestingAPI:<version>")
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### Step 3: Delete the redundant mixin from your mod

Because the BetterQuestingAPI main mod jar at runtime already includes the complete `MixinQuestCommandDefaults` (injecting both `load` and `loadLegacy`), your mod **no longer needs its own copy**.

Delete:
```
src/main/java/<yourpkg>/mixins/late/BetterQuesting/MixinQuestCommandDefaults.java
```

And remove the corresponding enum entry from `Mixins.java`:

```java
// Remove this entire block
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

#### Step 4: Declare dependency in the `@Mod` annotation

Add `bqapi` to the `dependencies` field of your mod's main class:

```java
@Mod(
    modid = "yourmodid",
    // ...
    dependencies = "required-after:bqapi"
)
```

This ensures FML loads BetterQuestingAPI before your mod.

---

### API Initialization Notes

BetterQuestingAPI's initialization (including the `@Mod.EventHandler onServerStarted` reinject trigger) **only runs when the main mod jar's `BQApiMod` is loaded**.

| Method | API classes available | Mixin active | `BQApiMod` present |
|--------|-----------------------|--------------|---------------------|
| Shadow embed | ✅ | ✅ (your mod's own mixin) | ❌ (your mixin handles reinject) |
| External mod dep | ✅ | ✅ (managed by BetterQuestingAPI) | ✅ |

> With shadow embed, the reinject trigger depends on your mod's mixin (which injects `QuestCommandDefaults.load` / `loadLegacy`). BetterQuestingAPI's own event subscription will not run.
> With external mod dependency, everything is delegated to BetterQuestingAPI — no additional setup needed.

---

### Multi-Mod Coexistence Compatibility

This section answers the question: **what happens when the main BetterQuestingAPI jar is present in the modpack AND one or more mods have shadow-embedded the api jar? Or when multiple mods all shadow-embed the api jar?**

#### Class Loading (No Conflict)

Minecraft 1.7.10 uses a single `LaunchClassLoader` for all mods. When multiple jars contain the same class name (e.g. `com.hfstudio.bqapi.BQApi`), the classloader loads **whichever copy it finds first** and ignores the rest.

This means:
- There is always exactly **one** `BQApi` class and one `BQApi.REGISTRY` static field in the JVM.
- Registrations from all mods accumulate in the same registry — correct behavior. ✅
- `BQApiMod` only exists in the main mod jar (shadow embed excludes it), so it initialises exactly once. ✅

> **Version mismatch risk**: If mod A shadows version 1.0.0 and mod B shadows version 1.1.0, whichever loads first wins. The other mod silently uses the older API. Always keep all shadow copies on the same version.

#### Double Mixin Injection

If the main BetterQuestingAPI jar is present AND a shadow-embedding mod still registers its own `MixinQuestCommandDefaults`, **both mixins are applied to `QuestCommandDefaults.load()` / `loadLegacy()`**. This means `BQReinjector.reinject()` is called twice in rapid succession.

As of the current implementation `BQReinjector` includes a **50 ms debounce**: any second call arriving within 50 ms of the first is silently dropped. The apply logic (`ChapterApplier` / `QuestApplier`) is also idempotent (UUID-keyed get-or-create + overwrite), so even if the debounce were absent the result would be the same data written twice.

Practical result:

| Scenario | Class conflict | Double reinject | Functional result |
|----------|---------------|-----------------|-------------------|
| Main jar + one shadow mod | ❌ (classloader deduplicates) | Debounced away | ✅ Correct |
| Main jar + two shadow mods | ❌ | Debounced away | ✅ Correct |
| Two shadow mods, no main jar | ❌ | Debounced away | ✅ Correct |
| Version mismatch between jars | ❌ classes, but wrong version | — | ⚠️ Unpredictable |

#### Recommendation

For the cleanest setup in a modpack that ships BetterQuestingAPI as a standalone mod:
- Use **Method B (external mod dependency)** for all downstream mods.
- Remove each mod's own `MixinQuestCommandDefaults` copy; the main mod jar handles it.
- There will be exactly one mixin injection per method and one `reinject` call. No debounce needed.

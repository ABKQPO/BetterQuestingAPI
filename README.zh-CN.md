# BetterQuestingAPI

English README: [README.md](README.md)

BetterQuestingAPI 是一个面向 Minecraft Forge 1.7.10 的运行时 API 模组，用于通过代码或资源导入的方式注册并应用 BetterQuesting 的章节、任务、条件与奖励。

## 项目提供能力

- 稳定的 Java API（定义与注册任务线）。
- 运行时应用管线（把注册内容注入 BetterQuesting）。
- 可选导入流程（从资源目录导入章节/任务线）。
- 与运行时行为相关的 Mixin 支持。

核心入口：

- 模组入口: [src/main/java/com/hfstudio/bqapi/BQApiMod.java](src/main/java/com/hfstudio/bqapi/BQApiMod.java)
- 对外门面: [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java)
- Builder 集合: [src/main/java/com/hfstudio/bqapi/api/builder](src/main/java/com/hfstudio/bqapi/api/builder)
- Definition 模型: [src/main/java/com/hfstudio/bqapi/api/definition](src/main/java/com/hfstudio/bqapi/api/definition)
- 导入器 API: [src/main/java/com/hfstudio/bqapi/api/importer](src/main/java/com/hfstudio/bqapi/api/importer)
- 运行时应用: [src/main/java/com/hfstudio/bqapi/runtime](src/main/java/com/hfstudio/bqapi/runtime)
- Mixin 加载与配置: [src/main/java/com/hfstudio/bqapi/mixins](src/main/java/com/hfstudio/bqapi/mixins), [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json), [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)

## 构建命令

Windows:

```bash
gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

测试:

```bash
gradlew.bat test
```

## 构建产物说明

现在 `build` 会在保留原有模组产物的前提下，额外生成一个带 `api` 分类器的纯 API jar。

预期产物包括：

- 主模组 jar（包含模组主类与 mixin 元数据）。
- GTNH 约定生成的 dev/sources 等产物。
- 额外 API jar：`*-api.jar`。

`*-api.jar` 会刻意排除：

- 模组主类：`com.hfstudio.bqapi.BQApiMod`。
- Mixin 实现包：`com.hfstudio.bqapi.mixins`。
- Mixin 配置资源：`mixins.bqapi.json`、`mixins.bqapi.late.json`。

这样可以保证主模组 jar 的初始化链路与 mixin 生效路径不变，同时给其他模组提供更轻量的编译/jar-in-jar API 依赖。

## API 使用教程

### 1. 用代码注册任务线

主要入口是 [src/main/java/com/hfstudio/bqapi/BQApi.java](src/main/java/com/hfstudio/bqapi/BQApi.java)。

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

推荐时机：

- 在你的模组初始化阶段完成章节/任务注册。
- 如果在服务器启动后才动态注册，需手动调用 `BQApi.reinject(server)`。

### 2. 从资源目录导入任务线

可以直接导入你模组资源目录中的任务定义。

```java
import com.hfstudio.bqapi.BQApi;

public final class ExampleImportRegistration {

    public static void registerImported() {
        BQApi.registerImportedFolder("yourmodid", "bqapi/quests");
    }
}
```

这会从以下路径解析资源：

- `assets/yourmodid/bqapi/quests`

更高级的导入控制（自定义 classloader、行目录、UUID 策略）见：

- [src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java](src/main/java/com/hfstudio/bqapi/api/importer/ImportedQuestFolders.java)
- [src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java](src/main/java/com/hfstudio/bqapi/api/builder/ImportedChapterBuilder.java)

### 3. 运行时重注入

默认情况下，服务器启动后的重注入由 [src/main/java/com/hfstudio/bqapi/BQApiMod.java](src/main/java/com/hfstudio/bqapi/BQApiMod.java) 自动处理。

如果你的模组在运行过程中动态变更了定义，可手动触发：

```java
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;
import com.hfstudio.bqapi.BQApi;

MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
if (server != null) {
    BQApi.reinject(server);
}
```

### 4. 查询已注册的章节与任务

注册完成后，可随时按 ID 或 UUID 查询定义：

```java
import java.util.Optional;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;

// 判断是否存在
boolean exists = BQApi.hasQuest("mymod.chapter1.intro");

// 按字符串 ID 获取
Optional<QuestDefinition> quest = BQApi.getQuest("mymod.chapter1.intro");
quest.ifPresent(q -> System.out.println("UUID: " + q.getUuid()));

// 按 UUID 获取章节
Optional<ChapterDefinition> chapter = BQApi.getChapter("mymod.chapter1");
```

### 5. 运行时任务 Patch

在启动阶段（如 `FMLPostInitializationEvent`）注册一次 patch，每次 `reinject` 时自动应用，不修改原始注册数据：

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.TaskBuilders;

// 在其他模组的任务上追加一个物品收集任务
BQApi.patchQuest("othermod.some_quest", def ->
    Quests.copyOf(def)
        .task(TaskBuilders.retrieval()
            .item("minecraft:diamond", 1)
            .build())
        .build());
```

同一任务上的多个 patch 按注册顺序依次应用。

### 6. 动态重载：Java SPI（推荐）

BetterQuestingAPI 支持 JDK SPI（`ServiceLoader`）作为重载回调机制。
这样就不需要每个使用方都额外绑定一个专门的启动类。

#### 6.1 创建服务实现类

```java
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.QuestReloadService;
import cpw.mods.fml.common.Loader;

public final class MyQuestReloadService implements QuestReloadService {

    @Override
    public void reloadQuest() {
        if (Loader.isModLoaded("somemod")) {
            BQApi.register(buildCompatChapter());
        }
    }
}
```

#### 6.2 添加 SPI 描述文件

创建文件：

- `src/main/resources/META-INF/services/com.hfstudio.bqapi.api.QuestReloadService`

文件内容（每行一个实现类全限定名）：

```text
your.mod.package.MyQuestReloadService
```

`BQApi.reinject(...)` 会在应用定义前自动发现并调用这些 SPI 服务。

#### 6.3 兼容方式：`@QuestReloader`

注解注册方式仍然可用：

```java
BQApi.registerReloader(MyQuestSetup.class);
```

仅在 SPI 方式不方便时使用。

> **注意**：不要在重载回调中调用 `BQApi.patchQuest()`。patch 是持久累积的，
> 在此处重复注册会导致每次重载都叠加一份。patch 应在启动时注册一次。

将分类器为 `api` 的产物作为你的编译/jar-in-jar 依赖。

### Gradle 依赖（模板）

使用该项目实际发布坐标，并指定 `api` 分类器。

```gradle
dependencies {
    // 将 <group>:<artifact>:<version> 替换为实际发布坐标
    implementation("<group>:<artifact>:<version>:api")
}
```

如果你的构建链使用 GTNH 的 deobf 包装器，请在同一 `:api` 坐标外层套用你现有的 deobf 写法。

### Forge API 注解标识

项目已在 API 包声明 Forge `@API` 注解，提供的 API 标识为 `bqapi|api`：

- 注解位置: [src/main/java/com/hfstudio/bqapi/api/package-info.java](src/main/java/com/hfstudio/bqapi/api/package-info.java)
- `owner = bqapi`
- `provides = bqapi|api`

这使得 FML 可将其识别为 API 容器，供依赖/可选逻辑查询。

### Jar-in-Jar 打包建议

打包你自己的模组时：

- 嵌入 `*-api.jar` 作为 jar-in-jar 依赖，而不是嵌入完整主模组 jar。
- 需要运行时启动逻辑与 mixin 时，整合包/服务器仍应提供 BetterQuestingAPI 主模组 jar。

实务规则：

- `*-api.jar`：用于编译与嵌入式库。
- BetterQuestingAPI 主模组 jar：用于 `@Mod` 启动与 mixin 加载。

## Mixin 安全说明

本项目的 mixin 选择逻辑已支持“模组已加载或 API 已声明”双判定：

- 实现位置: [src/main/java/com/hfstudio/bqapi/mixins/Mixins.java](src/main/java/com/hfstudio/bqapi/mixins/Mixins.java)
- 判定逻辑: `loadedMods.contains(modId) || ModAPIManager.INSTANCE.hasAPI(modId)`

注意：这保证了本项目自己的 mixin 选择可识别 API 容器；外部库（例如 GTNHLib）是否把 API 当作目标，仍取决于它们各自实现是否也调用了 `ModAPIManager`。

下列内容必须保留在主模组 jar 中：

- [src/main/resources/mixins.bqapi.json](src/main/resources/mixins.bqapi.json)
- [src/main/resources/mixins.bqapi.late.json](src/main/resources/mixins.bqapi.late.json)
- [src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java](src/main/java/com/hfstudio/bqapi/mixins/LateMixinLoader.java)

纯 API jar 会故意排除它们。

## 验证清单

修改构建/发布逻辑后建议执行：

1. 运行 `gradlew.bat clean build`。
2. 检查 `build/libs` 是否新增 `*-api.jar`。
3. 检查主模组 jar，确认仍包含 `BQApiMod` 与 mixin 资源。
4. 检查 `*-api.jar`，确认不含 `BQApiMod` 与 mixin 包/资源。
5. 在下游测试模组中依赖 `:api` 产物，验证编译与 jar-in-jar 打包通过。

---

## 下游项目迁移教程：从内嵌源码迁移到 API jar 依赖

本节适用于**已将 BetterQuestingAPI 全部源文件直接复制进自己项目**的下游模组。以下步骤说明如何删除这些冗余副本，改为正式依赖 BetterQuestingAPI 提供的 API jar。

### 背景：关于"jar-in-jar"与 1.7.10 的说明

> 1.7.10 的 GTNH Convention 使用 RetroFuturaGradle，其等价"嵌入"方案是 **Shadow（类合并）**。

下面介绍两种迁移方案，根据实际需求选择其一。

---

### 方案一：Shadow 嵌入（推荐用于不想额外分发 BetterQuestingAPI 的情况）

本方案将 API jar 的类直接合并进下游模组的 jar，**运行时无需额外安装 BetterQuestingAPI 主模组 jar**。但下游模组需要自行持有一个对 BetterQuesting 的 mixin 注入（见步骤 4）。

#### 步骤 1：在 BetterQuestingAPI 侧构建并发布 api jar

在 BetterQuestingAPI 项目根目录执行：

```
gradlew.bat clean build
```

构建成功后，`build/libs/` 会生成如下两个文件：

```
BetterQuestingAPI-<version>.jar       ← 主模组 jar（含 BQApiMod + mixin）
BetterQuestingAPI-<version>-api.jar   ← 纯 API jar（不含 BQApiMod + mixin）
```

如需通过 JitPack 发布，将 `jitpack.yml` 的 `before_install` 改为：

```yaml
before_install:
  - ./gradlew setupCIWorkspace
install:
  - ./gradlew clean build
```

JitPack 会自动发布带 `api` classifier 的产物，依赖坐标示例：

```
com.github.<用户名>:BetterQuestingAPI:<version>:api
```

#### 步骤 2：删除内嵌的 BetterQuestingAPI 源文件

在下游项目中，删除以下目录（如果存在）：

```
src/main/java/com/hfstudio/bqapi/
```

> 这包括 `BQApi.java`、`BQApiMod.java`、`BQApiLangKeys.java`、`Tags.java`，以及
> `api/builder/`、`api/definition/`、`api/importer/`、`runtime/` 的全部内容。

如果下游项目在 `resources` 下还有 `mixins.bqapi.json` / `mixins.bqapi.late.json`，也一并删除。

#### 步骤 3：在 `gradle.properties` 启用 shadow

打开下游项目的 `gradle.properties`，确认存在：

```properties
usesShadowedDependencies = true
```

若无此行则添加（GTNH Convention 默认支持此选项）。

#### 步骤 4：在 `dependencies.gradle` 添加依赖

```groovy
// 将 API 类嵌入到本模组 jar（shadow 合并）
shadowCompile("com.github.<用户名>:BetterQuestingAPI:<version>:api") {
    // 防止传递依赖意外携带非必要内容
    transitive = false
}
// 用于开发时运行（可选：本地测试需要 BetterQuesting 主模组）
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### 步骤 5：保留下游模组自己的 mixin 注入

由于 Shadow 方案不打包 BetterQuestingAPI 主模组的 mixin，下游模组**必须保留自己的 mixin 注入代码**，例如：

```java
// 保留 com.science.gtnl.mixins.late.BetterQuesting.MixinQuestCommandDefaults
// 它注入了 QuestCommandDefaults.load() 和 loadLegacy() 两个位置
// BQReinjector 已由 Shadow 合并进本模组，无需额外 import
```

`Mixins.java` 中对应枚举项**保持不变**：

```java
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

---

### 方案二：外部模组依赖（推荐用于整合包场景，结构最简洁）

本方案不嵌入任何类，下游模组在**编译时**引用 API jar，BetterQuestingAPI 主模组 jar 在**运行时**由整合包提供，mixin 注入也由主模组 jar 完成。

#### 步骤 1：删除内嵌源文件（同方案一步骤 2）

删除下游项目中 `src/main/java/com/hfstudio/bqapi/` 整个目录。

#### 步骤 2：在 `dependencies.gradle` 添加编译依赖

```groovy
// 仅用于编译（运行时由整合包提供主模组 jar）
compileOnly("com.github.<用户名>:BetterQuestingAPI:<version>:api") {
    transitive = false
}
// 开发环境运行时（包含主模组 jar，提供 mixin 和初始化逻辑）
devOnlyNonPublishable("com.github.<用户名>:BetterQuestingAPI:<version>")
devOnlyNonPublishable("com.github.GTNewHorizons:BetterQuesting:3.x.x-GTNH:dev")
```

#### 步骤 3：删除下游模组中的冗余 mixin

由于 BetterQuestingAPI 主模组 jar 运行时已包含完整 mixin（`MixinQuestCommandDefaults`，注入 `load` 与 `loadLegacy`），下游模组**不再需要**自己的同名 mixin。

删除：
```
src/main/java/<下游包>/mixins/late/BetterQuesting/MixinQuestCommandDefaults.java
```

同时从 `Mixins.java` 中删除对应枚举项：

```java
// 删除以下整段
BETTER_QUESTING_RUNTIME(new MixinBuilder("BetterQuesting Runtime API Mixins")
    .addCommonMixins("BetterQuesting.MixinQuestCommandDefaults")
    .setPhase(Phase.LATE)
    .addRequiredMod(ModList.BetterQuesting)),
```

#### 步骤 4：在 `@Mod` 的 `dependencies` 字段声明依赖

打开下游模组的主类（`@Mod` 注解），在 `dependencies` 中添加：

```java
@Mod(
    modid = "yourmodid",
    // ...
    dependencies = "required-after:bqapi"
)
```

这会让 FML 在加载顺序上保证 BetterQuestingAPI 先于下游模组初始化。

---

### 关于 API 初始化的注意事项

BetterQuestingAPI 的初始化（包括 `@Mod.EventHandler onServerStarted` 中触发的 reinject 流程）**只在主模组 jar 的 `BQApiMod` 被加载时才会执行**。

| 方案 | API 类是否可用 | Mixin 是否生效 | `BQApiMod` 是否存在 |
|------|--------------|--------------|------------------|
| Shadow 嵌入 | ✅ | ✅（下游自有 mixin） | ❌（需下游自行触发 reinject） |
| 外部模组依赖 | ✅ | ✅（BetterQuestingAPI 主模组管理） | ✅ |

> 使用 Shadow 方案时，reinject 的触发依赖下游模组的 mixin（注入 `QuestCommandDefaults.load` / `loadLegacy`），BetterQuestingAPI 的主模组事件订阅不会执行。外部模组依赖方案则完全委托给 BetterQuestingAPI 主模组管理，无需关心。

---

### 多模组共存兼容性

本节回答：**整合包自带 BetterQuestingAPI 主模组 jar，同时有一个或多个模组 Shadow 内嵌了 api jar，会不会出问题？两个模组都 Shadow 内嵌了 api jar，又会怎样？**

#### 类加载不冲突

Minecraft 1.7.10 所有模组共用同一个 `LaunchClassLoader`。当多个 jar 包含相同类名（如 `com.hfstudio.bqapi.BQApi`）时，类加载器**只加载第一个遇到的副本**，后续同名类被忽略。

因此：
- JVM 中始终只有**一个** `BQApi` 类和一个 `BQApi.REGISTRY` 静态注册表。
- 所有模组的注册内容都写入同一个注册表 — 行为正确。✅
- `BQApiMod` 仅存在于主模组 jar（Shadow 方案排除了它），因此只初始化一次。✅

> **版本不一致风险**：若模组 A Shadow 的是 1.0.0、模组 B Shadow 的是 1.1.0，先加载的副本获胜，另一个模组静默使用旧版 API。请确保所有 Shadow 副本版本一致。

#### Mixin 双重注入

若主模组 jar 已存在，同时某个 Shadow 模组仍注册了自己的 `MixinQuestCommandDefaults`，则 `QuestCommandDefaults.load()` / `loadLegacy()` 会被**两个 mixin 各注入一次**，导致 `BQReinjector.reinject()` 在极短时间内被调用两次。

当前实现在 `BQReinjector` 中内置了 **50 ms 去抖（debounce）**：第二次调用如果距第一次不足 50 ms，直接跳过。此外，apply 逻辑（`ChapterApplier` / `QuestApplier`）本身也是幂等的（UUID 键 get-or-create + 覆盖写），即便去抖不存在，双重执行的结果也与单次相同。

实际结论：

| 场景 | 类冲突 | reinject 重复调用 | 功能结果 |
|------|--------|-----------------|----------|
| 主模组 jar + 一个 Shadow 模组 | ❌（类加载器去重） | 去抖消除 | ✅ 正常 |
| 主模组 jar + 两个 Shadow 模组 | ❌ | 去抖消除 | ✅ 正常 |
| 两个 Shadow 模组，无主模组 jar | ❌ | 去抖消除 | ✅ 正常 |
| 多个 jar 版本不一致 | ❌ 类层面，但版本错误 | — | ⚠️ 不可预期 |

#### 建议

整合包若将 BetterQuestingAPI 作为独立模组分发：
- 所有下游模组均使用**方案二（外部模组依赖）**。
- 各模组删除自己的 `MixinQuestCommandDefaults` 副本，由主模组 jar 统一管理。
- 每个方法只有一次 mixin 注入、一次 `reinject` 调用，最简洁。

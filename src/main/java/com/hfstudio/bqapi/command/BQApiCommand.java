package com.hfstudio.bqapi.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;
import com.hfstudio.bqapi.api.definition.TaskDefinition;
import com.hfstudio.bqapi.runtime.BQRegistrationInfo;
import com.hfstudio.bqapi.runtime.BQReinjector;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * <b>/bqapi</b> debug command.
 *
 * <p>
 * Available sub-commands:
 * </p>
 * 
 * <pre>
 *   /bqapi info                       — Show registration statistics overview
 *   /bqapi list [page]                — List all registered chapters
 *   /bqapi quest &lt;id&gt;                 — Show quest details (string-id or UUID)
 *   /bqapi quest &lt;id&gt; stack           — Show the registration call stack of the chapter containing the quest
 *   /bqapi quest &lt;id&gt; prereqs         — List prerequisite quests
 *   /bqapi quest &lt;id&gt; patches         — List registered runtime patch sources
 *   /bqapi chapter &lt;id&gt;              — Show chapter details (string-id or UUID)
 *   /bqapi chapter &lt;id&gt; stack         — Show the chapter registration call stack
 *   /bqapi chapter &lt;id&gt; quests [page] — List all quests in the chapter
 *   /bqapi source &lt;id&gt;               — Auto-detect chapter/quest and show registration source
 *   /bqapi search &lt;keyword&gt;          — Search for keyword in registered IDs
 *   /bqapi reload                     — Trigger reinject (requires OP)
 * </pre>
 */
public final class BQApiCommand extends CommandBase {

    private static final int PAGE_SIZE = 8;

    // ── Color aliases ─────────────────────────────────────────────────────────
    private static final EnumChatFormatting H = EnumChatFormatting.GOLD; // heading
    private static final EnumChatFormatting K = EnumChatFormatting.AQUA; // key
    private static final EnumChatFormatting V = EnumChatFormatting.WHITE; // value
    private static final EnumChatFormatting G = EnumChatFormatting.GRAY; // note
    private static final EnumChatFormatting S = EnumChatFormatting.GREEN; // source
    private static final EnumChatFormatting W = EnumChatFormatting.YELLOW; // warning / secondary
    private static final EnumChatFormatting E = EnumChatFormatting.RED; // error

    // ═══════════════════════════════════════════════════════════════════════════
    // CommandBase overrides
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String getCommandName() {
        return "bqapi";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bqapi <info|list|quest|chapter|source|search|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "info",
                "list",
                "quest",
                "chapter",
                "source",
                "search",
                "reload");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("quest".equals(sub) || "source".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, collectQuestIds());
            }
            if ("chapter".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, collectChapterIds());
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("quest".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, "stack", "prereqs", "patches");
            }
            if ("chapter".equals(sub)) {
                return getListOfStringsMatchingLastWord(args, "stack", "quests");
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, W, "用法: " + getCommandUsage(sender));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info":
                doInfo(sender);
                break;
            case "list":
                doList(sender, args);
                break;
            case "quest":
                doQuest(sender, args);
                break;
            case "chapter":
                doChapter(sender, args);
                break;
            case "source":
                doSource(sender, args);
                break;
            case "search":
                doSearch(sender, args);
                break;
            case "reload":
                doReload(sender);
                break;
            default:
                msg(sender, E, "未知子命令: " + sub);
                msg(sender, W, "用法: " + getCommandUsage(sender));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi info
    // ═══════════════════════════════════════════════════════════════════════════

    private void doInfo(ICommandSender sender) {
        int chapters = BQApi.getRegisteredChapterUuids()
            .size();
        int quests = BQApi.getRegisteredQuestUuids()
            .size();
        int patched = BQApi.getPatchedQuestCount();
        line(sender);
        msg(sender, H, "=== BQApi 调试概况 ===");
        kv(sender, "已注册章节", String.valueOf(chapters));
        kv(sender, "已注册任务", String.valueOf(quests));
        kv(sender, "有 Patch 的任务", patched > 0 ? String.valueOf(patched) : G + "0");
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi list [page]
    // ═══════════════════════════════════════════════════════════════════════════

    private void doList(ICommandSender sender, String[] args) {
        Collection<ChapterDefinition> all = BQApi.getRegisteredChapters();
        List<ChapterDefinition> list = new ArrayList<>(all);
        if (list.isEmpty()) {
            msg(sender, W, "当前没有已注册的章节。");
            return;
        }
        int page = parsePageArg(args, 2, list.size());
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, list.size());
        int totalPages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;

        line(sender);
        msg(sender, H, "=== 已注册章节 (" + list.size() + " 个，第 " + page + "/" + totalPages + " 页) ===");
        for (int i = from; i < to; i++) {
            ChapterDefinition ch = list.get(i);
            Optional<BQRegistrationInfo> info = BQApi.getChapterRegistrationInfo(ch.getId());
            String src = info
                .map(
                    r -> shortClass(r.getRegistrantClass()) + "."
                        + r.getRegistrantMethod()
                        + (r.getRegistrantLine() > 0 ? ":" + r.getRegistrantLine() : ""))
                .orElse("?");
            String modId = info.map(BQRegistrationInfo::getModId)
                .orElse("?");
            msg(
                sender,
                G + "["
                    + (i + 1)
                    + "] "
                    + K
                    + ch.getId()
                    + G
                    + "  quests="
                    + V
                    + ch.getPlacements()
                        .size()
                    + G
                    + "  mod="
                    + W
                    + modId
                    + G
                    + "  src="
                    + S
                    + src);
        }
        if (totalPages > 1) {
            msg(sender, G, "输入 /bqapi list <页码> 翻页");
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi quest <id> [stack|prereqs|patches]
    // ═══════════════════════════════════════════════════════════════════════════

    private void doQuest(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, E, "用法: /bqapi quest <任务ID或UUID> [stack|prereqs|patches]");
            return;
        }
        String idArg = args[1];
        Optional<QuestDefinition> questOpt = resolveQuest(idArg);
        if (!questOpt.isPresent()) {
            msg(sender, E, "找不到任务: " + idArg);
            return;
        }
        QuestDefinition quest = questOpt.get();
        String modifier = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";

        switch (modifier) {
            case "stack":
                doQuestStack(sender, quest);
                break;
            case "prereqs":
                doQuestPrereqs(sender, quest);
                break;
            case "patches":
                doQuestPatches(sender, quest);
                break;
            default:
                doQuestDetail(sender, quest);
        }
    }

    private void doQuestDetail(ICommandSender sender, QuestDefinition quest) {
        Optional<BQRegistrationInfo> infoOpt = BQApi.getQuestRegistrationInfo(quest.getUuid());
        boolean hasPatches = !BQApi.getQuestPatchRegistrationInfos(quest.getUuid())
            .isEmpty();

        // Find the owning chapter.
        String chapterId = resolveQuestChapterId(quest.getUuid());

        line(sender);
        msg(sender, H, "=== 任务详情 ===");
        kv(sender, "ID", quest.getId());
        kv(
            sender,
            "UUID",
            quest.getUuid()
                .toString());
        kv(sender, "所属章节", chapterId);
        kv(
            sender,
            "任务类型数",
            String.valueOf(
                quest.getTasks()
                    .size()));
        if (!quest.getTasks()
            .isEmpty()) {
            StringBuilder taskTypes = new StringBuilder();
            for (TaskDefinition t : quest.getTasks()) {
                if (taskTypes.length() > 0) taskTypes.append(", ");
                taskTypes.append(
                    t.getClass()
                        .getSimpleName());
            }
            kv(sender, "  Task 类型", taskTypes.toString());
        }
        kv(
            sender,
            "前置任务数",
            String.valueOf(
                quest.getPrerequisites()
                    .size()));
        kv(sender, "主线任务", quest.isMain() ? "是" : "否");
        kv(sender, "有 Patch", hasPatches ? (W + "是 (输入 patches 子命令查看)") : "否");
        if (infoOpt.isPresent()) {
            BQRegistrationInfo info = infoOpt.get();
            kv(sender, "注册模组", info.getModId());
            kv(
                sender,
                "注册来源",
                shortClass(info.getRegistrantClass()) + "."
                    + info.getRegistrantMethod()
                    + (info.getRegistrantLine() > 0 ? ":" + info.getRegistrantLine() : ""));
            msg(sender, G, "  (输入 stack 子命令查看完整调用堆栈)");
        } else {
            kv(sender, "注册来源", G + "未知");
        }
        line(sender);
    }

    private void doQuestStack(ICommandSender sender, QuestDefinition quest) {
        Optional<BQRegistrationInfo> infoOpt = BQApi.getQuestRegistrationInfo(quest.getUuid());
        if (!infoOpt.isPresent()) {
            msg(sender, W, "任务 " + quest.getId() + " 没有注册来源信息。");
            return;
        }
        printStack(sender, "任务 " + quest.getId() + " 的注册堆栈", infoOpt.get());
    }

    private void doQuestPrereqs(ICommandSender sender, QuestDefinition quest) {
        List<UUID> prereqs = quest.getPrerequisites();
        line(sender);
        msg(sender, H, "=== 任务 " + quest.getId() + " 的前置任务 (" + prereqs.size() + " 个) ===");
        if (prereqs.isEmpty()) {
            msg(sender, G, "  (无前置任务)");
        } else {
            for (UUID prereqUuid : prereqs) {
                Optional<QuestDefinition> prereqOpt = BQApi.getQuest(prereqUuid);
                String prereqId = prereqOpt.map(QuestDefinition::getId)
                    .orElse(G + "(未注册)");
                msg(sender, G + "  · " + K + prereqId + G + "  UUID=" + prereqUuid);
            }
        }
        line(sender);
    }

    private void doQuestPatches(ICommandSender sender, QuestDefinition quest) {
        List<BQRegistrationInfo> patches = BQApi.getQuestPatchRegistrationInfos(quest.getUuid());
        line(sender);
        msg(sender, H, "=== 任务 " + quest.getId() + " 的运行时 Patch (" + patches.size() + " 个) ===");
        if (patches.isEmpty()) {
            msg(sender, G, "  (无 Patch)");
        } else {
            for (int i = 0; i < patches.size(); i++) {
                BQRegistrationInfo p = patches.get(i);
                msg(
                    sender,
                    G + "  ["
                        + (i + 1)
                        + "] "
                        + W
                        + p.getModId()
                        + G
                        + "  "
                        + S
                        + shortClass(p.getRegistrantClass())
                        + "."
                        + p.getRegistrantMethod()
                        + (p.getRegistrantLine() > 0 ? ":" + p.getRegistrantLine() : ""));
            }
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi chapter <id> [stack|quests [page]]
    // ═══════════════════════════════════════════════════════════════════════════

    private void doChapter(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, E, "用法: /bqapi chapter <章节ID或UUID> [stack|quests]");
            return;
        }
        String idArg = args[1];
        Optional<ChapterDefinition> chapterOpt = resolveChapter(idArg);
        if (!chapterOpt.isPresent()) {
            msg(sender, E, "找不到章节: " + idArg);
            return;
        }
        ChapterDefinition chapter = chapterOpt.get();
        String modifier = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";

        switch (modifier) {
            case "stack":
                doChapterStack(sender, chapter);
                break;
            case "quests":
                doChapterQuests(sender, chapter, args);
                break;
            default:
                doChapterDetail(sender, chapter);
        }
    }

    private void doChapterDetail(ICommandSender sender, ChapterDefinition chapter) {
        Optional<BQRegistrationInfo> infoOpt = BQApi.getChapterRegistrationInfo(chapter.getId());
        line(sender);
        msg(sender, H, "=== 章节详情 ===");
        kv(sender, "ID", chapter.getId());
        kv(
            sender,
            "UUID",
            chapter.getUuid()
                .toString());
        kv(
            sender,
            "任务数",
            String.valueOf(
                chapter.getPlacements()
                    .size()));
        if (infoOpt.isPresent()) {
            BQRegistrationInfo info = infoOpt.get();
            kv(sender, "注册模组", info.getModId());
            kv(
                sender,
                "注册来源",
                shortClass(info.getRegistrantClass()) + "."
                    + info.getRegistrantMethod()
                    + (info.getRegistrantLine() > 0 ? ":" + info.getRegistrantLine() : ""));
            msg(sender, G, "  (输入 stack 子命令查看完整调用堆栈)");
        } else {
            kv(sender, "注册来源", G + "未知");
        }
        line(sender);
    }

    private void doChapterStack(ICommandSender sender, ChapterDefinition chapter) {
        Optional<BQRegistrationInfo> infoOpt = BQApi.getChapterRegistrationInfo(chapter.getId());
        if (!infoOpt.isPresent()) {
            msg(sender, W, "章节 " + chapter.getId() + " 没有注册来源信息。");
            return;
        }
        printStack(sender, "章节 " + chapter.getId() + " 的注册堆栈", infoOpt.get());
    }

    private void doChapterQuests(ICommandSender sender, ChapterDefinition chapter, String[] args) {
        List<QuestPlacementDefinition> placements = chapter.getPlacements();
        int page = parsePageArg(args, 3, placements.size());
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, placements.size());
        int totalPages = (placements.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (placements.isEmpty()) {
            msg(sender, W, "章节 " + chapter.getId() + " 中没有任务。");
            return;
        }
        line(sender);
        msg(
            sender,
            H,
            "=== 章节 " + chapter.getId() + " 的任务 (" + placements.size() + " 个，第 " + page + "/" + totalPages + " 页) ===");
        for (int i = from; i < to; i++) {
            QuestDefinition q = placements.get(i)
                .getQuest();
            boolean hasPatches = !BQApi.getQuestPatchRegistrationInfos(q.getUuid())
                .isEmpty();
            msg(
                sender,
                G + "["
                    + (i + 1)
                    + "] "
                    + K
                    + q.getId()
                    + G
                    + "  UUID="
                    + q.getUuid()
                    + (hasPatches ? W + "  [patched]" : ""));
        }
        if (totalPages > 1) {
            msg(sender, G, "输入 /bqapi chapter " + chapter.getId() + " quests <页码> 翻页");
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi source <id>
    // ═══════════════════════════════════════════════════════════════════════════

    private void doSource(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, E, "用法: /bqapi source <任务或章节的ID/UUID>");
            return;
        }
        String idArg = args[1];

        // Try quest first, then chapter.
        Optional<QuestDefinition> questOpt = resolveQuest(idArg);
        if (questOpt.isPresent()) {
            QuestDefinition quest = questOpt.get();
            Optional<BQRegistrationInfo> info = BQApi.getQuestRegistrationInfo(quest.getUuid());
            printSourceBrief(sender, "任务 " + quest.getId(), info.orElse(null));
            return;
        }

        Optional<ChapterDefinition> chapterOpt = resolveChapter(idArg);
        if (chapterOpt.isPresent()) {
            ChapterDefinition chapter = chapterOpt.get();
            Optional<BQRegistrationInfo> info = BQApi.getChapterRegistrationInfo(chapter.getId());
            printSourceBrief(sender, "章节 " + chapter.getId(), info.orElse(null));
            return;
        }

        msg(sender, E, "找不到任务或章节: " + idArg);
    }

    private void printSourceBrief(ICommandSender sender, String label, BQRegistrationInfo info) {
        line(sender);
        msg(sender, H, "=== " + label + " 的注册来源 ===");
        if (info == null) {
            msg(sender, G, "  (无来源信息)");
        } else {
            kv(sender, "模组", info.getModId());
            kv(sender, "类", info.getRegistrantClass());
            kv(
                sender,
                "方法",
                info.getRegistrantMethod() + (info.getRegistrantLine() > 0 ? ":" + info.getRegistrantLine() : ""));
            msg(sender, G, "  (输入 stack 子命令查看完整调用堆栈)");
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi search <keyword>
    // ═══════════════════════════════════════════════════════════════════════════

    private void doSearch(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, E, "用法: /bqapi search <关键词>");
            return;
        }
        String keyword = args[1].toLowerCase(Locale.ROOT);
        List<String> matchedChapters = new ArrayList<>();
        List<String> matchedQuests = new ArrayList<>();

        for (ChapterDefinition ch : BQApi.getRegisteredChapters()) {
            if (ch.getId()
                .toLowerCase(Locale.ROOT)
                .contains(keyword)) {
                matchedChapters.add(ch.getId());
            }
            for (QuestPlacementDefinition p : ch.getPlacements()) {
                QuestDefinition q = p.getQuest();
                if (q.getId()
                    .toLowerCase(Locale.ROOT)
                    .contains(keyword)) {
                    matchedQuests.add(q.getId());
                }
            }
        }

        line(sender);
        msg(sender, H, "=== 搜索「" + keyword + "」结果 ===");
        msg(sender, W, "章节 (" + matchedChapters.size() + " 个):");
        for (String id : matchedChapters) {
            msg(sender, G + "  · " + K + id);
        }
        msg(sender, W, "任务 (" + matchedQuests.size() + " 个):");
        int shown = 0;
        for (String id : matchedQuests) {
            if (shown >= PAGE_SIZE * 2) {
                msg(sender, G, "  ... 还有 " + (matchedQuests.size() - shown) + " 个，请缩小搜索范围。");
                break;
            }
            msg(sender, G + "  · " + K + id);
            shown++;
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /bqapi reload
    // ═══════════════════════════════════════════════════════════════════════════

    private void doReload(ICommandSender sender) {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            msg(sender, E, "服务器实例不可用，无法执行 reinject。");
            return;
        }
        BQReinjector.reinject(server);
        msg(sender, S, "BQApi reinject 已触发。");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers: stack printing
    // ═══════════════════════════════════════════════════════════════════════════

    private void printStack(ICommandSender sender, String title, BQRegistrationInfo info) {
        line(sender);
        msg(sender, H, "=== " + title + " ===");
        kv(sender, "模组", info.getModId());
        List<StackTraceElement> stack = info.getStack();
        if (stack.isEmpty()) {
            msg(sender, G, "  (无堆栈信息)");
        } else {
            for (int i = 0; i < stack.size(); i++) {
                StackTraceElement frame = stack.get(i);
                msg(
                    sender,
                    G + "  ["
                        + i
                        + "] "
                        + S
                        + shortClass(frame.getClassName())
                        + "."
                        + frame.getMethodName()
                        + G
                        + "("
                        + frame.getFileName()
                        + ":"
                        + frame.getLineNumber()
                        + ")");
            }
        }
        line(sender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers: ID resolution
    // ═══════════════════════════════════════════════════════════════════════════

    /** Looks up a quest by string-id or UUID string. */
    private static Optional<QuestDefinition> resolveQuest(String idArg) {
        // Try UUID first.
        UUID uuid = tryParseUuid(idArg);
        if (uuid != null) {
            Optional<QuestDefinition> byUuid = BQApi.getQuest(uuid);
            if (byUuid.isPresent()) return byUuid;
        }
        // Fall back to string-id.
        return BQApi.getQuest(idArg);
    }

    /** Looks up a chapter by string-id or UUID string. */
    private static Optional<ChapterDefinition> resolveChapter(String idArg) {
        UUID uuid = tryParseUuid(idArg);
        if (uuid != null) {
            Optional<ChapterDefinition> byUuid = BQApi.getChapter(uuid);
            if (byUuid.isPresent()) return byUuid;
        }
        return BQApi.getChapter(idArg);
    }

    /** Returns the chapter ID that contains the given quest UUID, or a placeholder if not found. */
    private static String resolveQuestChapterId(UUID questUuid) {
        // BQApi does not expose a direct quest→chapter lookup, so iterate all chapters.
        for (ChapterDefinition ch : BQApi.getRegisteredChapters()) {
            for (QuestPlacementDefinition p : ch.getPlacements()) {
                if (p.getQuest()
                    .getUuid()
                    .equals(questUuid)) {
                    return ch.getId();
                }
            }
        }
        return G + "(未知章节)";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers: formatting
    // ═══════════════════════════════════════════════════════════════════════════

    private void msg(ICommandSender sender, EnumChatFormatting color, String text) {
        sender.addChatMessage(new ChatComponentText(color + text));
    }

    private void msg(ICommandSender sender, String rawText) {
        sender.addChatMessage(new ChatComponentText(rawText));
    }

    private void kv(ICommandSender sender, String key, String value) {
        sender.addChatMessage(new ChatComponentText(K + "  " + key + ": " + V + value));
    }

    private void line(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(G + "---"));
    }

    /** Shortens a fully-qualified class name to its simple (last-segment) name. */
    private static String shortClass(String fullName) {
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }

    private static UUID tryParseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Parses the page argument (1-indexed); clamps to the valid range if out of bounds. */
    private static int parsePageArg(String[] args, int argIndex, int totalItems) {
        int totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);
        if (args.length > argIndex) {
            try {
                int page = Integer.parseInt(args[argIndex]);
                return Math.max(1, Math.min(page, totalPages));
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private static String[] collectQuestIds() {
        List<String> ids = new ArrayList<>();
        for (ChapterDefinition ch : BQApi.getRegisteredChapters()) {
            for (QuestPlacementDefinition p : ch.getPlacements()) {
                ids.add(
                    p.getQuest()
                        .getId());
            }
        }
        return ids.toArray(new String[0]);
    }

    private static String[] collectChapterIds() {
        List<String> ids = new ArrayList<>();
        for (ChapterDefinition ch : BQApi.getRegisteredChapters()) {
            ids.add(ch.getId());
        }
        return ids.toArray(new String[0]);
    }
}

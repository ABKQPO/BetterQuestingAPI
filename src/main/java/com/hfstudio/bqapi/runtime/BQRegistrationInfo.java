package com.hfstudio.bqapi.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Immutable snapshot of the metadata captured when a chapter is registered via
 * {@link com.hfstudio.bqapi.BQApi#register}:
 * <ul>
 * <li>Caller class name and method name (first external stack frame).</li>
 * <li>The FML active mod's modId at registration time (best-effort; {@code "unknown"} if unavailable).</li>
 * <li>The call stack starting from the first external frame, capped at {@value #MAX_STACK_DEPTH} frames.</li>
 * <li>The system timestamp (milliseconds) when the registration occurred.</li>
 * </ul>
 */
public final class BQRegistrationInfo {

    /** Maximum number of stack frames to retain, to limit memory usage. */
    private static final int MAX_STACK_DEPTH = 12;
    private static final String BQAPI_PKG = "com.hfstudio.bqapi";

    private final String registrantClass;
    private final String registrantMethod;
    private final int registrantLine;
    private final String modId;
    private final List<StackTraceElement> stack;
    private final long registrationTimeMs;

    private BQRegistrationInfo(String registrantClass, String registrantMethod, int registrantLine, String modId,
        List<StackTraceElement> stack, long registrationTimeMs) {
        this.registrantClass = registrantClass;
        this.registrantMethod = registrantMethod;
        this.registrantLine = registrantLine;
        this.modId = modId;
        this.stack = stack;
        this.registrationTimeMs = registrationTimeMs;
    }

    /**
     * Captures registration metadata from the current thread's call stack.
     * Skips all frames belonging to {@code com.hfstudio.bqapi}, {@code java.*}, and {@code sun.*};
     * the first external frame is treated as the "registration source".
     */
    static BQRegistrationInfo capture() {
        // Best-effort: read the FML active container modId (only reliable during mod init events).
        String modId = "unknown";
        try {
            ModContainer active = Loader.instance()
                .activeModContainer();
            if (active != null) {
                modId = active.getModId();
            }
        } catch (Exception ignored) {}

        StackTraceElement[] full = Thread.currentThread()
            .getStackTrace();
        // full[0] = Thread.getStackTrace, full[1] = capture, full[2+] = callers
        StackTraceElement callerFrame = null;
        int externalStart = 0;
        for (int i = 0; i < full.length; i++) {
            String cls = full[i].getClassName();
            if (!cls.startsWith(BQAPI_PKG) && !cls.startsWith("java.") && !cls.startsWith("sun.")) {
                callerFrame = full[i];
                externalStart = i;
                break;
            }
        }

        StackTraceElement[] relevant;
        if (externalStart > 0 && externalStart < full.length) {
            int len = Math.min(full.length - externalStart, MAX_STACK_DEPTH);
            relevant = Arrays.copyOfRange(full, externalStart, externalStart + len);
        } else {
            relevant = new StackTraceElement[0];
        }

        String cls = callerFrame != null ? callerFrame.getClassName() : "unknown";
        String method = callerFrame != null ? callerFrame.getMethodName() : "unknown";
        int line = callerFrame != null ? callerFrame.getLineNumber() : -1;

        return new BQRegistrationInfo(
            cls,
            method,
            line,
            modId,
            Collections.unmodifiableList(Arrays.asList(relevant)),
            System.currentTimeMillis());
    }

    /** Fully-qualified class name of the first external call frame. */
    public String getRegistrantClass() {
        return registrantClass;
    }

    /** Method name of the first external call frame. */
    public String getRegistrantMethod() {
        return registrantMethod;
    }

    /** Source line number of the first external call frame, or {@code -1} if unavailable. */
    public int getRegistrantLine() {
        return registrantLine;
    }

    /**
     * The modId of the FML active mod at registration time; {@code "unknown"} if it could not be determined.
     */
    public String getModId() {
        return modId;
    }

    /** The call stack starting from the first external frame (unmodifiable view). */
    public List<StackTraceElement> getStack() {
        return stack;
    }

    /** System timestamp in milliseconds ({@link System#currentTimeMillis()}) when the registration occurred. */
    public long getRegistrationTimeMs() {
        return registrationTimeMs;
    }
}

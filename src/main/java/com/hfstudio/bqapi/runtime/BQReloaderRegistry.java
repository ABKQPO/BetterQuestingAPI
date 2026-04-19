package com.hfstudio.bqapi.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.hfstudio.bqapi.api.QuestReloader;

/**
 * Stores classes registered as {@link QuestReloader} participants and invokes
 * their {@code reloadQuest()} method on every reinject cycle.
 */
public final class BQReloaderRegistry {

    private static final String RELOAD_METHOD = "reloadQuest";

    /** Resolved methods, one per registered class. */
    private final List<Method> reloaders = new ArrayList<>();

    /**
     * Registers a class as a reload participant.
     *
     * <p>
     * Validation rules (violations throw {@link IllegalArgumentException}):
     * <ul>
     * <li>The class must be annotated with {@link QuestReloader}.</li>
     * <li>The class must declare a {@code public static void reloadQuest()} method.</li>
     * </ul>
     *
     * <p>
     * Registering the same class more than once is a no-op.
     *
     * @param clazz the class to register; must satisfy the rules above
     * @throws IllegalArgumentException if the class fails validation
     */
    public synchronized void register(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(QuestReloader.class)) {
            throw new IllegalArgumentException(clazz.getName() + " must be annotated with @QuestReloader");
        }

        Method method;
        try {
            method = clazz.getMethod(RELOAD_METHOD);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(clazz.getName() + " must declare: public static void reloadQuest()", e);
        }

        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            throw new IllegalArgumentException(clazz.getName() + ".reloadQuest() must be public static void");
        }

        // Avoid duplicate registration
        for (Method existing : reloaders) {
            if (existing.getDeclaringClass() == clazz) {
                return;
            }
        }

        reloaders.add(method);
    }

    /**
     * Invokes {@code reloadQuest()} on every registered class in registration order.
     *
     * @throws RuntimeException wrapping any exception thrown by a reloader
     */
    public synchronized void invokeAll() {
        for (Method method : reloaders) {
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException(
                    "reloadQuest() threw an exception in " + method.getDeclaringClass()
                        .getName(),
                    cause);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                    "Cannot invoke reloadQuest() on " + method.getDeclaringClass()
                        .getName(),
                    e);
            }
        }
    }
}

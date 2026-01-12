package com.terrasect.common.testing;

import de.skuzzle.test.snapshots.Snapshot;
import de.skuzzle.test.snapshots.impl.SnapshotConfiguration;
import de.skuzzle.test.snapshots.impl.SnapshotTestContext;
import de.skuzzle.test.snapshots.impl.TestFrameworkSupport;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;
import org.opentest4j.TestAbortedException;

public final class SnapshotUpdateExtension
        implements ParameterResolver, BeforeAllCallback, AfterEachCallback, AfterAllCallback, TestWatcher {
    private static final Namespace NAMESPACE = Namespace.create(SnapshotUpdateExtension.class);
    private static final String CONTEXT_KEY = "SNAPSHOT_CONTEXT";
    private static final String FORCE_UPDATE_PROPERTY = "forceUpdateSnapshots";
    private static final String CREATED_INITIALLY_PREFIX = "Snapshots have been created the first time.";
    private static final String UPDATED_FORCEFULLY_PREFIX = "Snapshots have been updated forcefully.";

    @Override public void beforeAll(ExtensionContext context) {
        SnapshotTestContext testContext = createContext(context);
        context.getStore(NAMESPACE).put(CONTEXT_KEY, testContext);
    }

    @Override public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context)
            throws ParameterResolutionException {
        SnapshotTestContext testContext = getContext(context);
        Class<?> type = parameterContext.getParameter().getType();
        return testContext.isSnapshotParameter(type) || SnapshotTestContext.class.isAssignableFrom(type);
    }

    @Override public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context)
            throws ParameterResolutionException {
        SnapshotTestContext testContext = getContext(context);
        Class<?> type = parameterContext.getParameter().getType();
        if (SnapshotTestContext.class.isAssignableFrom(type)) {
            return testContext;
        }
        return testContext.createSnapshotTestFor(context.getRequiredTestMethod());
    }

    @Override public void afterEach(ExtensionContext context) throws Exception {
        SnapshotTestContext testContext = getContext(context);
        try {
            testContext.finalizeSnapshotTest();
        } catch (AssertionError error) {
            if (shouldIgnore(error)) {
                return;
            }
            throw error;
        }
    }

    @Override public void afterAll(ExtensionContext context) throws Exception {
        getContext(context).detectOrCleanupOrphanedSnapshots();
    }

    @Override public void testFailed(ExtensionContext context, Throwable cause) {
        getContext(context).recordFailedOrSkippedTest(context.getRequiredTestMethod());
    }

    @Override public void testAborted(ExtensionContext context, Throwable cause) {
        getContext(context).recordFailedOrSkippedTest(context.getRequiredTestMethod());
    }

    @Override public void testDisabled(ExtensionContext context, Optional<String> reason) {
        getContext(context).recordFailedOrSkippedTest(context.getRequiredTestMethod());
    }

    private static SnapshotTestContext createContext(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        SnapshotConfiguration configuration = SnapshotConfiguration.defaultConfigurationFor(testClass);
        return SnapshotTestContext.forConfiguration(configuration, new Junit5Support());
    }

    private static SnapshotTestContext getContext(ExtensionContext context) {
        var current = context;
        while (current != null) {
            var found = current.getStore(NAMESPACE).get(CONTEXT_KEY, SnapshotTestContext.class);
            if (found != null) {
                return found;
            }
            current = current.getParent().orElse(null);
        }
        throw new IllegalStateException(
                "SnapshotTestContext not found on given ExtensionContext or any of its parents");
    }

    private static boolean shouldIgnore(AssertionError error) {
        if (!isUpdateEnabled()) {
            return false;
        }
        var message = error.getMessage();
        if (message == null) {
            return false;
        }
        return message.startsWith(CREATED_INITIALLY_PREFIX) || message.startsWith(UPDATED_FORCEFULLY_PREFIX);
    }

    private static boolean isUpdateEnabled() {
        for (Object key : System.getProperties().keySet()) {
            if (String.valueOf(key).contains(FORCE_UPDATE_PROPERTY)) {
                return true;
            }
        }
        return false;
    }

    private static final class Junit5Support implements TestFrameworkSupport {
        @Override public boolean isSnapshotTest(Class<?> testClass, Method method) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
                return false;
            }
            return Arrays.stream(method.getParameterTypes()).anyMatch(Snapshot.class::equals);
        }

        @Override public Throwable assumptionFailed(String message) {
            return new TestAbortedException(message);
        }
    }
}

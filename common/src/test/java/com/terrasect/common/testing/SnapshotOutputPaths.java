package com.terrasect.common.testing;

import java.io.File;
import java.nio.file.Path;

public final class SnapshotOutputPaths {
    private static final String COLLAPSED_PREFIX = "com.terrasect.common";

    public static File forTestClass(Class<?> testClass, String... segments) {
        var base = Path.of("build", "test-snapshots");
        var path = base.resolve(collapsedClassPath(testClass));
        if (segments != null) {
            for (String segment : segments) {
                if (segment == null || segment.isEmpty()) continue;
                path = path.resolve(segment);
            }
        }
        return path.toFile();
    }

    private static Path collapsedClassPath(Class<?> testClass) {
        var packageName = testClass.getPackageName();
        if (packageName.startsWith(COLLAPSED_PREFIX)) {
            packageName = packageName.substring(COLLAPSED_PREFIX.length());
            if (packageName.startsWith(".")) {
                packageName = packageName.substring(1);
            }
        }
        var path = packageName.isEmpty()
                ? Path.of(testClass.getSimpleName())
                : Path.of(packageName.replace('.', File.separatorChar)).resolve(testClass.getSimpleName());
        return path;
    }

    private SnapshotOutputPaths() {
    }
}

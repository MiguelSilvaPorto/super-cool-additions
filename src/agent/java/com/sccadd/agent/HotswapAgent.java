package com.sccadd.agent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Development-only hot-swap agent: watches the mod's compiled classes and
 * redefines changed classes inside the running game, so logic fixes do not need
 * a restart. Structural changes (new methods/fields/classes) are rejected by the
 * stock JVM - the agent logs that a restart is needed for those. Mixin classes
 * are skipped (they are merged into their targets at load time).
 */
public final class HotswapAgent {
    private HotswapAgent() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        Path dir = Paths.get(args == null || args.isBlank() ? "build/classes/java/main" : args.trim());
        Thread watcher = new Thread(() -> watch(dir, instrumentation), "sccadd-hotswap");
        watcher.setDaemon(true);
        watcher.start();
        System.out.println("[sccadd-hotswap] watching " + dir.toAbsolutePath());
    }

    private static void watch(Path root, Instrumentation instrumentation) {
        Map<Path, Long> stamps = new HashMap<>();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (Files.isDirectory(root)) {
                    try (Stream<Path> stream = Files.walk(root)) {
                        for (Path file : (Iterable<Path>) stream
                                .filter(path -> path.toString().endsWith(".class"))::iterator) {
                            long stamp = Files.getLastModifiedTime(file).toMillis();
                            Long previous = stamps.put(file, stamp);
                            if (previous != null && previous != stamp) {
                                reload(root, file, instrumentation);
                            }
                        }
                    }
                }
                Thread.sleep(500L);
            } catch (InterruptedException interrupted) {
                return;
            } catch (Throwable failed) {
                // keep watching
            }
        }
    }

    private static void reload(Path root, Path file, Instrumentation instrumentation) {
        String name = root.relativize(file).toString().replace('/', '.').replace('\\', '.');
        name = name.substring(0, name.length() - ".class".length());
        if (name.startsWith("com.sccadd.mixin.") || name.startsWith("com.sccadd.agent.")) {
            return;
        }
        Class<?> type = null;
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(name)) {
                type = loaded;
                break;
            }
        }
        if (type == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            instrumentation.redefineClasses(new ClassDefinition(type, bytes));
            System.out.println("[sccadd-hotswap] reloaded " + name);
        } catch (Throwable failed) {
            System.out.println("[sccadd-hotswap] " + name + " needs a restart: " + failed.getClass().getSimpleName());
        }
    }
}

package com.phcraft.philosnpc.npc;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** File operations shared by legacy inventory migration and its failure-path tests. */
final class InventoryMigrationStorage {

    private InventoryMigrationStorage() {
    }

    @FunctionalInterface
    interface Writer {
        void write(Path target) throws IOException;
    }

    /** Makes an untouched copy before any migration metadata is persisted. */
    static Path backupOriginal(Path source, Path preferredBackup) throws IOException {
        Path absoluteSource = source.toAbsolutePath();
        Path parent = absoluteSource.getParent();
        if (parent == null || !Files.isRegularFile(absoluteSource)) {
            throw new IOException("migration source is not a regular file: " + source);
        }
        Files.createDirectories(parent);

        Path backup = preferredBackup.toAbsolutePath();
        if (Files.exists(backup) && Files.mismatch(absoluteSource, backup) != -1) {
            backup = backup.resolveSibling(backup.getFileName() + "-" + UUID.randomUUID());
        }
        if (!Files.exists(backup)) {
            Files.copy(absoluteSource, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return backup;
    }

    /**
     * Backs up the current source, writes to a sibling temporary file, and then replaces the source.
     * If backup or writing fails, the original source remains available for the next startup.
     */
    static Path saveWithBackup(Path source, Path preferredBackup, Writer writer) throws IOException {
        Path backup = backupOriginal(source, preferredBackup);
        writeAtomically(source, writer);
        return backup;
    }

    static void writeAtomically(Path target, Writer writer) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent == null) throw new IOException("target has no parent directory: " + target);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

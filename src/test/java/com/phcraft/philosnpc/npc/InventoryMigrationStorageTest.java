package com.phcraft.philosnpc.npc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryMigrationStorageTest {

    @TempDir
    Path directory;

    @Test
    void failedBackupDoesNotInvokeWriterOrChangeSource() throws IOException {
        Path source = directory.resolve("missing.yml");
        Path backup = directory.resolve("missing.yml.bak-v1-inventory");
        AtomicBoolean writerCalled = new AtomicBoolean();

        assertThrows(IOException.class, () -> InventoryMigrationStorage.saveWithBackup(
                source, backup, target -> writerCalled.set(true)));

        assertFalse(writerCalled.get());
        assertFalse(Files.exists(source));
    }

    @Test
    void failedWriteLeavesOriginalAndBackupUntouched() throws IOException {
        Path source = directory.resolve("npcs.yml");
        Path backup = directory.resolve("npcs.yml.bak-v1-inventory");
        Files.writeString(source, "original stock");

        assertThrows(IOException.class, () -> InventoryMigrationStorage.saveWithBackup(source, backup, target -> {
            Files.writeString(target, "new migration marker");
            throw new IOException("simulated storage failure");
        }));

        assertEquals("original stock", Files.readString(source));
        assertEquals("original stock", Files.readString(backup));
    }

    @Test
    void stalePreferredBackupIsPreservedAndCurrentSourceGetsAnotherCopy() throws IOException {
        Path source = directory.resolve("npcs.yml");
        Path backup = directory.resolve("npcs.yml.bak-v1-inventory");
        Files.writeString(source, "current stock");
        Files.writeString(backup, "older backup");

        Path actualBackup = InventoryMigrationStorage.backupOriginal(source, backup);

        assertNotEquals(backup, actualBackup);
        assertEquals("older backup", Files.readString(backup));
        assertEquals("current stock", Files.readString(actualBackup));
        assertEquals("current stock", Files.readString(source));
    }

    @Test
    void successfulWriteReplacesSourceOnlyAfterCreatingBackup() throws IOException {
        Path source = directory.resolve("npcs.yml");
        Path backup = directory.resolve("npcs.yml.bak-v1-inventory");
        Files.writeString(source, "original stock");

        InventoryMigrationStorage.saveWithBackup(source, backup, target -> {
            assertEquals("original stock", Files.readString(source));
            assertEquals("original stock", Files.readString(backup));
            Files.writeString(target, "review marker");
        });

        assertEquals("review marker", Files.readString(source));
        assertEquals("original stock", Files.readString(backup));
        assertTrue(Files.exists(source));
    }
}

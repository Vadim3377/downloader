package downloader.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesSha256ForFile() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "abc");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtils.sha256(file));
    }
}

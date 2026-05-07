package downloader.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SizeParserTest {

    @Test
    void parsesByteUnits() {
        assertEquals(1, SizeParser.parseBytes("1"));
        assertEquals(1, SizeParser.parseBytes("1B"));
        assertEquals(1024, SizeParser.parseBytes("1KB"));
        assertEquals(1024 * 1024, SizeParser.parseBytes("1MB"));
        assertEquals(2L * 1024 * 1024 * 1024, SizeParser.parseBytes("2GB"));
    }
}

package com.eduexam.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelServiceTest {

    private final ExcelService service = new ExcelService();

    @Test
    void csvRoundTripPreservesChineseQuotesCommasAndNewlines() throws Exception {
        List<String> headers = List.of("title", "answer");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("title", "中文, \"引用\"\n第二行");
        source.put("answer", "A");

        byte[] exported = service.export("csv", headers, List.of(source));

        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                new byte[]{exported[0], exported[1], exported[2]});
        List<Map<String, String>> parsed = service.parse("questions.csv", exported);
        assertEquals(1, parsed.size());
        assertEquals(source.get("title"), parsed.get(0).get("title"));
        assertEquals("A", parsed.get(0).get("answer"));
    }

    @Test
    void rejectsUnsupportedExtensionAndMalformedCsv() {
        assertThrows(IllegalArgumentException.class,
                () -> service.parse("questions.txt", "a,b".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> service.parse("legacy.xls", "not-an-xlsx".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> service.parse("questions.csv", "a\n\"open".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsXlsxWhoseAggregateExpandedContentExceedsLimit() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            byte[] block = new byte[8 * 1024];
            for (int i = 0; i <= (32 * 1024 * 1024) / block.length; i++) zip.write(block);
            zip.closeEntry();
        }

        assertThrows(IllegalArgumentException.class,
                () -> service.parse("oversized.xlsx", bytes.toByteArray()));
    }

    @Test
    void malformedXlsxAlwaysReleasesTheGlobalImportPermit() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.parse("broken.xlsx", "not-a-zip".getBytes(StandardCharsets.UTF_8)));

        byte[] valid = service.export("xlsx", List.of("name"), List.of(Map.of("name", "student")));
        assertEquals("student", service.parse("valid.xlsx", valid).get(0).get("name"));
    }

    @Test
    void csvExportNeutralizesSpreadsheetFormulaStrings() throws Exception {
        byte[] exported = service.export("csv", List.of("name"),
                List.of(Map.of("name", "=HYPERLINK(\"https://example.invalid\")")));

        List<Map<String, String>> parsed = service.parse("users.csv", exported);
        assertEquals("'=HYPERLINK(\"https://example.invalid\")", parsed.get(0).get("name"));
    }

    @Test
    void rejectsOversizedInMemoryExports() {
        List<Map<String, Object>> rows = Collections.nCopies(
                ExcelService.MAX_EXPORT_ROWS + 1, Map.of("name", "student"));
        assertThrows(IllegalArgumentException.class,
                () -> service.export("xlsx", List.of("name"), rows));
    }

    @Test
    void rejectsExcessCsvRowsWhileScanning() {
        byte[] csv = ("name\n" + "x\n".repeat(ExcelService.MAX_IMPORT_ROWS + 1))
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.parse("users.csv", csv));
    }
}

package com.eduexam.service;

import com.eduexam.common.AppException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.openxml4j.util.ZipInputStreamZipEntrySource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** Tabular XLSX/CSV import and export shared by administration endpoints. */
@Service
public class ExcelService {

    public static final int MAX_EXPORT_ROWS = 10_000;
    public static final int MAX_IMPORT_ROWS = 20_000;
    private static final int MAX_IMPORT_COLUMNS = 256;
    private static final long MAX_IMPORT_CELLS = 500_000;
    private static final long MAX_IMPORT_TEXT_CHARS = 20_000_000;
    private static final int MAX_IMPORT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_XLSX_ENTRIES = 1_000;
    private static final long MAX_XLSX_ENTRY_BYTES = 32L * 1024 * 1024;
    private static final long MAX_XLSX_TOTAL_BYTES = 32L * 1024 * 1024;
    private static final Semaphore XLSX_IMPORT_PERMIT = new Semaphore(1, true);

    static {
        // Multipart size limits compressed input, while these POI limits also
        // bound expansion of valid-looking but excessively compressed XLSX XML.
        ZipSecureFile.setMinInflateRatio(Math.max(ZipSecureFile.getMinInflateRatio(), 0.01d));
        ZipSecureFile.setMaxEntrySize(Math.min(ZipSecureFile.getMaxEntrySize(), MAX_XLSX_ENTRY_BYTES));
        ZipSecureFile.setMaxTextSize(Math.min(ZipSecureFile.getMaxTextSize(), 50L * 1024 * 1024));
        ZipSecureFile.setMaxFileCount(Math.min(ZipSecureFile.getMaxFileCount(), MAX_XLSX_ENTRIES));
        // Keep expanded OOXML parts out of the heap once they exceed 1 MiB.
        ZipInputStreamZipEntrySource.setThresholdBytesForTempFiles(1024 * 1024);
        ZipInputStreamZipEntrySource.setEncryptTempFiles(true);
    }
    public static final long MAX_EXPORT_CELLS = 500_000;
    private static final long MAX_EXPORT_TEXT_CHARS = 20_000_000;

    public byte[] export(List<String> headers, List<Map<String, Object>> rows) throws Exception {
        return export("xlsx", headers, rows);
    }

    public byte[] export(String format, List<String> headers, List<Map<String, Object>> rows) throws Exception {
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("单次最多导出 " + MAX_EXPORT_ROWS + " 行，请增加筛选条件后重试");
        }
        if ((long) headers.size() * Math.max(1, rows.size()) > MAX_EXPORT_CELLS)
            throw new IllegalArgumentException("导出数据列数与行数的组合过大，请缩小导出范围");
        long textChars = 0;
        for (Map<String, Object> row : rows) {
            for (String header : headers) {
                Object value = row.get(header);
                if (value instanceof CharSequence chars) {
                    textChars += chars.length();
                    if (textChars > MAX_EXPORT_TEXT_CHARS)
                        throw new IllegalArgumentException("导出文本内容过大，请增加筛选条件后重试");
                }
            }
        }
        String normalized = normalizeFormat(format);
        return "csv".equals(normalized) ? exportCsv(headers, rows) : exportXlsx(headers, rows);
    }

    public String normalizeFormat(String format) {
        String normalized = format == null ? "xlsx" : format.strip().toLowerCase(Locale.ROOT);
        if (!List.of("xlsx", "csv").contains(normalized)) {
            throw new IllegalArgumentException("仅支持 xlsx 或 csv 格式");
        }
        return normalized;
    }

    private byte[] exportXlsx(List<String> headers, List<Map<String, Object>> rows) throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        wb.setCompressTempFiles(true);
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) headerRow.createCell(i).setCellValue(headers.get(i));
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < headers.size(); c++) {
                    Object val = rows.get(r).get(headers.get(c));
                    Cell cell = row.createCell(c);
                    if (val == null) cell.setCellValue("");
                    else if (val instanceof Number number) cell.setCellValue(number.doubleValue());
                    else if (val instanceof Boolean bool) cell.setCellValue(bool);
                    else cell.setCellValue(val.toString());
                }
            }
            wb.write(out);
            return out.toByteArray();
        } finally {
            // SXSSF stores flushed rows in temporary files; always remove them.
            wb.dispose();
        }
    }

    private byte[] exportCsv(List<String> headers, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, headers.stream().map(h -> (Object) h).toList());
        for (Map<String, Object> row : rows) {
            List<Object> values = new ArrayList<>(headers.size());
            for (String header : headers) values.add(row.get(header));
            appendCsvRow(csv, values);
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }

    private void appendCsvRow(StringBuilder csv, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) csv.append(',');
            Object raw = values.get(i);
            String value = raw == null ? "" : raw.toString();
            // Spreadsheet programs may execute string cells beginning with a
            // formula marker when a CSV is opened. Prefix user-controlled text
            // with an apostrophe so exports remain data, never formulas.
            if (raw instanceof CharSequence) {
                String leadingTrimmed = value.stripLeading();
                if (!leadingTrimmed.isEmpty()
                        && "=+-@\t\r".indexOf(leadingTrimmed.charAt(0)) >= 0) {
                    value = "'" + value;
                }
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    public List<Map<String, String>> parse(String filename, byte[] content) throws Exception {
        if (content == null || content.length == 0) return List.of();
        if (content.length > MAX_IMPORT_BYTES)
            throw new IllegalArgumentException("导入文件不能超过 10MB");
        String extension = extension(filename);
        if ("csv".equals(extension)) return parseCsv(content);
        if (!"xlsx".equals(extension)) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .csv 文件");
        }
        if (!XLSX_IMPORT_PERMIT.tryAcquire())
            throw AppException.tooManyRequests("已有 Excel 文件正在解析，请稍后重试");
        try {
            validateXlsxArchive(content);
            return parseWorkbook(content);
        } finally {
            XLSX_IMPORT_PERMIT.release();
        }
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private List<Map<String, String>> parseWorkbook(byte[] content) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(content); Workbook wb = WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) return result;
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return result;
            List<String> headers = new ArrayList<>();
            int lastColumn = Math.max(0, headerRow.getLastCellNum());
            if (lastColumn > MAX_IMPORT_COLUMNS)
                throw new IllegalArgumentException("导入文件最多包含 " + MAX_IMPORT_COLUMNS + " 列");
            for (int c = 0; c < lastColumn; c++) headers.add(normalizeHeader(getCellString(headerRow.getCell(c))));
            validateHeaders(headers);
            long cellCount = 0;
            long textChars = 0;
            for (Row row : sheet) {
                if (row == null || row.getRowNum() == 0) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    String val = getCellString(row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
                    cellCount++;
                    textChars += val.length();
                    if (cellCount > MAX_IMPORT_CELLS || textChars > MAX_IMPORT_TEXT_CHARS)
                        throw new IllegalArgumentException("导入文件内容过大，请拆分后重试");
                    rowMap.put(headers.get(c), val);
                    if (!val.isEmpty()) hasData = true;
                }
                if (hasData) {
                    if (result.size() >= MAX_IMPORT_ROWS)
                        throw new IllegalArgumentException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
                    result.add(rowMap);
                }
            }
        }
        return result;
    }

    /**
     * Validate the OOXML ZIP before POI builds its in-memory object model. POI's
     * own guards protect individual entries while parsing, but a cheap streaming
     * pass also caps the aggregate expanded size and rejects duplicate entries.
     */
    private void validateXlsxArchive(byte[] content) {
        Set<String> names = new HashSet<>();
        boolean hasContentTypes = false;
        boolean hasWorkbook = false;
        int entryCount = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[8 * 1024];

        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(content))) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isBlank() || name.length() > 1_024 || name.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("XLSX 文件包含非法 ZIP 条目");
                if (!names.add(name))
                    throw new IllegalArgumentException("XLSX 文件包含重复 ZIP 条目");
                if (++entryCount > MAX_XLSX_ENTRIES)
                    throw new IllegalArgumentException("XLSX 文件包含过多 ZIP 条目");
                if (!zip.canReadEntryData(entry))
                    throw new IllegalArgumentException("XLSX 文件包含不支持或加密的 ZIP 条目");

                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasWorkbook |= "xl/workbook.xml".equals(name);

                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    totalBytes += read;
                    if (entryBytes > MAX_XLSX_ENTRY_BYTES || totalBytes > MAX_XLSX_TOTAL_BYTES)
                        throw new IllegalArgumentException("XLSX 解压后内容过大，请拆分后重试");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("XLSX 文件损坏或压缩格式无效", e);
        }

        if (!hasContentTypes || !hasWorkbook)
            throw new IllegalArgumentException("文件不是有效的 XLSX 工作簿");
    }

    private List<Map<String, String>> parseCsv(byte[] content) {
        int offset = content.length >= 3 && content[0] == (byte) 0xEF
                && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF ? 3 : 0;
        String input = new String(content, offset, content.length - offset, StandardCharsets.UTF_8);
        List<List<String>> records = parseCsvRecords(input);
        if (records.isEmpty()) return List.of();
        List<String> headers = records.get(0).stream().map(this::normalizeHeader).toList();
        validateHeaders(headers);
        if ((long) Math.max(0, records.size() - 1) * headers.size() > MAX_IMPORT_CELLS)
            throw new IllegalArgumentException("导入文件内容过大，请拆分后重试");
        List<Map<String, String>> result = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> record = records.get(r);
            if (record.size() > headers.size())
                throw new IllegalArgumentException("CSV 第 " + (r + 1) + " 行的列数超过表头");
            Map<String, String> row = new LinkedHashMap<>();
            boolean hasData = false;
            for (int c = 0; c < headers.size(); c++) {
                String value = c < record.size() ? record.get(c).strip() : "";
                row.put(headers.get(c), value);
                if (!value.isEmpty()) hasData = true;
            }
            if (hasData) {
                if (result.size() >= MAX_IMPORT_ROWS)
                    throw new IllegalArgumentException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
                result.add(row);
            }
        }
        return result;
    }

    private List<List<String>> parseCsvRecords(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean justClosedQuote = false;
        long parsedCells = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        justClosedQuote = true;
                    }
                } else field.append(ch);
                continue;
            }
            if (ch == '"' && field.length() == 0 && !justClosedQuote) quoted = true;
            else if (ch == ',') {
                if (row.size() >= MAX_IMPORT_COLUMNS)
                    throw new IllegalArgumentException("导入文件最多包含 " + MAX_IMPORT_COLUMNS + " 列");
                if (++parsedCells > MAX_IMPORT_CELLS)
                    throw new IllegalArgumentException("导入文件内容过大，请拆分后重试");
                row.add(field.toString());
                field.setLength(0);
                justClosedQuote = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                if (row.size() >= MAX_IMPORT_COLUMNS)
                    throw new IllegalArgumentException("导入文件最多包含 " + MAX_IMPORT_COLUMNS + " 列");
                if (++parsedCells > MAX_IMPORT_CELLS)
                    throw new IllegalArgumentException("导入文件内容过大，请拆分后重试");
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) addCsvRecord(records, row);
                row = new ArrayList<>();
                justClosedQuote = false;
            } else if (justClosedQuote && !Character.isWhitespace(ch)) {
                throw new IllegalArgumentException("CSV 引号结束后存在非法字符");
            } else if (!justClosedQuote) field.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("CSV 文件中存在未闭合的引号");
        if (field.length() > 0 || !row.isEmpty()) {
            if (row.size() >= MAX_IMPORT_COLUMNS)
                throw new IllegalArgumentException("导入文件最多包含 " + MAX_IMPORT_COLUMNS + " 列");
            if (++parsedCells > MAX_IMPORT_CELLS)
                throw new IllegalArgumentException("导入文件内容过大，请拆分后重试");
            row.add(field.toString());
            addCsvRecord(records, row);
        }
        return records;
    }

    private void addCsvRecord(List<List<String>> records, List<String> row) {
        // One record is the header; reject excess data while scanning instead
        // of first materializing an attacker-controlled number of tiny rows.
        if (records.size() >= MAX_IMPORT_ROWS + 1)
            throw new IllegalArgumentException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
        records.add(row);
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.strip().toLowerCase(Locale.ROOT)
                .replace(" ", "_").replace("-", "_");
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty() || headers.stream().allMatch(String::isBlank))
            throw new IllegalArgumentException("导入文件缺少表头");
        if (headers.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("导入文件包含空表头");
        if (headers.stream().distinct().count() != headers.size())
            throw new IllegalArgumentException("导入文件包含重复表头");
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().strip(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}

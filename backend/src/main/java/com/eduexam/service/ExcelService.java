package com.eduexam.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class ExcelService {

    public byte[] export(List<String> headers, List<Map<String, Object>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < headers.size(); c++) {
                    Object val = rows.get(r).get(headers.get(c));
                    Cell cell = row.createCell(c);
                    if (val == null) cell.setCellValue("");
                    else if (val instanceof Number) cell.setCellValue(((Number) val).doubleValue());
                    else if (val instanceof Boolean) cell.setCellValue((Boolean) val);
                    else cell.setCellValue(val.toString());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public List<Map<String, String>> parse(String filename, byte[] content) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(content);
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return result;
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim().toLowerCase()
                        .replace(" ", "_").replace("-", "_"));
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String val = getCellString(cell);
                    rowMap.put(headers.get(c), val);
                    if (!val.isEmpty()) hasData = true;
                }
                if (hasData) result.add(rowMap);
            }
        }
        return result;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}

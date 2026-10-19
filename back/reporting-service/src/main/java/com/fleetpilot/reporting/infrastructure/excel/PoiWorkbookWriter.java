package com.fleetpilot.reporting.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fleetpilot.reporting.application.Tables.Column;
import com.fleetpilot.reporting.application.Tables.Kind;
import com.fleetpilot.reporting.application.Tables.Sheet;
import com.fleetpilot.reporting.application.Tables.WorkbookWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Lays the report tables out in an Excel workbook (.xlsx): a title and subtitle, a teal header row frozen at the top
 * with filters, typed cells (amounts in euros, dates, integers) so that the figures can be summed and sorted, and a
 * bold totals row.
 */
@Component
public class PoiWorkbookWriter implements WorkbookWriter {

    private static final int TITLE_ROW = 0;
    private static final int SUBTITLE_ROW = 1;
    private static final int HEADER_ROW = 3;
    private static final byte[] TEAL = {(byte) 0x0f, (byte) 0x76, (byte) 0x6e};
    private static final byte[] LIGHT = {(byte) 0xf5, (byte) 0xf6, (byte) 0xf8};

    @Override
    public byte[] write(List<Sheet> sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            for (Sheet sheet : sheets) {
                write(workbook.createSheet(sheet.name()), sheet, styles);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("The workbook could not be written", e);
        }
    }

    private static void write(XSSFSheet sheet, Sheet table, Styles styles) {
        List<Column> columns = table.columns();
        cell(sheet.createRow(TITLE_ROW), 0, table.title(), styles.title);
        cell(sheet.createRow(SUBTITLE_ROW), 0, table.subtitle(), styles.subtitle);
        Row header = sheet.createRow(HEADER_ROW);
        header.setHeightInPoints(20);
        for (int c = 0; c < columns.size(); c++) {
            cell(header, c, columns.get(c).label(), styles.header);
            sheet.setColumnWidth(c, columns.get(c).width() * 256);
        }
        int r = HEADER_ROW + 1;
        for (List<Object> values : table.rows()) {
            Row row = sheet.createRow(r++);
            for (int c = 0; c < columns.size(); c++) {
                value(row, c, values.get(c), styles.forKind(columns.get(c).kind(), false));
            }
        }
        if (!table.totals().isEmpty()) {
            Row totals = sheet.createRow(r);
            for (int c = 0; c < columns.size(); c++) {
                value(totals, c, table.totals().get(c), styles.forKind(columns.get(c).kind(), true));
            }
        }
        if (!table.rows().isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, HEADER_ROW + table.rows().size(), 0, columns.size() - 1));
        }
        sheet.createFreezePane(0, HEADER_ROW + 1);
    }

    private static void value(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            case LocalDate date -> cell.setCellValue(date);
            default -> cell.setCellValue(value.toString());
        }
    }

    private static void cell(Row row, int column, String text, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    /** The cell styles of a workbook (POI requires them to be created once per workbook). */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle subtitle;
        private final CellStyle header;
        private final Map<Kind, CellStyle> body = new EnumMap<>(Kind.class);
        private final Map<Kind, CellStyle> total = new EnumMap<>(Kind.class);

        Styles(XSSFWorkbook workbook) {
            DataFormat format = workbook.createDataFormat();
            title = workbook.createCellStyle();
            title.setFont(font(workbook, 14, true, null));
            subtitle = workbook.createCellStyle();
            XSSFFont grey = font(workbook, 10, false, null);
            grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle.setFont(grey);
            XSSFCellStyle head = workbook.createCellStyle();
            head.setFont(font(workbook, 10, true, new XSSFColor(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff})));
            head.setFillForegroundColor(new XSSFColor(TEAL));
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header = head;
            for (Kind kind : Kind.values()) {
                body.put(kind, style(workbook, format, kind, false));
                total.put(kind, style(workbook, format, kind, true));
            }
        }

        CellStyle forKind(Kind kind, boolean isTotal) {
            return isTotal ? total.get(kind) : body.get(kind);
        }

        private static XSSFCellStyle style(XSSFWorkbook workbook, DataFormat format, Kind kind, boolean isTotal) {
            XSSFCellStyle style = workbook.createCellStyle();
            String pattern = switch (kind) {
                case TEXT -> "@";
                case INTEGER -> "# ##0";
                case DECIMAL -> "# ##0.00#";
                case MONEY -> "# ##0.00 \"€\"";
                case PERCENT -> "0.0\" %\"";
                case DATE -> "dd/mm/yyyy";
            };
            style.setDataFormat(format.getFormat(pattern));
            if (isTotal) {
                style.setFont(font(workbook, 10, true, null));
                style.setBorderTop(BorderStyle.THIN);
                style.setFillForegroundColor(new XSSFColor(LIGHT));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        private static XSSFFont font(XSSFWorkbook workbook, int points, boolean bold, XSSFColor color) {
            XSSFFont font = workbook.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) points);
            font.setBold(bold);
            if (color != null) {
                font.setColor(color);
            }
            font.setUnderline(Font.U_NONE);
            return font;
        }
    }
}

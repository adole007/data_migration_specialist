package com.oz.dms.excel;

import com.oz.dms.FileRecord;
import com.oz.dms.Summary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal XLSX writer that generates Excel files using Office Open XML (OOXML) format.
 * 
 * This implementation manually constructs the XLSX file structure without external libraries
 * (like Apache POI) by directly writing OOXML parts as a ZIP archive. This approach:
 * - Keeps the project dependency-free (no external JARs needed)
 * - Generates valid Excel files readable by Microsoft Excel, LibreOffice, etc.
 * - Supports basic features: inline strings, numbers, multiple sheets
 * 
 * XLSX files are ZIP archives containing XML files. The structure includes:
 * - [Content_Types].xml: MIME type mappings for all parts
 * - _rels/.rels: Package-level relationships
 * - docProps/: Document properties (core.xml, app.xml)
 * - xl/workbook.xml: Workbook structure and sheet references
 * - xl/worksheets/: Individual sheet data (sheet1.xml, sheet2.xml)
 * 
 * Limitations (by design for simplicity):
 * - Only supports inline strings (not shared strings table)
 * - No formatting, styles, or formulas
 * - No cell merging or advanced features
 * - Sufficient for generating data reports with text and numbers
 */
public class SimpleXlsxWriter {
    /** Name of the first sheet (File Inventory) */
    private String sheet1Name = "File Inventory";
    
    /** Name of the second sheet (Summary) */
    private String sheet2Name = "Summary";
    
    /** Rows data for the first sheet (File Inventory) */
    private List<List<Object>> sheet1Rows = new ArrayList<>();
    
    /** Rows data for the second sheet (Summary) */
    private List<List<Object>> sheet2Rows = new ArrayList<>();

    /**
     * Builds the File Inventory sheet with all scanned file records.
     * 
     * Creates a table with columns:
     * - Path: Full absolute path to the file
     * - Filename: Just the filename without directory
     * - Size (MB): File size in megabytes (formatted to 2 decimal places)
     * - Type: File extension (e.g., DOCX, XLSX, PDF)
     * - Created Date: File creation timestamp
     * - Modified Date: File last modified timestamp
     * - Issues Found: Semicolon-separated list of validation issues
     * 
     * Issues are joined with "; " separator. Empty issues list results in empty string.
     * Files with size -1 (inaccessible) show empty string for size.
     * 
     * @param name Sheet name (typically "File Inventory")
     * @param records List of FileRecord objects to include in the sheet
     */
    public void addInventorySheet(String name, List<FileRecord> records) {
        this.sheet1Name = name;
        sheet1Rows.clear();
        
        // Add header row with column names
        row(sheet1Rows, "Path", "Filename", "Size (MB)", "Type", "Created Date", "Modified Date", "Issues Found");
        
        // Add data row for each file record
        for (FileRecord r : records) {
            // Join all issues with semicolon separator, or empty string if no issues
            String issues = r.issues.isEmpty() ? "" : String.join("; ", r.issues);
            
            // Create row with file metadata
            row(sheet1Rows,
                r.path,                                    // Full path
                r.filename,                                // Filename only
                r.sizeMb >= 0 ? round2(r.sizeMb) : "",    // Size (formatted) or empty if unknown
                r.extension,                              // File extension
                r.created,                                 // Creation date
                r.modified,                                // Modification date
                issues                                     // Issues list (joined)
            );
        }
    }

    /**
     * Builds the Summary sheet with aggregated statistics.
     * 
     * Creates two sections:
     * 1. Top-level statistics (key-value pairs):
     *    - Total files scanned
     *    - Total size (MB)
     *    - Files with issues
     * 2. File count by type (table):
     *    - Type column: File extension (or "(none)" for files without extension)
     *    - Count column: Number of files with that extension
     * 
     * A blank row separates the two sections for readability.
     * 
     * @param name Sheet name (typically "Summary")
     * @param s Summary object containing aggregated statistics
     */
    public void addSummarySheet(String name, Summary s) {
        this.sheet2Name = name;
        sheet2Rows.clear();
        
        // Top-level statistics section
        // Header row
        row(sheet2Rows, "Metric", "Value");
        // Statistics rows
        row(sheet2Rows, "Total files scanned", s.totalFiles);
        row(sheet2Rows, "Total size (MB)", round2(s.totalSizeMb));
        row(sheet2Rows, "Files with issues", s.filesWithIssues);
        
        // Blank row separator for visual clarity
        sheet2Rows.add(new ArrayList<>());
        
        // File count by type section
        // Header row
        row(sheet2Rows, "Type", "Count");
        // Data rows: one per file type
        for (Map.Entry<String, Long> e : s.countByType.entrySet()) {
            row(sheet2Rows, e.getKey(), e.getValue());
        }
    }

    /**
     * Formats a double value to 2 decimal places for display.
     * 
     * Uses Locale.ROOT to ensure consistent formatting regardless of system locale
     * (always uses '.' as decimal separator, not ',' which some locales use).
     * 
     * @param v The double value to format
     * @return String representation with exactly 2 decimal places (e.g., "123.45")
     */
    private static String round2(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /**
     * Helper method to add a row to a sheet's row list.
     * 
     * Converts variable arguments into a List<Object> and appends it to the rows list.
     * Used to build sheet data row by row.
     * 
     * @param rows The list of rows to add to
     * @param cells Variable arguments representing cell values in the row
     */
    private static void row(List<List<Object>> rows, Object... cells) {
        List<Object> r = new ArrayList<>(cells.length);
        // Copy all cell values into the row list
        for (Object c : cells) r.add(c);
        rows.add(r);
    }

    /**
     * Writes the Excel file to disk by constructing the XLSX ZIP archive.
     * 
     * XLSX files are ZIP archives containing XML parts. This method:
     * 1. Creates a ZIP output stream
     * 2. Writes all required OOXML parts in the correct structure
     * 3. Closes the ZIP file
     * 
     * The parts are written in this order:
     * - [Content_Types].xml: MIME type registry (must be first or second)
     * - _rels/.rels: Package relationships (links parts together)
     * - docProps/core.xml: Core document properties (title, creator, dates)
     * - docProps/app.xml: Extended properties (application info)
     * - xl/workbook.xml: Workbook structure (sheet definitions)
     * - xl/_rels/workbook.xml.rels: Workbook relationships (links to sheets)
     * - xl/worksheets/sheet1.xml: First sheet data (File Inventory)
     * - xl/worksheets/sheet2.xml: Second sheet data (Summary)
     * 
     * @param outputFile Path to the output .xlsx file (will be created or overwritten)
     * @throws IOException if file cannot be written or ZIP operations fail
     */
    public void write(String outputFile) throws IOException {
        Path out = Path.of(outputFile).toAbsolutePath();
        try (OutputStream fos = Files.newOutputStream(out);
             ZipOutputStream zip = new ZipOutputStream(fos)) {

            // Write all required OOXML parts to the ZIP archive
            // [Content_Types].xml - MIME type mappings for all parts
            put(zip, "[Content_Types].xml", contentTypes());
            
            // _rels/.rels - Package-level relationships (links workbook, core props, app props)
            put(zip, "_rels/.rels", rels());
            
            // docProps/core.xml - Core document properties (Dublin Core metadata)
            put(zip, "docProps/core.xml", coreProps());
            // docProps/app.xml - Extended application properties
            put(zip, "docProps/app.xml", appProps());
            
            // xl/workbook.xml - Workbook structure (defines sheets)
            put(zip, "xl/workbook.xml", workbook());
            // xl/_rels/workbook.xml.rels - Workbook relationships (links to worksheets)
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            
            // xl/worksheets/sheet1.xml - First worksheet data (File Inventory)
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(sheet1Rows));
            // xl/worksheets/sheet2.xml - Second worksheet data (Summary)
            put(zip, "xl/worksheets/sheet2.xml", sheetXml(sheet2Rows));
        }
    }

    /**
     * Writes a file entry to the ZIP archive.
     * 
     * Creates a ZIP entry with the given name and writes the content bytes.
     * This is a helper method to simplify writing multiple parts to the XLSX ZIP.
     * 
     * @param zip The ZIP output stream to write to
     * @param name The entry name (path within the ZIP, e.g., "xl/workbook.xml")
     * @param content The content bytes to write
     * @throws IOException if ZIP operations fail
     */
    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zip.putNextEntry(e);
        zip.write(content);
        zip.closeEntry();
    }

    /**
     * Generates the [Content_Types].xml part.
     * 
     * This part declares MIME types for all files in the package.
     * Required by OOXML specification - Excel readers use this to identify part types.
     * 
     * Contains:
     * - Default mappings: .rels files and .xml files
     * - Override mappings: Specific parts with their exact MIME types
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] contentTypes() throws IOException {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "  <Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "  <Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
                "  <Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
                "</Types>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates the _rels/.rels part (package relationships).
     * 
     * Defines relationships at the package level, linking:
     * - The main office document (workbook)
     * - Core properties (metadata)
     * - Extended properties (application info)
     * 
     * Relationship IDs (rId1, rId2, rId3) are unique identifiers used to reference
     * these parts from other parts of the package.
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] rels() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
                "</Relationships>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates the docProps/core.xml part (Dublin Core metadata).
     * 
     * Contains standard document metadata using Dublin Core vocabulary:
     * - Title: Document title
     * - Creator: Application/author that created the document
     * - Created/Modified: Timestamps in W3CDTF format (ISO 8601)
     * 
     * This metadata appears in Excel's File > Info panel and document properties.
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] coreProps() {
        // Get current timestamp in ISO 8601 format for created/modified dates
        String now = java.time.OffsetDateTime.now().toString();
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "  <dc:title>Data Migration Quality Report</dc:title>" +
                "  <dc:creator>DataMigrationScanner</dc:creator>" +
                "  <cp:lastModifiedBy>DataMigrationScanner</cp:lastModifiedBy>" +
                "  <dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>" +
                "  <dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>" +
                "</cp:coreProperties>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates the docProps/app.xml part (extended application properties).
     * 
     * Contains application-specific metadata:
     * - Application: Name of the application that created the file
     * - DocSecurity: Security level (0 = no security)
     * - AppVersion: Application version
     * 
     * This metadata is less standardized than core properties and is application-specific.
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] appProps() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">" +
                "  <Application>DataMigrationScanner</Application>" +
                "  <DocSecurity>0</DocSecurity>" +
                "  <AppVersion>1.0</AppVersion>" +
                "</Properties>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates the xl/workbook.xml part (workbook structure).
     * 
     * Defines the workbook structure, listing all sheets with:
     * - name: Sheet name (displayed on tab)
     * - sheetId: Unique numeric ID (must be sequential starting from 1)
     * - r:id: Relationship ID linking to the actual worksheet XML file
     * 
     * Sheet names are XML-attribute-escaped to handle special characters.
     * Relationship IDs (rId1, rId2) must match those in workbook.xml.rels.
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] workbook() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "  <sheets>" +
                "    <sheet name=\"" + escAttr(sheet1Name) + "\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "    <sheet name=\"" + escAttr(sheet2Name) + "\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "  </sheets>" +
                "</workbook>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates the xl/_rels/workbook.xml.rels part (workbook relationships).
     * 
     * Defines relationships from the workbook to its worksheets.
     * Links relationship IDs (rId1, rId2) used in workbook.xml to actual
     * worksheet XML files (sheet1.xml, sheet2.xml).
     * 
     * Relationship IDs must match those referenced in workbook.xml.
     * Target paths are relative to the xl/ directory.
     * 
     * @return UTF-8 encoded XML bytes
     */
    private byte[] workbookRels() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "</Relationships>";
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Generates worksheet XML for a sheet's data.
     * 
     * Creates the XML structure for a worksheet containing:
     * - dimension: Cell range reference (e.g., "A1:G100") for performance optimization
     * - sheetData: All rows and cells with their values
     * 
     * Cell types:
     * - Empty cells: <c r="A1"/> (no value)
     * - Numbers: <c r="A1"><v>123</v></c> (numeric value)
     * - Strings: <c r="A1" t="inlineStr"><is><t>text</t></is></c> (inline string)
     * 
     * Uses inline strings (not shared strings table) for simplicity.
     * Cell references use Excel notation (A1, B1, etc.) calculated from row/column indices.
     * 
     * @param rows List of rows, where each row is a list of cell values
     * @return UTF-8 encoded XML bytes
     * @throws IOException if encoding fails
     */
    private byte[] sheetXml(List<List<Object>> rows) throws IOException {
        // Calculate maximum column count across all rows for dimension
        int maxCols = 0;
        for (List<Object> r : rows) maxCols = Math.max(maxCols, r.size());
        
        // Calculate dimension range (e.g., "A1:G100")
        int lastRow = rows.size();
        String dim = "A1:" + colName(maxCols) + lastRow;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        
        // Dimension helps Excel optimize loading by pre-allocating cell range
        sb.append("<dimension ref=\"").append(dim).append("\"/>");
        
        sb.append("<sheetData>");
        
        // Process each row
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            int rowNum = r + 1; // Excel rows are 1-indexed
            
            sb.append("<row r=\"").append(rowNum).append("\">");
            
            // Process each cell in the row
            for (int c = 0; c < row.size(); c++) {
                Object val = row.get(c);
                // Calculate cell reference (e.g., "A1", "B2")
                String cellRef = colName(c + 1) + rowNum;
                
                if (val == null || val.toString().isEmpty()) {
                    // Empty cell: just reference, no value
                    sb.append("<c r=\"").append(cellRef).append("\"/>");
                } else if (val instanceof Number) {
                    // Numeric cell: store as numeric value
                    sb.append("<c r=\"").append(cellRef).append("\"><v>")
                      .append(val.toString())
                      .append("</v></c>");
                } else {
                    // String cell: use inline string type (not shared strings)
                    sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t>")
                      .append(escText(val.toString()))
                      .append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        sb.append("</worksheet>");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Converts a column number to Excel column name (A, B, ..., Z, AA, AB, ...).
     * 
     * Uses base-26 conversion with 1-based indexing:
     * - 1 -> A
     * - 26 -> Z
     * - 27 -> AA
     * - 28 -> AB
     * 
     * Algorithm: Repeatedly divide by 26, using remainder to determine letter.
     * The result is built right-to-left, then reversed.
     * 
     * @param n Column number (1-based: 1 = column A)
     * @return Excel column name (e.g., "A", "Z", "AA", "AB")
     */
    private static String colName(int n) {
        // 1 -> A, 27 -> AA
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            // Get remainder (0-25) to determine letter
            int rem = (n - 1) % 26;
            // Convert to letter (0=A, 1=B, ..., 25=Z)
            sb.append((char) ('A' + rem));
            // Divide by 26 for next digit
            n = (n - 1) / 26;
        }
        // Reverse because we built it right-to-left
        return sb.reverse().toString();
    }

    /**
     * Escapes special characters for XML attribute values.
     * 
     * XML attributes require escaping of:
     * - & -> &amp;
     * - " -> &quot;
     * - < -> &lt;
     * - > -> &gt;
     * 
     * Used for sheet names and other attribute values.
     * 
     * @param s String to escape
     * @return Escaped string safe for XML attributes
     */
    private static String escAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Escapes special characters for XML text content.
     * 
     * XML text content requires escaping of:
     * - & -> &amp;
     * - < -> &lt;
     * - > -> &gt;
     * 
     * Note: Quotes don't need escaping in text content (only in attributes).
     * Used for cell values and other text content.
     * 
     * @param s String to escape
     * @return Escaped string safe for XML text content
     */
    private static String escText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

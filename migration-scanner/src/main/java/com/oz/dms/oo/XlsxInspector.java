package com.oz.dms.oo;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Inspector for XLSX (Microsoft Excel) files.
 * 
 * XLSX files are ZIP archives containing XML parts. This class:
 * 1. Opens the XLSX file as a ZIP archive
 * 2. Checks for encryption indicators (EncryptedPackage, EncryptionInfo)
 * 3. Extracts sheet count from xl/workbook.xml
 * 
 * This approach avoids external libraries by directly reading OOXML ZIP structure.
 * Uses standard Java XML parsing (javax.xml.parsers) which is part of the JDK.
 */
public class XlsxInspector {
    /**
     * Checks if an XLSX file is password-protected or encrypted.
     * 
     * Encrypted XLSX files contain special parts:
     * - EncryptedPackage: Encrypted content package
     * - EncryptionInfo: Encryption metadata
     * 
     * If either of these parts exists, the file is encrypted.
     * Returns as soon as an encryption indicator is found (early exit optimization).
     * 
     * @param file Path to the XLSX file to check
     * @return true if file is encrypted/password-protected, false otherwise
     * @throws IOException if file cannot be read or is not a valid ZIP archive
     */
    public static boolean isEncrypted(Path file) throws IOException {
        // Read entire file into memory for ZIP processing
        byte[] bytes = Files.readAllBytes(file);
        
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            // Iterate through ZIP entries looking for encryption indicators
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                
                // Check for encryption parts
                // If found, file is encrypted - return immediately (early exit)
                if (name.equalsIgnoreCase("EncryptedPackage") || name.equalsIgnoreCase("EncryptionInfo")) {
                    return true;
                }
                zis.closeEntry();
            }
        }
        // No encryption indicators found
        return false;
    }

    /**
     * Counts the number of worksheets in an XLSX file.
     * 
     * Reads xl/workbook.xml which contains <sheet> elements, one per worksheet.
     * Returns the count of <sheet> elements found.
     * 
     * Returns 0 if:
     * - workbook.xml is not found
     * - workbook.xml cannot be parsed
     * - No sheets are found
     * 
     * @param file Path to the XLSX file to inspect
     * @return Number of worksheets in the workbook, or 0 if cannot be determined
     * @throws IOException if file cannot be read or is not a valid ZIP archive
     */
    public static int sheetCount(Path file) throws IOException {
        // Read entire file into memory for ZIP processing
        byte[] bytes = Files.readAllBytes(file);
        
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            // Iterate through ZIP entries looking for workbook.xml
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                
                // Found workbook.xml - parse it to count sheets
                if (name.equalsIgnoreCase("xl/workbook.xml")) {
                    byte[] xml = zis.readAllBytes();
                    return parseSheetCount(xml);
                }
                zis.closeEntry();
            }
        }
        // workbook.xml not found or parsing failed
        return 0;
    }

    /**
     * Parses the sheet count from xl/workbook.xml.
     * 
     * The workbook.xml file contains a <sheets> element with <sheet> child elements.
     * Each <sheet> element represents one worksheet in the workbook.
     * 
     * Counts all <sheet> elements found in the document.
     * Uses namespace-aware parsing to handle XML namespaces correctly.
     * 
     * Returns 0 if:
     * - XML parsing fails
     * - No <sheet> elements found
     * 
     * @param xml The XML content from xl/workbook.xml as bytes
     * @return Number of <sheet> elements found, or 0 if parsing fails
     */
    private static int parseSheetCount(byte[] xml) {
        try {
            // Create namespace-aware XML parser
            var db = DocumentBuilderFactory.newInstance();
            db.setNamespaceAware(true);
            var doc = db.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            
            // Find all <sheet> elements
            // Each <sheet> represents one worksheet
            var sheets = doc.getElementsByTagName("sheet");
            
            // Return count of sheet elements
            return sheets.getLength();
        } catch (Exception e) {
            // Silently fail - return 0 if parsing fails
            // This allows the scan to continue even if metadata extraction fails
            return 0;
        }
    }
}

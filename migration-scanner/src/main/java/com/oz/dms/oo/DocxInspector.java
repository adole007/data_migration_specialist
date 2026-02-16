package com.oz.dms.oo;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Inspector for DOCX (Microsoft Word) files.
 * 
 * DOCX files are ZIP archives containing XML parts. This class:
 * 1. Opens the DOCX file as a ZIP archive
 * 2. Searches for specific parts (EncryptedPackage, EncryptionInfo, docProps/core.xml, docProps/app.xml)
 * 3. Extracts metadata (author, page count) and encryption status
 * 
 * This approach avoids external libraries by directly reading OOXML ZIP structure.
 * Uses standard Java XML parsing (javax.xml.parsers) which is part of the JDK.
 */
public class DocxInspector {
    /**
     * Inspects a DOCX file to extract metadata and check encryption status.
     * 
     * Reads the entire file into memory, then processes it as a ZIP archive.
     * Searches for specific OOXML parts:
     * - EncryptedPackage/EncryptionInfo: Indicates password protection
     * - docProps/core.xml: Contains author (creator) information
     * - docProps/app.xml: Contains page count
     * 
     * Returns a DocxInfo object with extracted metadata. Fields may be null
     * if the corresponding parts are missing or cannot be parsed.
     * 
     * @param file Path to the DOCX file to inspect
     * @return DocxInfo object containing author, page count, and encryption status
     * @throws IOException if file cannot be read or is not a valid ZIP archive
     */
    public static DocxInfo inspect(Path file) throws IOException {
        // Read entire file into memory for ZIP processing
        // This is acceptable for typical document sizes, but could be optimized
        // for very large files by streaming individual entries
        byte[] bytes = Files.readAllBytes(file);
        
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            // Track what we find while iterating through ZIP entries
            boolean hasEncrypted = false;
            String author = null;
            Integer pages = null;

            // Iterate through all entries in the ZIP archive
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                
                // Check for encryption indicators
                // EncryptedPackage and EncryptionInfo are parts that appear in password-protected files
                if (name.equalsIgnoreCase("EncryptedPackage") || name.equalsIgnoreCase("EncryptionInfo")) {
                    hasEncrypted = true;
                } 
                // Extract author from core properties (Dublin Core metadata)
                else if (name.equalsIgnoreCase("docProps/core.xml")) {
                    byte[] xml = zis.readAllBytes();
                    author = parseAuthor(xml);
                } 
                // Extract page count from application properties
                else if (name.equalsIgnoreCase("docProps/app.xml")) {
                    byte[] xml = zis.readAllBytes();
                    pages = parsePages(xml);
                }
                // Close entry to move to next one
                zis.closeEntry();
            }
            
            // Build result object with extracted information
            DocxInfo info = new DocxInfo();
            info.author = author;
            info.pages = pages;
            info.encrypted = hasEncrypted;
            return info;
        }
    }

    /**
     * Parses the author (creator) from docProps/core.xml.
     * 
     * The core.xml file uses Dublin Core vocabulary with namespace:
     * - Namespace: http://purl.org/dc/elements/1.1/
     * - Element: creator (dc:creator)
     * 
     * Tries namespace-aware parsing first (preferred), then falls back to
     * simple tag name matching if namespace parsing fails.
     * 
     * Returns null if:
     * - XML parsing fails
     * - Creator element not found
     * - Creator element is empty
     * 
     * @param xml The XML content from docProps/core.xml as bytes
     * @return Author name, or null if not found or parsing fails
     */
    private static String parseAuthor(byte[] xml) {
        try {
            // Create namespace-aware XML parser
            var db = DocumentBuilderFactory.newInstance();
            db.setNamespaceAware(true);
            var doc = db.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            
            // Try namespace-aware lookup first (preferred method)
            // Dublin Core namespace: http://purl.org/dc/elements/1.1/
            var nl = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator");
            if (nl.getLength() > 0) {
                return nl.item(0).getTextContent();
            }
            
            // Fallback: try simple tag name matching (dc:creator)
            // Some files may not properly declare namespaces
            var nl2 = doc.getElementsByTagName("dc:creator");
            if (nl2.getLength() > 0) return nl2.item(0).getTextContent();
        } catch (Exception ignored) {
            // Silently fail - return null if parsing fails
            // This allows the scan to continue even if metadata extraction fails
        }
        return null;
    }

    /**
     * Parses the page count from docProps/app.xml.
     * 
     * The app.xml file contains application-specific properties including:
     * - Pages: Total page count of the document
     * 
     * The Pages element is typically in the default namespace (no prefix).
     * Parses the text content as an integer.
     * 
     * Returns null if:
     * - XML parsing fails
     * - Pages element not found
     * - Pages value cannot be parsed as integer
     * 
     * @param xml The XML content from docProps/app.xml as bytes
     * @return Page count as Integer, or null if not found or parsing fails
     */
    private static Integer parsePages(byte[] xml) {
        try {
            // Create namespace-aware XML parser
            var db = DocumentBuilderFactory.newInstance();
            db.setNamespaceAware(true);
            var doc = db.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            
            // Look for Pages element (typically in default namespace)
            var nl = doc.getElementsByTagName("Pages");
            if (nl.getLength() > 0) {
                // Extract text content and parse as integer
                String t = nl.item(0).getTextContent();
                return Integer.parseInt(t.trim());
            }
        } catch (Exception ignored) {
            // Silently fail - return null if parsing fails
            // This allows the scan to continue even if metadata extraction fails
        }
        return null;
    }
}

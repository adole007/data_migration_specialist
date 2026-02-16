package com.oz.dms;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing metadata for a single scanned file.
 * 
 * This class holds all information extracted during the scan:
 * - Basic file information (path, name, size, dates)
 * - File extension (for type classification)
 * - List of issues found during validation and inspection
 * 
 * Fields are public for simplicity, as this is a data transfer object (DTO)
 * used to pass file metadata between scanner, validators, and Excel writer.
 */
public class FileRecord {
    /** Full absolute path to the file */
    public String path;
    
    /** Filename without directory path */
    public String filename;
    
    /** File extension in uppercase without the dot (e.g., "DOCX", "XLSX", "PDF") */
    public String extension;
    
    /** File size in megabytes (or -1 if size could not be determined) */
    public double sizeMb;
    
    /** Creation date formatted as "yyyy-MM-dd HH:mm:ss" (empty string if unavailable) */
    public String created;
    
    /** Last modified date formatted as "yyyy-MM-dd HH:mm:ss" (empty string if unavailable) */
    public String modified;
    
    /** List of issues found during validation and inspection.
     *  Examples: "Path length > 250", "Password-protected or encrypted", "Pages=10"
     *  Empty list means no issues found. */
    public List<String> issues = new ArrayList<>();

    /**
     * Extracts the file extension from a filename.
     * 
     * Returns the extension in uppercase without the dot.
     * Handles edge cases:
     * - Files without extensions return empty string
     * - Files ending with a dot return empty string
     * - Hidden files (starting with dot) are handled correctly
     * 
     * Examples:
     * - "document.docx" -> "DOCX"
     * - "file" -> ""
     * - "archive.tar.gz" -> "GZ" (last extension only)
     * 
     * @param name The filename (with or without path)
     * @return Uppercase extension without dot, or empty string if no extension
     */
    public static String extensionOf(String name) {
        // Find last dot in filename
        int dot = name.lastIndexOf('.');
        // Check that dot exists and is not the last character
        if (dot >= 0 && dot < name.length()-1) {
            // Extract substring after dot and convert to uppercase
            return name.substring(dot+1).toUpperCase();
        }
        // No valid extension found
        return "";
    }

    /**
     * Finds special characters in a filename that may cause migration issues.
     * 
     * Checks for Windows-problematic characters: < > : " / \ | ? *
     * These characters can cause issues when migrating to different file systems
     * or cloud storage systems that have stricter naming rules.
     * 
     * Returns a string containing all found special characters (may contain duplicates).
     * Empty string means no problematic characters found.
     * 
     * @param name The filename to check
     * @return String containing all special characters found, or empty string if none
     */
    public static String findSpecials(String name) {
        // Characters that are problematic on Windows file systems
        String specials = "<>:\"/\\|?*";
        StringBuilder sb = new StringBuilder();
        
        // Check each character in the filename
        for (char c : name.toCharArray()) {
            // If character is in the specials list, add it to result
            if (specials.indexOf(c) >= 0) sb.append(c);
        }
        return sb.toString();
    }
}

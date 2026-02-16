package com.oz.dms;

import com.oz.dms.excel.SimpleXlsxWriter;
import com.oz.dms.oo.DocxInfo;
import com.oz.dms.oo.DocxInspector;
import com.oz.dms.oo.XlsxInspector;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main entry point for the Data Migration Scanner application.
 * 
 * This class orchestrates the entire scanning process:
 * 1. Parses command-line arguments and configuration
 * 2. Recursively scans a directory tree for files
 * 3. Extracts basic file metadata (path, size, dates)
 * 4. Performs validation checks for migration blockers
 * 5. Extracts document-specific metadata (DOCX/XLSX)
 * 6. Generates an Excel report with inventory and summary sheets
 * 
 * The scanner is designed to be resilient - it continues processing even if
 * individual files fail, logging warnings and marking them as inaccessible.
 */
public class DataMigrationScanner {
    /**
     * Main entry point for the application.
     * 
     * Handles command-line argument parsing, configuration loading, and error handling.
     * Exits with appropriate status codes:
     * - Exit code 2: Configuration errors (invalid arguments or missing required parameters)
     * - Exit code 1: Runtime errors (I/O failures, unexpected exceptions)
     * 
     * @param args Command-line arguments (--source, --maxSizeMb, --output, --config)
     */
    public static void main(String[] args) {
        try {
            // Parse configuration from command-line arguments and/or properties file
            // CLI arguments take precedence over properties file values
            Config cfg = Config.fromArgs(args);
            
            // Display configuration summary for user verification
            System.out.println("Data Migration Scanner");
            System.out.println("- Source: " + cfg.sourceDir);
            System.out.println("- Max size (MB): " + cfg.maxSizeMb);
            System.out.println("- Output: " + cfg.outputFile);

            // Create scanner instance and execute the scan
            var scanner = new DataMigrationScanner(cfg);
            scanner.run();
        } catch (IllegalArgumentException e) {
            // Configuration errors: invalid paths, missing required parameters, etc.
            // Print error and usage instructions, then exit with code 2
            System.err.println("Configuration error: " + e.getMessage());
            printUsage();
            System.exit(2);
        } catch (Exception e) {
            // Unexpected runtime errors: I/O failures, parsing errors, etc.
            // Print full stack trace for debugging, then exit with code 1
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Prints usage instructions to help users understand command-line options.
     * Called when configuration errors occur to guide users on correct usage.
     */
    private static void printUsage() {
        System.out.println("\nUsage:");
        System.out.println("  java -cp out com.oz.dms.DataMigrationScanner --source <path> [--maxSizeMb 50] [--output scan-report.xlsx] [--config config.properties]");
        System.out.println("\nArgs:");
        System.out.println("  --source       Source directory to scan (required unless provided in --config)");
        System.out.println("  --maxSizeMb    Maximum file size before flagging (default 50)");
        System.out.println("  --output       Output Excel filename (default scan-report.xlsx)");
        System.out.println("  --config       Optional properties file (keys: sourceDir, maxSizeMb, outputFile). CLI args override file.");
    }

    /** Configuration settings loaded from CLI args or properties file */
    private final Config cfg;
    
    /**
     * Date/time formatter for converting file timestamps to human-readable strings.
     * Uses system default timezone to display dates in the user's local timezone.
     * Format: "yyyy-MM-dd HH:mm:ss" (e.g., "2026-02-13 14:30:45")
     */
    private final DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Constructs a new scanner instance with the given configuration.
     * 
     * @param cfg Configuration object containing source directory, size threshold, and output filename
     */
    public DataMigrationScanner(Config cfg) {
        this.cfg = cfg;
    }

    /**
     * Main execution method that orchestrates the entire scanning process.
     * 
     * Process flow:
     * 1. Recursively walks the directory tree using Files.walkFileTree()
     * 2. Processes each file to extract metadata and perform validations
     * 3. Handles errors gracefully by creating fallback records for inaccessible files
     * 4. Calculates summary statistics from all processed files
     * 5. Generates Excel report with inventory and summary sheets
     * 
     * Uses AtomicLong for totalSizeBytes to allow thread-safe accumulation if
     * the file visitor were to be parallelized in the future.
     * 
     * @throws IOException if the directory cannot be accessed or the Excel file cannot be written
     */
    public void run() throws IOException {
        // Collection to store metadata for all scanned files
        List<FileRecord> inventory = new ArrayList<>();
        
        // Atomic counter for total size (thread-safe, though currently single-threaded)
        // Using AtomicLong allows for potential future parallelization
        AtomicLong totalSizeBytes = new AtomicLong();
        
        // Track start time for performance reporting
        long started = System.currentTimeMillis();

        System.out.println("Scanning...");
        try {
            // Recursively walk the directory tree
            // SimpleFileVisitor provides default implementations for directory traversal
            Files.walkFileTree(cfg.sourceDir, new SimpleFileVisitor<>() {
                /**
                 * Called for each file encountered during the tree walk.
                 * Processes the file to extract metadata and perform validations.
                 * If processing fails, creates a fallback record with error information.
                 * 
                 * @param file The file path being visited
                 * @param attrs Basic file attributes (size, timestamps, etc.)
                 * @return CONTINUE to keep traversing the tree
                 */
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        // Process file: extract metadata, validate, check for issues
                        inventory.add(processFile(file, attrs));
                        // Accumulate total size for summary statistics
                        totalSizeBytes.addAndGet(attrs.size());
                    } catch (Exception ex) {
                        // File processing failed (permissions, corruption, etc.)
                        // Log warning but continue scanning - don't fail the entire scan
                        System.err.println("WARN: Failed to process file: " + file + " -> " + ex.getMessage());
                        // Create minimal record with error information
                        FileRecord fr = fallbackRecord(file);
                        fr.issues.add("Inaccessible: " + ex.getClass().getSimpleName());
                        inventory.add(fr);
                    }
                    return FileVisitResult.CONTINUE;
                }

                /**
                 * Called when a file cannot be accessed (permissions, locked, etc.).
                 * Creates a fallback record so the file is still included in the report
                 * with an "inaccessible" issue flag.
                 * 
                 * @param file The file path that failed to access
                 * @param exc The IOException that occurred (may be null)
                 * @return CONTINUE to keep scanning other files
                 */
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Log the access failure
                    System.err.println("WARN: Cannot access file: " + file + " -> " + (exc!=null?exc.getMessage():"unknown"));
                    // Create fallback record with minimal information
                    FileRecord fr = fallbackRecord(file);
                    fr.issues.add("Inaccessible: " + (exc!=null?exc.getClass().getSimpleName():"unknown"));
                    inventory.add(fr);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Re-throw I/O errors from walkFileTree (e.g., directory doesn't exist)
            // These are fatal errors that should stop execution
            throw e;
        }

        // Calculate and display scan statistics
        long durationMs = System.currentTimeMillis() - started;
        System.out.printf("Scanned %,d files (%.2f MB) in %.1f s%n", inventory.size(), bytesToMb(totalSizeBytes.get()), durationMs/1000.0);

        // Build summary statistics from all processed files
        // Summary includes: total files, total size, files with issues, count by file type
        Summary summary = Summary.from(inventory, totalSizeBytes.get());

        // Generate Excel report with two sheets: File Inventory and Summary
        System.out.println("Writing Excel report: " + cfg.outputFile);
        SimpleXlsxWriter xw = new SimpleXlsxWriter();
        xw.addInventorySheet("File Inventory", inventory);
        xw.addSummarySheet("Summary", summary);
        xw.write(cfg.outputFile);
        System.out.println("Done.");
    }

    /**
     * Creates a minimal FileRecord for files that cannot be fully processed.
     * 
     * Used when:
     * - File access fails (permissions, locked, etc.)
     * - File processing throws an exception
     * 
     * Sets size to -1 and dates to empty strings to indicate missing data.
     * The file will still appear in the inventory with an "Inaccessible" issue flag.
     * 
     * @param file The file path that failed to process
     * @return A FileRecord with minimal information (path, filename, extension)
     */
    private FileRecord fallbackRecord(Path file) {
        FileRecord fr = new FileRecord();
        // Store absolute path for reporting
        fr.path = file.toAbsolutePath().toString();
        // Extract filename, handling edge case where getFileName() might return null
        fr.filename = file.getFileName() != null ? file.getFileName().toString() : file.toString();
        // Extract file extension (uppercase, without dot)
        fr.extension = FileRecord.extensionOf(fr.filename);
        // Set to -1 to indicate size could not be determined
        fr.sizeMb = -1;
        // Empty strings indicate dates could not be retrieved
        fr.created = "";
        fr.modified = "";
        return fr;
    }

    /**
     * Processes a single file to extract metadata and perform validations.
     * 
     * This method:
     * 1. Extracts basic file metadata (path, name, size, dates)
     * 2. Performs validation checks for migration blockers
     * 3. Extracts document-specific metadata for DOCX/XLSX files
     * 
     * Document metadata (author, pages, sheet count) is stored in the issues list
     * rather than separate columns to keep the Excel schema simple while still
     * capturing useful information.
     * 
     * @param file The file path to process
     * @param attrs Basic file attributes from the file system
     * @return A FileRecord containing all extracted metadata and validation issues
     * @throws IOException if file cannot be read (though this is usually caught by the caller)
     */
    private FileRecord processFile(Path file, BasicFileAttributes attrs) throws IOException {
        FileRecord fr = new FileRecord();
        
        // Extract basic file information
        fr.path = file.toAbsolutePath().toString();
        fr.filename = file.getFileName().toString();
        fr.extension = FileRecord.extensionOf(fr.filename);
        
        // Convert file size from bytes to megabytes for readability
        fr.sizeMb = bytesToMb(attrs.size());
        
        // Convert timestamps from epoch milliseconds to formatted date strings
        // Using system default timezone so dates are displayed in user's local time
        fr.created = dtFmt.format(Instant.ofEpochMilli(attrs.creationTime().toMillis()));
        fr.modified = dtFmt.format(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));

        // Perform validation checks for migration blockers
        // Checks: path length, special characters, file size threshold
        validate(fr, file, attrs.size());

        // Extract document-specific metadata for Office Open XML formats
        // DOCX/XLSX are chosen as alternatives to PDF since they're common document formats
        // and can be inspected without external libraries by reading ZIP contents
        try {
            if (fr.extension.equals("DOCX")) {
                // Inspect DOCX file: extract author, page count, encryption status
                DocxInfo info = DocxInspector.inspect(file);
                
                // Encryption is a migration blocker - flag it as an issue
                if (info.encrypted) {
                    fr.issues.add("Password-protected or encrypted");
                }
                
                // Store metadata in issues list rather than separate columns
                // This keeps the Excel schema simple while still capturing useful info
                if (info.pages != null) {
                    fr.issues.add("Pages=" + info.pages);
                }
                if (info.author != null && !info.author.isBlank()) {
                    fr.issues.add("Author='" + info.author + "'");
                }
            } else if (fr.extension.equals("XLSX")) {
                // Inspect XLSX file: count sheets, check encryption
                int sheetCount = XlsxInspector.sheetCount(file);
                boolean enc = XlsxInspector.isEncrypted(file);
                
                // Encryption is a migration blocker
                if (enc) fr.issues.add("Password-protected or encrypted");
                
                // Store sheet count in issues list for reference
                fr.issues.add("Sheets=" + sheetCount);
            }
        } catch (Exception e) {
            // Metadata extraction failed (corrupted file, unexpected format, etc.)
            // Log warning but don't fail the entire scan - just mark this file
            System.err.println("WARN: Metadata parse failed for " + file + ": " + e.getMessage());
            fr.issues.add("Metadata parse failed");
        }
        return fr;
    }

    /**
     * Validates a file against migration blocker criteria.
     * 
     * Checks for common issues that can prevent successful file migration:
     * 1. Path length > 250 characters (Windows path length limit)
     * 2. Special characters in filename (can cause issues in some file systems)
     * 3. File size exceeding configured threshold (may cause performance issues)
     * 
     * All issues are added to the FileRecord's issues list for reporting.
     * 
     * @param fr The FileRecord to populate with validation issues
     * @param file The file path (used for path length check)
     * @param sizeBytes The file size in bytes (converted to MB for comparison)
     */
    private void validate(FileRecord fr, Path file, long sizeBytes) {
        // Check path length: Windows has a 260 character limit (including drive letter and null terminator)
        // Using 250 as threshold to leave buffer for destination path differences
        if (fr.path.length() > 250) {
            fr.issues.add("Path length > 250");
        }
        
        // Check for special characters that may cause issues in target file systems
        // Characters like < > : " / \ | ? * are problematic on Windows
        String specials = FileRecord.findSpecials(fr.filename);
        if (!specials.isEmpty()) {
            fr.issues.add("Special chars in name: " + specials);
        }
        
        // Check file size against configured threshold
        // Large files may cause migration performance issues or timeout
        if (bytesToMb(sizeBytes) > cfg.maxSizeMb) {
            fr.issues.add("Size > " + cfg.maxSizeMb + "MB");
        }
    }

    /**
     * Converts bytes to megabytes for human-readable display.
     * 
     * Uses binary conversion (1024 bytes = 1 KB, 1024 KB = 1 MB) which is
     * standard for file size reporting in operating systems.
     * 
     * @param size File size in bytes
     * @return File size in megabytes as a decimal value
     */
    private static double bytesToMb(long size) {
        return size / 1024.0 / 1024.0;
    }
}

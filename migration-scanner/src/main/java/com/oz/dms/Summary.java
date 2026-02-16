package com.oz.dms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary statistics calculated from all scanned files.
 * 
 * This class aggregates statistics across the entire scan:
 * - Total file count and total size
 * - Count of files with validation issues
 * - Breakdown of files by type (extension)
 * 
 * Used to populate the Summary sheet in the Excel report, providing
 * a high-level overview of the scanned directory.
 */
public class Summary {
    /** Total number of files scanned */
    public long totalFiles;
    
    /** Total size of all files in megabytes */
    public double totalSizeMb;
    
    /** Number of files that have at least one issue flag */
    public long filesWithIssues;
    
    /** Map of file extension to count of files with that extension.
     *  Files without extensions are counted under key "(none)". */
    public Map<String, Long> countByType = new HashMap<>();

    /**
     * Factory method that calculates summary statistics from a list of file records.
     * 
     * Processes all FileRecord objects to compute:
     * - Total file count (size of records list)
     * - Total size in MB (from pre-calculated totalSizeBytes)
     * - Count of files with issues (any file with non-empty issues list)
     * - Count by file type (grouping by extension)
     * 
     * Uses Map.merge() to efficiently count files by type, incrementing
     * the count for each extension encountered.
     * 
     * @param records List of all FileRecord objects from the scan
     * @param totalSizeBytes Total size of all files in bytes (pre-calculated during scan)
     * @return Summary object with all calculated statistics
     */
    public static Summary from(List<FileRecord> records, long totalSizeBytes) {
        Summary s = new Summary();
        
        // Total files is simply the count of records
        s.totalFiles = records.size();
        
        // Convert total size from bytes to megabytes (binary conversion: 1024^2)
        s.totalSizeMb = totalSizeBytes / 1024.0 / 1024.0;
        
        // Count files that have at least one issue
        long issues = 0;
        for (FileRecord r : records) {
            // If issues list is not empty, increment counter
            if (!r.issues.isEmpty()) issues++;
            
            // Group files by extension for type breakdown
            // Handle null/blank extensions by using "(none)" as the key
            String type = r.extension == null || r.extension.isBlank() ? "(none)" : r.extension;
            
            // Increment count for this file type
            // merge() adds 1 to existing count, or sets to 1 if type not seen before
            s.countByType.merge(type, 1L, Long::sum);
        }
        s.filesWithIssues = issues;
        return s;
    }
}

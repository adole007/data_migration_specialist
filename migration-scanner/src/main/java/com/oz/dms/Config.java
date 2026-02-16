package com.oz.dms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration class that holds scanner settings.
 * 
 * Handles loading configuration from:
 * 1. Command-line arguments (highest priority)
 * 2. Properties file (fallback if CLI arg not provided)
 * 
 * CLI arguments always override properties file values, allowing users to
 * override defaults or properties file settings on a per-run basis.
 * 
 * This class is package-private (not public) as it's an internal implementation detail.
 */
class Config {
    /** Source directory to scan - must exist and be a directory */
    final Path sourceDir;
    
    /** Maximum file size threshold in MB - files exceeding this are flagged */
    final int maxSizeMb;
    
    /** Output Excel filename for the scan report */
    final String outputFile;

    /**
     * Private constructor - use fromArgs() factory method instead.
     * 
     * @param sourceDir Source directory path (validated to exist)
     * @param maxSizeMb Maximum file size threshold in MB
     * @param outputFile Output Excel filename
     */
    private Config(Path sourceDir, int maxSizeMb, String outputFile) {
        this.sourceDir = sourceDir;
        this.maxSizeMb = maxSizeMb;
        this.outputFile = outputFile;
    }

    /**
     * Factory method that creates a Config instance from command-line arguments.
     * 
     * Configuration precedence (highest to lowest):
     * 1. CLI arguments (--source, --maxSizeMb, --output)
     * 2. Properties file values (if --config specified)
     * 3. Default values (maxSizeMb=50, outputFile="scan-report.xlsx")
     * 
     * Validates that:
     * - Source directory is provided (either via CLI or properties file)
     * - Source directory exists and is actually a directory
     * - maxSizeMb is a valid integer if provided
     * 
     * @param args Command-line arguments array
     * @return Configured Config instance
     * @throws IllegalArgumentException if configuration is invalid or missing required parameters
     */
    static Config fromArgs(String[] args) {
        // Parse command-line arguments into a map
        Map<String,String> cli = parseArgs(args);

        // Load properties file if specified via --config or --properties
        // Properties file provides fallback values that can be overridden by CLI args
        Properties props = new Properties();
        String cfgPath = cli.getOrDefault("config", cli.getOrDefault("properties", null));
        if (cfgPath != null && !cfgPath.isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(cfgPath))) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read config file: " + cfgPath + ": " + e.getMessage());
            }
        }

        // Source directory: required parameter
        // Check CLI first, then properties file (key: "sourceDir")
        String source = cli.getOrDefault("source", props.getProperty("sourceDir"));
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("--source not provided and not found in properties file");
        }
        
        // Validate that source directory exists and is actually a directory
        // Normalize path to resolve ".." and "." components, convert to absolute path
        Path sourceDir = Path.of(source).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }

        // Max size threshold: optional, defaults to 50 MB
        // Check CLI first, then properties file
        int maxSizeMb = 50; // Default value
        String ms = cli.getOrDefault("maxSizeMb", props.getProperty("maxSizeMb"));
        if (ms != null && !ms.isBlank()) {
            try { 
                maxSizeMb = Integer.parseInt(ms); 
            } catch (NumberFormatException e) { 
                throw new IllegalArgumentException("Invalid maxSizeMb: " + ms); 
            }
        }

        // Output filename: optional, defaults to "scan-report.xlsx"
        // Check CLI first, then properties file, then use default
        String output = cli.getOrDefault("output", props.getProperty("outputFile", "scan-report.xlsx"));

        return new Config(sourceDir, maxSizeMb, output);
    }

    /**
     * Parses command-line arguments into a key-value map.
     * 
     * Supports three argument formats:
     * 1. --key=value (inline value)
     * 2. --key value (separate value argument)
     * 3. --key (boolean flag, value becomes "true")
     * 
     * Examples:
     * - "--source=C:\\Data" -> {source: "C:\\Data"}
     * - "--source C:\\Data" -> {source: "C:\\Data"}
     * - "--verbose" -> {verbose: "true"}
     * 
     * Only arguments starting with "--" are parsed; others are ignored.
     * This allows flexibility in argument ordering and format.
     * 
     * @param args Command-line arguments array
     * @return Map of argument names to values (all keys without "--" prefix)
     */
    private static Map<String,String> parseArgs(String[] args) {
        Map<String,String> m = new HashMap<>();
        for (int i=0; i<args.length; i++) {
            String a = args[i];
            // Only process arguments starting with "--"
            if (a.startsWith("--")) {
                // Remove "--" prefix to get the key name
                String k = a.substring(2);
                String v = null;
                
                // Check for inline format: --key=value
                int eq = k.indexOf('=');
                if (eq >= 0) {
                    // Split on '=': key=value
                    v = k.substring(eq+1);
                    k = k.substring(0, eq);
                } else if (i+1 < args.length && !args[i+1].startsWith("--")) {
                    // Separate value format: --key value
                    // Next argument is the value (if it doesn't start with "--")
                    v = args[++i];
                } else {
                    // Boolean flag format: --key (no value)
                    // Treat as flag with value "true"
                    v = "true";
                }
                m.put(k, v);
            }
        }
        return m;
    }
}

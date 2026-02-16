# Data Migration Specialist

Java command-line scanner that inventories a directory, extracts basic and document-specific metadata (DOCX/XLSX), flags migration blockers, and writes an Excel (.xlsx) quality report with an Inventory and a Summary sheet.

## Repository layout
- `migration-scanner/` — Java sources, scripts, and sample configs/files
  - `src/main/java/com/oz/dms/...` — scanner implementation
  - `scripts/build.ps1`, `scripts/run.ps1` — Windows PowerShell helpers
  - `config.properties` — example config
  - `sample-scan-report.xlsx`, `sample-scan-report-java.xlsx` — example outputs
- `out/` — build output (class files) when compiled locally

## Features
- Recursive scan of a source directory
- Tracks: full path, filename, size (MB), type, created/modified dates
- DOCX: author and page count (via OOXML core/app parts), encryption detection
- XLSX: sheet count, encryption detection
- Validation rules: path length > 250, special characters in filename, file size > threshold
- Excel output with two sheets: File Inventory and Summary
- Configurable via CLI args or a properties file
- Resilient: logs and continues on individual file failures

## Requirements
- Java 17+ (tested on Java 21)
- No Maven/Gradle required; uses only the JDK tools (javac/java)

## How to run
Run from the `migration-scanner/` directory.

Windows (PowerShell):

```powershell
# 1) Build
cd migration-scanner
pwsh scripts/build.ps1

# 2) Run with explicit args
java -cp out com.oz.dms.DataMigrationScanner --source "C:\\Data" --maxSizeMb 50 --output scan-report.xlsx

# Or use the helper script (choose ONE)
#   a) Args
pwsh scripts/run.ps1 -Source "C:\\Data" -MaxSizeMb 50 -Output scan-report.xlsx
#   b) Properties file (CLI overrides file values if both are provided)
pwsh scripts/run.ps1 -Config config.properties
```

macOS/Linux (bash):

```bash
# 1) Build
cd migration-scanner
mkdir -p out
javac -encoding UTF-8 -source 17 -target 17 -d out $(find src/main/java -name "*.java")

# 2) Run
java -cp out com.oz.dms.DataMigrationScanner --source "." --maxSizeMb 50 --output scan-report.xlsx
```

The Excel report (e.g., `scan-report.xlsx`) will be created in `migration-scanner/`.

## Quick start (Windows PowerShell)
From the repo root:

1) Build
- `cd migration-scanner`
- `./scripts/build.ps1` (or `pwsh scripts/build.ps1`)

2) Run (choose ONE)
- Direct args:
  `pwsh scripts/run.ps1 -Source "C:\\Data" -MaxSizeMb 50 -Output scan-report.xlsx`
- Or via properties file:
  `pwsh scripts/run.ps1 -Config config.properties`

The Excel report (e.g., `scan-report.xlsx`) will be created in `migration-scanner/`.

## Quick start (macOS/Linux bash)
From `migration-scanner/`:

- Compile:
  `mkdir -p out && javac -encoding UTF-8 -source 17 -target 17 -d out $(find src/main/java -name "*.java")`
- Run with args:
  `java -cp out com.oz.dms.DataMigrationScanner --source "." --maxSizeMb 50 --output scan-report.xlsx`
- Or with a properties file:
  `java -cp out com.oz.dms.DataMigrationScanner --config config.properties`

## Configuration
CLI flags (properties values can be provided via `--config` and are overridden by CLI flags):
- `--source <path>`: Source directory to scan (required unless in properties)
- `--maxSizeMb <int>`: Max size threshold before flagging (default 50)
- `--output <file>`: Output Excel filename (default `scan-report.xlsx`)
- `--config <file>`: Optional `.properties` file with keys `sourceDir`, `maxSizeMb`, `outputFile`

Example `config.properties`:
```
sourceDir=.
maxSizeMb=50
outputFile=scan-report.xlsx
```

## Output
Excel file with two sheets:
- File Inventory: Path, Filename, Size (MB), Type, Created Date, Modified Date, Issues Found
- Summary: total files scanned, total size, count by file type, count of files with issues

## Architecture & internals
See `migration-scanner/ARCHITECTURE_DIAGRAM.md` for a detailed flow of components:
- Scanner orchestrates traversal and validation (`DataMigrationScanner`)
- OOXML inspectors parse DOCX/XLSX metadata without external libs (`DocxInspector`, `XlsxInspector`)
- Minimal XLSX writer builds the report (`SimpleXlsxWriter`)

Notes:
- Encryption detection for OOXML is based on presence of `EncryptedPackage`/`EncryptionInfo` parts.
- The XLSX writer intentionally supports a minimal feature set (inline strings, numbers) for zero-dependency builds.

## Housekeeping
- Consider adding a `.gitignore` to exclude build artifacts like `out/` and `*.class` from source control.

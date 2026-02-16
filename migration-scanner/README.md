<<<<<<< HEAD
# Data Migration Scanner

Java command-line tool that scans a directory, extracts basic and document-specific metadata (DOCX/XLSX), validates migration blockers, and outputs an Excel (.xlsx) quality report with an Inventory and a Summary sheet.

## Features
- Recursive scan of a source directory
- Tracks: full path, filename, size (MB), type, created/modified dates
- DOCX: author and page count (via app.xml), encryption detection
- XLSX: sheet count, encryption detection
- Validation rules: path length > 250, special characters in filename, file size > threshold, access restrictions
- Excel output with two sheets: File Inventory and Summary
- Configurable via CLI args or a properties file
- Logs progress and continues on individual file failures

## Requirements
- Java 17+ (tested on Java 21)
- No external build tools required (no Maven/Gradle); uses only the JDK

## Build
On Windows PowerShell:

```powershell
# From the project root (migration-scanner)
$src = Get-ChildItem -Recurse -Filter *.java | ForEach-Object FullName
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -source 17 -target 17 -d out $src
```

On macOS/Linux (bash):

```bash
# From the project root (migration-scanner)
mkdir -p out
javac -encoding UTF-8 -source 17 -target 17 -d out $(find src/main/java -name "*.java")
```

## Run
Use CLI flags or a properties file (CLI overrides file):

```powershell
# Example: scan C:\\Data with 50MB threshold and write scan-report.xlsx
java -cp out com.oz.dms.DataMigrationScanner --source "C:\\Data" --maxSizeMb 50 --output scan-report.xlsx

# Or via properties file
java -cp out com.oz.dms.DataMigrationScanner --config config.properties
```

`config.properties` example:

```
sourceDir=C:\\Data
maxSizeMb=50
outputFile=scan-report.xlsx
```

## Output
- File `scan-report.xlsx` with two sheets:
  - File Inventory: Path, Filename, Size (MB), Type, Created Date, Modified Date, Issues Found
  - Summary: total files scanned, total size, count by file type, count of files with issues

## Notes
- This implementation supports DOCX/XLSX (as allowed by the task) without external libraries by directly reading OOXML ZIP parts. PDF-specific metadata is not included.
- Encrypted/password-protected detection for OOXML is based on the presence of `EncryptedPackage`/`EncryptionInfo` parts.
- The Excel writer is a minimal OOXML implementation that writes inline strings and numeric values only.

## Example command
```powershell
java -cp out com.oz.dms.DataMigrationScanner --source "." --maxSizeMb 50 --output scan-report.xlsx
=======
# Data Migration Scanner

Java command-line tool that scans a directory, extracts basic and document-specific metadata (DOCX/XLSX), validates migration blockers, and outputs an Excel (.xlsx) quality report with an Inventory and a Summary sheet.

## Features
- Recursive scan of a source directory
- Tracks: full path, filename, size (MB), type, created/modified dates
- DOCX: author and page count (via app.xml), encryption detection
- XLSX: sheet count, encryption detection
- Validation rules: path length > 250, special characters in filename, file size > threshold, access restrictions
- Excel output with two sheets: File Inventory and Summary
- Configurable via CLI args or a properties file
- Logs progress and continues on individual file failures

## Requirements
- Java 17+ (tested on Java 21)
- No external build tools required (no Maven/Gradle); uses only the JDK

## Build
On Windows PowerShell:

```powershell
# From the project root (migration-scanner)
$src = Get-ChildItem -Recurse -Filter *.java | ForEach-Object FullName
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -source 17 -target 17 -d out $src
```

On macOS/Linux (bash):

```bash
# From the project root (migration-scanner)
mkdir -p out
javac -encoding UTF-8 -source 17 -target 17 -d out $(find src/main/java -name "*.java")
```

## Run
Use CLI flags or a properties file (CLI overrides file):

```powershell
# Example: scan C:\\Data with 50MB threshold and write scan-report.xlsx
java -cp out com.oz.dms.DataMigrationScanner --source "C:\\Data" --maxSizeMb 50 --output scan-report.xlsx

# Or via properties file
java -cp out com.oz.dms.DataMigrationScanner --config config.properties
```

`config.properties` example:

```
sourceDir=C:\\Data
maxSizeMb=50
outputFile=scan-report.xlsx
```

## Output
- File `scan-report.xlsx` with two sheets:
  - File Inventory: Path, Filename, Size (MB), Type, Created Date, Modified Date, Issues Found
  - Summary: total files scanned, total size, count by file type, count of files with issues

## Notes
- This implementation supports DOCX/XLSX (as allowed by the task) without external libraries by directly reading OOXML ZIP parts. PDF-specific metadata is not included.
- Encrypted/password-protected detection for OOXML is based on the presence of `EncryptedPackage`/`EncryptionInfo` parts.
- The Excel writer is a minimal OOXML implementation that writes inline strings and numeric values only.

## Example command
```powershell
java -cp out com.oz.dms.DataMigrationScanner --source "." --maxSizeMb 50 --output scan-report.xlsx
>>>>>>> c27f44f (Initial commit from genesis_task)
```
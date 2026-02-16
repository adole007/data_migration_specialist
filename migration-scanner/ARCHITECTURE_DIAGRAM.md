<<<<<<< HEAD
# Architecture Diagram - Data Migration Scanner

## System Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Command Line / Properties File                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   Config.java    │
                    │  Parse & Validate│
                    └────────┬─────────┘
                             │
                             ▼
        ┌────────────────────────────────────────────┐
        │     DataMigrationScanner.run()             │
        │  ┌──────────────────────────────────────┐  │
        │  │  Files.walkFileTree()               │  │
        │  │  Recursive Directory Traversal      │  │
        │  └──────────────┬───────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  For Each File:                     │  │
        │  │  1. processFile()                   │  │
        │  │     - Extract basic metadata        │  │
        │  │     - validate()                   │  │
        │  │     - Check document type           │  │
        │  │  2. Document Inspection (if DOCX/XLSX)│
        │  │  3. Create FileRecord               │  │
        │  └──────────────┬──────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  Summary.from()                   │  │
        │  │  Aggregate Statistics             │  │
        │  └──────────────┬───────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  SimpleXlsxWriter.write()          │  │
        │  │  Generate Excel Report            │  │
        │  └────────────────────────────────────┘  │
        └────────────────────────────────────────────┘
```

## File Processing Pipeline

```
┌─────────────┐
│   File      │
│   Path      │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Extract Basic Metadata             │
│  - Path, Filename, Extension        │
│  - Size (bytes → MB)                │
│  - Created/Modified Dates           │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Validation Checks                   │
│  - Path length > 250?               │
│  - Special characters?               │
│  - Size > threshold?                 │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Document Type Check                │
│  ┌──────────┐    ┌──────────┐      │
│  │  DOCX?   │    │  XLSX?   │      │
│  └────┬─────┘    └────┬─────┘      │
│       │               │             │
│       ▼               ▼             │
│  DocxInspector  XlsxInspector      │
│  - Author       - Sheet Count        │
│  - Pages        - Encryption        │
│  - Encryption                        │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  FileRecord                          │
│  - Metadata                          │
│  - Issues List                       │
└──────────────────────────────────────┘
```

## Document Inspection (DOCX Example)

```
┌─────────────┐
│  DOCX File  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Read as ZIP Archive                │
│  ZipInputStream                     │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Iterate ZIP Entries                │
│  ┌──────────────────────────────┐  │
│  │ EncryptedPackage?            │  │
│  │ EncryptionInfo?               │  │
│  │ → Set encrypted = true        │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ docProps/core.xml?           │  │
│  │ → Parse XML, extract author  │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ docProps/app.xml?            │  │
│  │ → Parse XML, extract pages    │  │
│  └──────────────────────────────┘  │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  DocxInfo                           │
│  - author: String                   │
│  - pages: Integer                   │
│  - encrypted: boolean                │
└──────────────────────────────────────┘
```

## Excel Generation Process

```
┌─────────────────────────────────────┐
│  SimpleXlsxWriter                  │
│  ┌──────────────────────────────┐  │
│  │ addInventorySheet()          │  │
│  │ - Build File Inventory rows  │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ addSummarySheet()            │  │
│  │ - Build Summary rows         │  │
│  └──────────────────────────────┘  │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  write() - Create ZIP Archive       │
│  ZipOutputStream                    │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Write OOXML Parts:                 │
│  1. [Content_Types].xml             │
│  2. _rels/.rels                    │
│  3. docProps/core.xml              │
│  4. docProps/app.xml               │
│  5. xl/workbook.xml                │
│  6. xl/_rels/workbook.xml.rels     │
│  7. xl/worksheets/sheet1.xml        │
│  8. xl/worksheets/sheet2.xml         │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────┐
│  .xlsx File │
└─────────────┘
```

## Class Relationships

```
DataMigrationScanner
    │
    ├──► Config (creates)
    │
    ├──► FileRecord (creates for each file)
    │       │
    │       ├──► Uses FileRecord.extensionOf()
    │       └──► Uses FileRecord.findSpecials()
    │
    ├──► DocxInspector.inspect() (if DOCX)
    │       └──► Returns DocxInfo
    │
    ├──► XlsxInspector (if XLSX)
    │       ├──► XlsxInspector.sheetCount()
    │       └──► XlsxInspector.isEncrypted()
    │
    ├──► Summary.from() (aggregates FileRecords)
    │
    └──► SimpleXlsxWriter (generates Excel)
            ├──► addInventorySheet(FileRecords)
            ├──► addSummarySheet(Summary)
            └──► write(outputFile)
```

## Data Flow

```
CLI Args/Properties
    │
    ▼
Config Object
    │
    ├── sourceDir: Path
    ├── maxSizeMb: int
    └── outputFile: String
    │
    ▼
List<FileRecord>
    │
    ├── FileRecord 1
    │   ├── path: String
    │   ├── filename: String
    │   ├── sizeMb: double
    │   ├── created/modified: String
    │   └── issues: List<String>
    │
    ├── FileRecord 2
    │   └── ...
    │
    └── FileRecord N
        └── ...
    │
    ▼
Summary Object
    ├── totalFiles: long
    ├── totalSizeMb: double
    ├── filesWithIssues: long
    └── countByType: Map<String, Long>
    │
    ▼
Excel File (.xlsx)
    ├── Sheet 1: File Inventory
    └── Sheet 2: Summary
```

## Error Handling Flow

```
File Processing
    │
    ├──► Success
    │       └──► FileRecord with metadata
    │
    ├──► Exception in processFile()
    │       └──► Fallback FileRecord
    │           └──► issues.add("Inaccessible: ExceptionType")
    │
    └──► visitFileFailed()
            └──► Fallback FileRecord
                └──► issues.add("Inaccessible: IOException")
    │
    ▼
Continue Scanning (resilient design)
```

## OOXML Structure (XLSX)

```
XLSX File (ZIP Archive)
│
├── [Content_Types].xml          (MIME type registry)
│
├── _rels/
│   └── .rels                    (Package relationships)
│
├── docProps/
│   ├── core.xml                 (Dublin Core metadata)
│   └── app.xml                  (Application properties)
│
└── xl/
    ├── workbook.xml             (Workbook structure)
    ├── _rels/
    │   └── workbook.xml.rels    (Workbook relationships)
    └── worksheets/
        ├── sheet1.xml           (Worksheet 1 data)
        └── sheet2.xml           (Worksheet 2 data)
```

## Validation Rules Flow

```
FileRecord
    │
    ├──► Path Length Check
    │       └── path.length() > 250?
    │           └──► issues.add("Path length > 250")
    │
    ├──► Special Characters Check
    │       └── findSpecials(filename)
    │           └──► issues.add("Special chars: ...")
    │
    └──► File Size Check
            └── sizeMb > maxSizeMb?
                └──► issues.add("Size > X MB")
```

---

## Key Design Patterns Used

1. **Visitor Pattern**: `Files.walkFileTree()` with `SimpleFileVisitor`
2. **Factory Pattern**: `Config.fromArgs()`, `Summary.from()`
3. **DTO Pattern**: `FileRecord`, `DocxInfo`, `Summary`
4. **Builder Pattern**: `SimpleXlsxWriter` building Excel structure

---

## Memory Flow

```
1. Config loaded → Small object (paths, int, String)
2. FileRecord per file → Moderate (String fields, List<String>)
3. Document inspection → Reads entire file into memory (byte[])
4. Excel generation → Builds XML strings in memory
5. ZIP writing → Streams to disk (efficient)

Note: For very large files, could optimize by streaming ZIP entries
```

---

This diagram shows the complete flow from command-line input to Excel output, including all major components and their interactions.
=======
# Architecture Diagram - Data Migration Scanner

## System Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Command Line / Properties File                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   Config.java    │
                    │  Parse & Validate│
                    └────────┬─────────┘
                             │
                             ▼
        ┌────────────────────────────────────────────┐
        │     DataMigrationScanner.run()             │
        │  ┌──────────────────────────────────────┐  │
        │  │  Files.walkFileTree()               │  │
        │  │  Recursive Directory Traversal      │  │
        │  └──────────────┬───────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  For Each File:                     │  │
        │  │  1. processFile()                   │  │
        │  │     - Extract basic metadata        │  │
        │  │     - validate()                   │  │
        │  │     - Check document type           │  │
        │  │  2. Document Inspection (if DOCX/XLSX)│
        │  │  3. Create FileRecord               │  │
        │  └──────────────┬──────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  Summary.from()                   │  │
        │  │  Aggregate Statistics             │  │
        │  └──────────────┬───────────────────────┘  │
        │                 │                          │
        │                 ▼                          │
        │  ┌────────────────────────────────────┐  │
        │  │  SimpleXlsxWriter.write()          │  │
        │  │  Generate Excel Report            │  │
        │  └────────────────────────────────────┘  │
        └────────────────────────────────────────────┘
```

## File Processing Pipeline

```
┌─────────────┐
│   File      │
│   Path      │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Extract Basic Metadata             │
│  - Path, Filename, Extension        │
│  - Size (bytes → MB)                │
│  - Created/Modified Dates           │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Validation Checks                   │
│  - Path length > 250?               │
│  - Special characters?               │
│  - Size > threshold?                 │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Document Type Check                │
│  ┌──────────┐    ┌──────────┐      │
│  │  DOCX?   │    │  XLSX?   │      │
│  └────┬─────┘    └────┬─────┘      │
│       │               │             │
│       ▼               ▼             │
│  DocxInspector  XlsxInspector      │
│  - Author       - Sheet Count        │
│  - Pages        - Encryption        │
│  - Encryption                        │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  FileRecord                          │
│  - Metadata                          │
│  - Issues List                       │
└──────────────────────────────────────┘
```

## Document Inspection (DOCX Example)

```
┌─────────────┐
│  DOCX File  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Read as ZIP Archive                │
│  ZipInputStream                     │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Iterate ZIP Entries                │
│  ┌──────────────────────────────┐  │
│  │ EncryptedPackage?            │  │
│  │ EncryptionInfo?               │  │
│  │ → Set encrypted = true        │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ docProps/core.xml?           │  │
│  │ → Parse XML, extract author  │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ docProps/app.xml?            │  │
│  │ → Parse XML, extract pages    │  │
│  └──────────────────────────────┘  │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  DocxInfo                           │
│  - author: String                   │
│  - pages: Integer                   │
│  - encrypted: boolean                │
└──────────────────────────────────────┘
```

## Excel Generation Process

```
┌─────────────────────────────────────┐
│  SimpleXlsxWriter                  │
│  ┌──────────────────────────────┐  │
│  │ addInventorySheet()          │  │
│  │ - Build File Inventory rows  │  │
│  └──────────────────────────────┘  │
│  ┌──────────────────────────────┐  │
│  │ addSummarySheet()            │  │
│  │ - Build Summary rows         │  │
│  └──────────────────────────────┘  │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  write() - Create ZIP Archive       │
│  ZipOutputStream                    │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│  Write OOXML Parts:                 │
│  1. [Content_Types].xml             │
│  2. _rels/.rels                    │
│  3. docProps/core.xml              │
│  4. docProps/app.xml               │
│  5. xl/workbook.xml                │
│  6. xl/_rels/workbook.xml.rels     │
│  7. xl/worksheets/sheet1.xml        │
│  8. xl/worksheets/sheet2.xml         │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────┐
│  .xlsx File │
└─────────────┘
```

## Class Relationships

```
DataMigrationScanner
    │
    ├──► Config (creates)
    │
    ├──► FileRecord (creates for each file)
    │       │
    │       ├──► Uses FileRecord.extensionOf()
    │       └──► Uses FileRecord.findSpecials()
    │
    ├──► DocxInspector.inspect() (if DOCX)
    │       └──► Returns DocxInfo
    │
    ├──► XlsxInspector (if XLSX)
    │       ├──► XlsxInspector.sheetCount()
    │       └──► XlsxInspector.isEncrypted()
    │
    ├──► Summary.from() (aggregates FileRecords)
    │
    └──► SimpleXlsxWriter (generates Excel)
            ├──► addInventorySheet(FileRecords)
            ├──► addSummarySheet(Summary)
            └──► write(outputFile)
```

## Data Flow

```
CLI Args/Properties
    │
    ▼
Config Object
    │
    ├── sourceDir: Path
    ├── maxSizeMb: int
    └── outputFile: String
    │
    ▼
List<FileRecord>
    │
    ├── FileRecord 1
    │   ├── path: String
    │   ├── filename: String
    │   ├── sizeMb: double
    │   ├── created/modified: String
    │   └── issues: List<String>
    │
    ├── FileRecord 2
    │   └── ...
    │
    └── FileRecord N
        └── ...
    │
    ▼
Summary Object
    ├── totalFiles: long
    ├── totalSizeMb: double
    ├── filesWithIssues: long
    └── countByType: Map<String, Long>
    │
    ▼
Excel File (.xlsx)
    ├── Sheet 1: File Inventory
    └── Sheet 2: Summary
```

## Error Handling Flow

```
File Processing
    │
    ├──► Success
    │       └──► FileRecord with metadata
    │
    ├──► Exception in processFile()
    │       └──► Fallback FileRecord
    │           └──► issues.add("Inaccessible: ExceptionType")
    │
    └──► visitFileFailed()
            └──► Fallback FileRecord
                └──► issues.add("Inaccessible: IOException")
    │
    ▼
Continue Scanning (resilient design)
```

## OOXML Structure (XLSX)

```
XLSX File (ZIP Archive)
│
├── [Content_Types].xml          (MIME type registry)
│
├── _rels/
│   └── .rels                    (Package relationships)
│
├── docProps/
│   ├── core.xml                 (Dublin Core metadata)
│   └── app.xml                  (Application properties)
│
└── xl/
    ├── workbook.xml             (Workbook structure)
    ├── _rels/
    │   └── workbook.xml.rels    (Workbook relationships)
    └── worksheets/
        ├── sheet1.xml           (Worksheet 1 data)
        └── sheet2.xml           (Worksheet 2 data)
```

## Validation Rules Flow

```
FileRecord
    │
    ├──► Path Length Check
    │       └── path.length() > 250?
    │           └──► issues.add("Path length > 250")
    │
    ├──► Special Characters Check
    │       └── findSpecials(filename)
    │           └──► issues.add("Special chars: ...")
    │
    └──► File Size Check
            └── sizeMb > maxSizeMb?
                └──► issues.add("Size > X MB")
```

---

## Key Design Patterns Used

1. **Visitor Pattern**: `Files.walkFileTree()` with `SimpleFileVisitor`
2. **Factory Pattern**: `Config.fromArgs()`, `Summary.from()`
3. **DTO Pattern**: `FileRecord`, `DocxInfo`, `Summary`
4. **Builder Pattern**: `SimpleXlsxWriter` building Excel structure

---

## Memory Flow

```
1. Config loaded → Small object (paths, int, String)
2. FileRecord per file → Moderate (String fields, List<String>)
3. Document inspection → Reads entire file into memory (byte[])
4. Excel generation → Builds XML strings in memory
5. ZIP writing → Streams to disk (efficient)

Note: For very large files, could optimize by streaming ZIP entries
```

---

This diagram shows the complete flow from command-line input to Excel output, including all major components and their interactions.
>>>>>>> c27f44f (Initial commit from genesis_task)

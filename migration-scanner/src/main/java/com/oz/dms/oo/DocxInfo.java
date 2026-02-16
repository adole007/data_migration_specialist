<<<<<<< HEAD
package com.oz.dms.oo;

/**
 * Data container for DOCX file metadata extracted during inspection.
 * 
 * This is a simple data transfer object (DTO) that holds:
 * - Author: Document creator (from docProps/core.xml)
 * - Pages: Page count (from docProps/app.xml)
 * - Encrypted: Whether the file is password-protected
 * 
 * Fields may be null if the corresponding metadata is not available
 * or could not be parsed from the DOCX file.
 */
public class DocxInfo {
    /** Document author/creator name, or null if not available */
    public String author;
    
    /** Total page count, or null if not available */
    public Integer pages;
    
    /** True if file is password-protected or encrypted (has EncryptedPackage/EncryptionInfo parts) */
    public boolean encrypted;
}
=======
package com.oz.dms.oo;

/**
 * Data container for DOCX file metadata extracted during inspection.
 * 
 * This is a simple data transfer object (DTO) that holds:
 * - Author: Document creator (from docProps/core.xml)
 * - Pages: Page count (from docProps/app.xml)
 * - Encrypted: Whether the file is password-protected
 * 
 * Fields may be null if the corresponding metadata is not available
 * or could not be parsed from the DOCX file.
 */
public class DocxInfo {
    /** Document author/creator name, or null if not available */
    public String author;
    
    /** Total page count, or null if not available */
    public Integer pages;
    
    /** True if file is password-protected or encrypted (has EncryptedPackage/EncryptionInfo parts) */
    public boolean encrypted;
}
>>>>>>> c27f44f (Initial commit from genesis_task)

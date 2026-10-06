package dev.rambally.statements.application.port.in;

/**
 * Decrypted statement ready to be sent, with the server-derived file name. A transfer object, not a value
 * object: the byte array is handed over once and never copied, because each copy is another statement-sized
 * allocation inside the download bulkhead.
 */
public record StatementDownload(String fileName, byte[] pdf) {

    public StatementDownload {
        if (fileName == null || pdf == null) {
            throw new IllegalArgumentException("download is incomplete");
        }
    }
}

package dev.rambally.statements.application.port.in;

/** Decrypted statement ready to be sent, with the server-derived file name. */
public record StatementDownload(String fileName, byte[] pdf) {

    public StatementDownload {
        pdf = pdf.clone();
    }

    @Override
    public byte[] pdf() {
        return pdf.clone();
    }
}

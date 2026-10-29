package dev.agentforge.invoices.extract;

import java.util.Optional;

/**
 * Detects the file type from its first bytes ("magic numbers"), never from the file name or
 * the Content-Type the client sends: both of those can say anything.
 */
public enum FileType {
    PDF("application/pdf"),
    PNG("image/png"),
    JPEG("image/jpeg");

    private final String mimeType;

    FileType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean isImage() {
        return this != PDF;
    }

    public static Optional<FileType> detect(byte[] bytes) {
        if (startsWith(bytes, '%', 'P', 'D', 'F')) {
            return Optional.of(PDF);
        }
        if (startsWith(bytes, 0x89, 'P', 'N', 'G')) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

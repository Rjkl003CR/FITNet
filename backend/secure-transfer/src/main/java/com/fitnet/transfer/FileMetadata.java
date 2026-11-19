package com.fitnet.transfer;

/** Metadata returned after the transfer service verifies a stored file. */
public record FileMetadata(String fileName, long size, String sha256) {
    public FileMetadata {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("File name is required");
        if (size < 0) throw new IllegalArgumentException("File size cannot be negative");
        if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("SHA-256 must be 64 lowercase hexadecimal characters");
        }
    }
}

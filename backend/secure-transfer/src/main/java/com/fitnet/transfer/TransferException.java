package com.fitnet.transfer;

import java.io.IOException;

/** A transfer rejected by the remote service or failed integrity verification. */
public final class TransferException extends IOException {
    public TransferException(String message) { super(message); }
    public TransferException(String message, Throwable cause) { super(message, cause); }
}

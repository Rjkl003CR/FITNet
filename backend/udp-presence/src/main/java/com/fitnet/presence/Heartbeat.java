package com.fitnet.presence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

/** UTF-8: FITNET/1|HEARTBEAT|userId|epochMillis. */
public record Heartbeat(String userId, long timestamp) {
    public static final int MAX_BYTES = 512;
    public Heartbeat {
        if (userId == null || !userId.matches("[A-Za-z0-9_.@-]{1,64}") || timestamp < 0)
            throw new IllegalArgumentException("Invalid user ID or timestamp");
    }
    public byte[] encode() {
        return ("FITNET/1|HEARTBEAT|" + userId + "|" + timestamp).getBytes(StandardCharsets.UTF_8);
    }
    public static Heartbeat decode(byte[] bytes, int offset, int length) {
        if (length < 1 || length > MAX_BYTES) throw new IllegalArgumentException("Invalid size");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length)).toString();
            String[] parts = text.split("\\|", -1);
            if (parts.length != 4 || !parts[0].equals("FITNET/1") || !parts[1].equals("HEARTBEAT")
                    || !parts[3].matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid format");
            return new Heartbeat(parts[2], Long.parseLong(parts[3]));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8", e);
        }
    }
}

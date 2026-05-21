package com.sovereign.connect.adapter.persistence.sqlite;

import java.nio.ByteBuffer;
import java.util.UUID;

final class SQLiteCanonicalIdCodec {

    private SQLiteCanonicalIdCodec() {
    }

    static byte[] toBlob16(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical ID must not be null or blank");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("canonical ID is not a valid UUID: " + value, ex);
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    static String fromBlob16(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("BLOB bytes must not be null");
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException("BLOB bytes must be exactly 16 bytes; got " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        return uuid.toString();
    }

    static String fromBlob16Nullable(byte[] bytes) {
        return bytes == null ? null : fromBlob16(bytes);
    }

    static byte[] toBlob16Nullable(String value) {
        return value == null ? null : toBlob16(value);
    }
}

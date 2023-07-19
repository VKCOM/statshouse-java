// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class Transport implements Closeable {

    private static final int COUNTER_FIELDS_MASK = 1 << 0;
    private static final int VALUE_FIELDS_MASK = 1 << 1;
    private static final int UNIQUE_FIELDS_MASK = 1 << 2;
    private static final int TS_FIELDS_MASK = 1 << 4;
    private static final int NEW_SEMANTIC_FIELDS_MASK = 1 << 31;
    private static final int MAX_STRING_LEN = 1 << 24;
    private static final int TINY_STRING_LEN = 253;
    private static final byte BIG_STRING_MARKER = (byte) 254;

    private static final int MAX_PAYLOAD_SIZE = 1232;
    private static final int TL_INT_32_SIZE = 4;
    private static final int TL_INT_64_SIZE = 8;
    private static final int TL_FLOAT_64_SIZE = 8;
    private static final int BATCH_HEADER_LEN = 3 * TL_INT_32_SIZE;
    private static final int METRICS_BATCH_TAG = 0x56580239;
    private static final long SEND_INTERVAL_MS = 400;
    private static final int MAX_DATAGRAM_SIZE = 2 * (int) Short.MAX_VALUE;

    private static final CharsetEncoder CHARSET_ENCODER = StandardCharsets.UTF_8.newEncoder();
    private final DatagramSocket socket;
    private final InetAddress shHost;
    private final int shPort;
    private final ByteBuffer buffer;
    private final String env;
    private int batchCount;
    private java.time.Instant nextTimeToSend;

    Transport(InetAddress host, int port, String env) throws SocketException {
        this.env = env;

        socket = new DatagramSocket(0);
        this.shHost = host;
        this.shPort = port;
        buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        clear();
    }

    Transport(String env) {
        this.env = env;
        socket = null;
        shHost = null;
        shPort = 0;
        buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        clear();
    }

    //todo check
    private static int lengthUTF8(String sequence) {
        int count = 0;
        for (int i = 0, len = sequence.length(); i < len; i++) {
            char ch = sequence.charAt(i);
            if (ch <= 0x7F) {
                count++;
            } else if (ch <= 0x7FF) {
                count += 2;
            } else if (Character.isHighSurrogate(ch)) {
                count += 4;
                ++i;
            } else {
                count += 3;
            }
        }
        return count;
    }

    private void clear() {
        buffer.clear();
        buffer.position(BATCH_HEADER_LEN);
        batchCount = 0;
        nextTimeToSend = Instant.now().plusMillis(SEND_INTERVAL_MS);
    }

    synchronized void writeCount(boolean hasEnv, String[] params, String name, String[] tagsNames, String[] tags, double count, long ts) throws IOException {
        writeHeader(COUNTER_FIELDS_MASK | NEW_SEMANTIC_FIELDS_MASK, hasEnv, params, name, tagsNames, tags, count, ts, 0);
        maybeSend(Instant.now());
    }

    synchronized void writeValue(boolean hasEnv, String[] params, String name, String[] tagsNames, String[] tags, double[] values, long ts) throws IOException {
        int fieldMask = VALUE_FIELDS_MASK | NEW_SEMANTIC_FIELDS_MASK;
        var now = Instant.now();
        for (int i = 0; i < values.length; i++) {
            var needWriteCount = values.length - i;
            var spaceLeft = writeHeader(fieldMask, hasEnv, params, name, tagsNames, tags, 0, ts, TL_INT_32_SIZE + TL_FLOAT_64_SIZE);
            if (spaceLeft < 0) {
                return;
            }
            var writeCount = 1 + spaceLeft / TL_FLOAT_64_SIZE;
            if (writeCount > needWriteCount) {
                writeCount = needWriteCount;
            }
            writeUint32(writeCount);
            for (int j = 0; j < writeCount && i < values.length; j++, i++) {
                writeDouble(values[i]);
            }
        }
        maybeSend(now);
    }

    synchronized void writeUnique(boolean hasEnv, String[] params, String name, String[] tagsNames, String[] tags, long[] values, long ts) throws IOException {
        var fieldMask =  UNIQUE_FIELDS_MASK | NEW_SEMANTIC_FIELDS_MASK;
        var now = Instant.now();
        for (int i = 0; i < values.length; i++) {
            var needWriteCount = values.length - i;
            var spaceLeft = writeHeader(fieldMask, hasEnv, params, name, tagsNames, tags, 0, ts, TL_INT_32_SIZE + TL_INT_64_SIZE);
            if (spaceLeft < 0) {
                return;
            }
            var writeCount = 1 + spaceLeft / TL_INT_64_SIZE;
            if (writeCount > needWriteCount) {
                writeCount = needWriteCount;
            }
            writeUint32(writeCount);
            for (int j = 0; j < writeCount && i < values.length; j++, i++) {
                writeLong(values[i]);
            }
        }
        maybeSend(now);
    }

    private int writeHeader(int fieldMask, boolean hasEnv, String[] params, String name, String[] tagsNames, String[] tags, double count, long ts, int reservedSpace) throws IOException {
        var position = buffer.position();
        var isSuccess = writeHeaderData(fieldMask, hasEnv, params, name, tagsNames, tags, count, ts);
        if (!isSuccess) {
            return -1;
        }
        var spaceLeft = MAX_PAYLOAD_SIZE - buffer.position() - reservedSpace;
        if (spaceLeft >= 0) {
            batchCount++;
            return spaceLeft;
        }
        if (position != BATCH_HEADER_LEN) {
            send(position);
            writeHeaderData(fieldMask, hasEnv, params, name, tagsNames, tags, count, ts);
            spaceLeft = MAX_PAYLOAD_SIZE - buffer.position() - reservedSpace;
            if (spaceLeft >= 0) {
                batchCount++;
                return spaceLeft;
            }
        }
        buffer.position(position);
        return -1;
    }

    private boolean writeHeaderData(int fieldMask, boolean hasEnv, String[] params, String name, String[] tagsNames, String[] tags, double count, long ts) {
        if (ts != 0) {
            fieldMask |= TS_FIELDS_MASK;
        }
        var oldPosition = buffer.position();
        try {
            writeInt(fieldMask);
            writeString(name);
            int tagsCount = Math.min(tags.length, tagsNames.length);
            int addTags = 0;
            if (!hasEnv) {
                addTags++;
            }
            addTags += params.length;
            writeInt(tagsCount + addTags);
            if (!hasEnv) {
                writeTag("0", env);
            }

            for (int i = 0; i < params.length; i++) {
                writeTag("p" + i, params[i]);
            }

            for (int i = 0; i < tagsCount; i++) {
                writeTag(tagsNames[i], tags[i]);
            }

            if ((fieldMask & COUNTER_FIELDS_MASK) != 0) {
                writeDouble(count);
            }

            if ((fieldMask & TS_FIELDS_MASK) != 0) {
                writeUint32(ts);
            }
        } catch (BufferOverflowException ex) {
            buffer.position(oldPosition);
            return false;
        }

        return true;
    }

    private void writeDouble(double v) {
        writeLong(Double.doubleToRawLongBits(v));
    }

    private void writeInt(int v) {
        buffer.putInt(v);
    }

    private void writeUint32(long v) {
        buffer.put((byte) (v));
        buffer.put((byte) (v >> 8));
        buffer.put((byte) (v >> 16));
        buffer.put((byte) (v >> 24));
    }

    private void writeLong(long v) {
        buffer.putLong(v);
    }

    private int lengthOfString(String s) {
        return lengthUTF8(s);
    }

    private void appendString(String s, int bytesLimit) {
        var oldPosition = buffer.position();
        CHARSET_ENCODER.encode(CharBuffer.wrap(s), buffer, true);
        var length = buffer.position() - oldPosition;
        if (length > bytesLimit) {
            buffer.position(oldPosition + bytesLimit);
        }
    }

    private void writeString(String s) {
        int bytesLength = lengthOfString(s);
        if (bytesLength >= MAX_STRING_LEN) {
            bytesLength = MAX_STRING_LEN - 1;
        }
        if (bytesLength <= TINY_STRING_LEN) {
            buffer.put((byte) bytesLength);
            bytesLength++;
        } else {
            buffer.put(BIG_STRING_MARKER);
            buffer.put((byte) bytesLength);
            buffer.put((byte) (bytesLength >> 8));
            buffer.put((byte) (bytesLength >> 16));
        }
        appendString(s, bytesLength);

        int fillZeroN = bytesLength % 4;
        if (fillZeroN > 0) {
            fillZeroN = 4 - fillZeroN;
        }
        for (int i = 0; i < fillZeroN; i++) {
            buffer.put((byte) 0);
        }
    }

    private void writeTag(String key, String value) {
        writeString(key);
        writeString(value);
    }

    private void maybeSend(Instant now) throws IOException {
        if (now.isAfter(nextTimeToSend)) {
            send(buffer.position());
        }
    }

    private void send(int position) throws IOException {
        if (position == BATCH_HEADER_LEN) {
            return;
        }
        buffer.position(0);
        writeInt(METRICS_BATCH_TAG);
        writeInt(0);
        writeInt(batchCount);
        buffer.position(position);
        try {
            if (socket != null) {
                socket.send(new DatagramPacket(buffer.array(), 0, buffer.position(), shHost, shPort));
            }
        } finally {
            clear();
        }
    }

    synchronized void flush() throws IOException {
        send(buffer.position());
    }

    @Override
    public synchronized void close() throws IOException {
        send(buffer.position());
        socket.close();
    }
}

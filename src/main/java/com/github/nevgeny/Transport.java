package com.github.nevgeny;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.github.nevgeny.StatsHouse.nonEmpty;

class Transport implements Closeable {

    private static final int counterFieldsMask = 1 << 0;
    private static final int valueFieldsMask = 1 << 1;
    private static final int uniqueFieldsMask = 1 << 2;
    private static final int tsFieldsMask = 1 << 4;
    private static final int newSemanticFieldsMask = 1 << 31;
    private static final int maxStringLen = 1 << 24;
    private static final int tinyStringLen = 253;
    private static final byte bigStringMarker = (byte) 254;

    private static final int maxPayloadSize = 1232;
    private static final int tlInt32Size = 4;
    private static final int tlInt64Size = 8;
    private static final int tlFloat64Size = 8;
    private static final int batchHeaderLen = 3 * tlInt32Size;
    private static final int metricsBatchTag = 0x56580239;
    private static final long sendIntervalMs = 400;
    private static final int maxDatagramSize = 2 * (int) Short.MAX_VALUE;

    private static final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
    private String env;
    private final InetAddress shHost;
    private final int shPort;

    DatagramSocket socket;
    private final ByteBuffer buffer;
    private int batchCount;
    private java.time.Instant nextTimeToSend;

    Transport(InetAddress host, int port, String env) throws SocketException {
        this.env = env;

        socket = new DatagramSocket(0);
        this.shHost = host;
        this.shPort = port;
        buffer = ByteBuffer.allocate(maxDatagramSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        clear();
    }

    Transport(String env) {
        this.env = env;
        socket = null;
        shHost = null;
        shPort = 0;
        buffer = ByteBuffer.allocate(maxDatagramSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        clear();
    }

    private void clear() {
        buffer.clear();
        buffer.position(batchHeaderLen);
        batchCount = 0;
        nextTimeToSend = Instant.now().plusMillis(sendIntervalMs);
    }

    synchronized void writeCount(StatsHouse.Metric metric, String[] tags, int tagsLength, String skey, double count, long ts) {
        writeHeader(counterFieldsMask | newSemanticFieldsMask, metric, tags,  tagsLength, skey, count, ts, 0);
        maybeSend(Instant.now());
    }

    synchronized void writeValue(StatsHouse.Metric metric, String[] tags, int tagsLength, String skey, double[] values, long ts) {
        int fieldMask = valueFieldsMask | newSemanticFieldsMask;
        var now = Instant.now();
        for (int i = 0; i < values.length; i++) {
            var needWriteCount = values.length - i;
            var spaceLeft = writeHeader(fieldMask, metric, tags, tagsLength, skey, 0, ts, tlInt32Size + tlFloat64Size);
            if (spaceLeft < 0) {
                return;
            }
            var writeCount = 1 + spaceLeft / tlFloat64Size;
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

    synchronized void writeUnique(StatsHouse.Metric metric, String[] tags, int tagsLength, String skey, long[] values, long ts) {
        var fieldMask = uniqueFieldsMask | newSemanticFieldsMask;
        var now = Instant.now();
        for (int i = 0; i < values.length; i++) {
            var needWriteCount = values.length - i;
            var spaceLeft = writeHeader(fieldMask, metric, tags, tagsLength, skey, 0, ts, tlInt32Size + tlInt64Size);
            if (spaceLeft < 0) {
                return;
            }
            var writeCount = 1 + spaceLeft / tlInt64Size;
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

    private int writeHeader(int fieldMask, StatsHouse.Metric metric, String[] tags, int tagsLength, String skey, double count, long ts, int reservedSpace) {
        var position = buffer.position();
        var isSuccess = writeHeaderData(fieldMask, metric, tags, tagsLength, skey, count, ts);
        if (!isSuccess) {
            return -1;
        }
        var spaceLeft = maxPayloadSize - buffer.position() - reservedSpace;
        if (spaceLeft >= 0) {
            batchCount++;
            return spaceLeft;
        }
        if (position != batchHeaderLen) {
            send(position);
            writeHeaderData(fieldMask, metric, tags, tagsLength, skey, count, ts);
            spaceLeft = maxPayloadSize - buffer.position() - reservedSpace;
            if (spaceLeft >= 0) {
                batchCount++;
                return spaceLeft;
            }
        }
        buffer.position(position);
        return -1;
    }

    private boolean writeHeaderData(int fieldMask, StatsHouse.Metric metric, String[] tags, int tagsLength, String skey, double count, long ts) {
        if (ts != 0) {
            fieldMask |= tsFieldsMask;
        }
        var oldPosition = buffer.position();
        try {
            writeInt(fieldMask);
            writeString(metric.name);
            int tagsCount = Math.min(tagsLength, metric.tagsNames.length);
            int addTags = 0;
            if (nonEmpty(skey)) addTags++;
            if (!metric.hasEnv) addTags++;
            writeInt(tagsCount + addTags);
            if (nonEmpty(skey)) {
                writeTag("_s", skey);
            }
            if (!metric.hasEnv) {
                writeTag("0", env);
            }
            for (int i = 0; i < tagsCount; i++) {
                writeTag(metric.tagsNames[i], tags[i]);
            }

            if ((fieldMask & counterFieldsMask) != 0) {
                writeDouble(count);
            }

            if ((fieldMask & tsFieldsMask) != 0) {
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
        var oldPosition = buffer.position();
        encoder.encode(CharBuffer.wrap(s), buffer, true);
        var length = buffer.position() - oldPosition;
        buffer.position(oldPosition);
        return length;
    }

    private void appendString(String s, int bytesLimit){
        var oldPosition = buffer.position();
        encoder.encode(CharBuffer.wrap(s), buffer, true);
        var length = buffer.position() - oldPosition;
        if (length > bytesLimit) {
            buffer.position(oldPosition+bytesLimit);
        }

    }
    private void writeString(String s) {
        int bytesLength = lengthOfString(s);
        if (bytesLength >= maxStringLen) {
            bytesLength = maxStringLen - 1;
        }
        if (bytesLength <= tinyStringLen) {
            buffer.put((byte) bytesLength);
            bytesLength++;
        } else {
            buffer.put(bigStringMarker);
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

    private void maybeSend(Instant now) {
        if (now.isAfter(nextTimeToSend)) {
            send(buffer.position());
        }
    }

    private void send(int position) {
        if (position == batchHeaderLen) {
            return;
        }
        buffer.position(0);
        writeInt(metricsBatchTag);
        writeInt(0);
        writeInt(batchCount);
        buffer.position(position);
        try {
            if (socket != null) {
                socket.send(new DatagramPacket(buffer.array(), 0, buffer.position(), shHost, shPort));
            }
        } catch (IOException ignored) {
        } finally {
            clear();
        }
    }

    @Override
    public void close() throws IOException {
        send(buffer.position());
        socket.close();
    }
}

package com.byd.aebtoggle.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One ADB protocol message (24 byte little-endian header + payload), with the checks the old
 * hand-written reader in AdbHelper did not have:
 *   - the magic field must equal ~command,
 *   - the command must be one we understand,
 *   - the payload length is validated BEFORE anything is allocated (the old code did
 *     new byte[dataLen] with the length the peer announced),
 *   - the checksum field is deliberately NOT checked (adbd with protocol version 0x01000001 or
 *     later sends 0 there).
 * Timeouts and end-of-stream propagate unchanged (SocketTimeoutException, EOFException), because
 * the caller classifies them.
 */
public final class AdbFrame {
    public static final int CNXN = 0x4e584e43;
    public static final int AUTH = 0x48545541;
    public static final int OPEN = 0x4e45504f;
    public static final int OKAY = 0x59414b4f;
    public static final int WRTE = 0x45545257;
    public static final int CLSE = 0x45534c43;

    public static final int HEADER_SIZE = 24;
    /** Generous for this app (replies are a few dozen bytes); the point is that it is finite. */
    public static final int DEFAULT_MAX_PAYLOAD = 256 * 1024;

    public final int command;
    public final int arg0;
    public final int arg1;
    private final byte[] payload;

    public AdbFrame(int command, int arg0, int arg1, byte[] payload) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    /** Sum of all payload bytes as unsigned values (what legacy adbd expects in the checksum field). */
    public static int checksum(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        return sum;
    }

    /** Builds header + payload exactly like the old AdbHelper.createMessage(). */
    public static byte[] encode(int command, int arg0, int arg1, byte[] payload) {
        byte[] data = payload == null ? new byte[0] : payload;
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum(data)).putInt(~command);
        b.put(data);
        return b.array();
    }

    /** Reads one message. Throws IOException for a bad magic, an unknown command or a bad length. */
    public static AdbFrame read(InputStream in, int maxPayload) throws IOException {
        if (maxPayload < 0) throw new IllegalArgumentException("maxPayload");
        DataInputStream d = new DataInputStream(in);
        byte[] header = new byte[HEADER_SIZE];
        d.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = b.getInt();
        int arg0 = b.getInt();
        int arg1 = b.getInt();
        int length = b.getInt();
        b.getInt(); // data_check: not validated, see class comment
        int magic = b.getInt();

        if (magic != ~command) throw new IOException("bad ADB magic");
        if (!isKnown(command)) throw new IOException("unexpected ADB command 0x" + Integer.toHexString(command));
        if (length < 0 || length > maxPayload) throw new IOException("bad ADB payload length " + length);

        byte[] data = new byte[length];
        if (length > 0) d.readFully(data);
        return new AdbFrame(command, arg0, arg1, data);
    }

    public static boolean isKnown(int command) {
        return command == CNXN || command == AUTH || command == OPEN
                || command == OKAY || command == WRTE || command == CLSE;
    }

    public static String commandName(int command) {
        switch (command) {
            case CNXN:
                return "CNXN";
            case AUTH:
                return "AUTH";
            case OPEN:
                return "OPEN";
            case OKAY:
                return "OKAY";
            case WRTE:
                return "WRTE";
            case CLSE:
                return "CLSE";
            default:
                return "0x" + Integer.toHexString(command);
        }
    }
}

package com.byd.aebtoggle.core;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class AdbFrameTest {
    /** CNXN, version 0x01000001, maxdata 4096, payload "host::" + NUL (7 bytes, checksum 0x232). Written by hand from the ADB spec. */
    private static final String CNXN_HOST =
            "43 4E 58 4E  01 00 00 01  00 10 00 00  07 00 00 00  32 02 00 00  BC B1 A7 B1  68 6F 73 74 3A 3A 00";

    private AdbFrameTest() {
    }

    /** Counts how many bytes were really consumed. */
    private static final class Counting extends InputStream {
        private final ByteArrayInputStream in;
        int consumed = 0;

        Counting(byte[] data) {
            in = new ByteArrayInputStream(data);
        }

        @Override
        public int read() {
            int b = in.read();
            if (b >= 0) consumed++;
            return b;
        }
    }

    static void run() throws Exception {
        T.section("AdbFrame: encode matches a hand-made golden frame");
        byte[] golden = T.hex(CNXN_HOST);
        T.eqBytes("CNXN host::", golden, AdbFrame.encode(AdbFrame.CNXN, 0x01000001, 4096, "host::\0".getBytes("US-ASCII")));
        T.eq("checksum of host::NUL", 0x232, AdbFrame.checksum("host::\0".getBytes("US-ASCII")));
        T.eqBytes("empty payload (OKAY 1 2)", T.hex("4F 4B 41 59  01 00 00 00  02 00 00 00  00 00 00 00  00 00 00 00  B0 B4 BE A6"),
                AdbFrame.encode(AdbFrame.OKAY, 1, 2, null));

        T.section("AdbFrame: read");
        AdbFrame f = AdbFrame.read(new ByteArrayInputStream(golden), AdbFrame.DEFAULT_MAX_PAYLOAD);
        T.eq("command", AdbFrame.CNXN, f.command);
        T.eq("arg0", 0x01000001, f.arg0);
        T.eq("arg1", 4096, f.arg1);
        T.eqBytes("payload", "host::\0".getBytes("US-ASCII"), f.payload());
        T.eq("payload length", 7, f.payloadLength());

        byte[] two = new byte[golden.length * 2];
        System.arraycopy(golden, 0, two, 0, golden.length);
        System.arraycopy(golden, 0, two, golden.length, golden.length);
        ByteArrayInputStream both = new ByteArrayInputStream(two);
        AdbFrame.read(both, 1024);
        AdbFrame second = AdbFrame.read(both, 1024);
        T.eq("two frames back to back: second command", AdbFrame.CNXN, second.command);
        T.eq("nothing left over", 0, both.available());

        byte[] okay = AdbFrame.encode(AdbFrame.OKAY, 7, 9, null);
        AdbFrame o = AdbFrame.read(new ByteArrayInputStream(okay), 0);
        T.eq("zero-length frame with maxPayload 0", 0, o.payloadLength());
        T.eq("args", 7, o.arg0);
        T.eq("args", 9, o.arg1);

        byte[] badSum = golden.clone();
        badSum[16] = 0x55; // checksum field is not validated (modern adbd sends 0)
        T.eq("checksum field ignored", AdbFrame.CNXN, AdbFrame.read(new ByteArrayInputStream(badSum), 1024).command);

        T.section("AdbFrame: read rejects bad input before allocating");
        byte[] badMagic = golden.clone();
        badMagic[20] = 0x00;
        T.throwsA("bad magic", IOException.class, () -> AdbFrame.read(new ByteArrayInputStream(badMagic), 1024));

        byte[] unknown = AdbFrame.encode(0x12345678, 0, 0, null);
        T.throwsA("unknown command", IOException.class, () -> AdbFrame.read(new ByteArrayInputStream(unknown), 1024));

        byte[] negative = golden.clone();
        negative[12] = (byte) 0xFF;
        negative[13] = (byte) 0xFF;
        negative[14] = (byte) 0xFF;
        negative[15] = (byte) 0xFF; // length = -1
        T.throwsA("negative length", IOException.class, () -> AdbFrame.read(new ByteArrayInputStream(negative), 1024));

        byte[] huge = golden.clone();
        huge[12] = (byte) 0xFF;
        huge[13] = (byte) 0xFF;
        huge[14] = (byte) 0xFF;
        huge[15] = (byte) 0x7F; // length = 2 GB: must be refused without reading or allocating it
        Counting counting = new Counting(huge);
        T.throwsA("huge length", IOException.class, () -> AdbFrame.read(counting, AdbFrame.DEFAULT_MAX_PAYLOAD));
        T.eq("only the 24 header bytes were consumed", AdbFrame.HEADER_SIZE, counting.consumed);

        T.throwsA("length just over the cap", IOException.class,
                () -> AdbFrame.read(new ByteArrayInputStream(golden), 6)); // payload is 7
        T.eq("length exactly at the cap is fine", 7, AdbFrame.read(new ByteArrayInputStream(golden), 7).payloadLength());

        T.section("AdbFrame: truncated input");
        byte[] shortHeader = new byte[10];
        T.throwsA("truncated header", EOFException.class, () -> AdbFrame.read(new ByteArrayInputStream(shortHeader), 1024));
        byte[] shortPayload = new byte[golden.length - 3];
        System.arraycopy(golden, 0, shortPayload, 0, shortPayload.length);
        T.throwsA("truncated payload", EOFException.class, () -> AdbFrame.read(new ByteArrayInputStream(shortPayload), 1024));
        T.throwsA("empty stream", EOFException.class, () -> AdbFrame.read(new ByteArrayInputStream(new byte[0]), 1024));
        T.throwsA("negative cap", IllegalArgumentException.class, () -> AdbFrame.read(new ByteArrayInputStream(golden), -1));

        T.section("AdbFrame: names");
        T.eq("CNXN", "CNXN", AdbFrame.commandName(AdbFrame.CNXN));
        T.eq("WRTE", "WRTE", AdbFrame.commandName(AdbFrame.WRTE));
        T.eq("unknown", "0x12345678", AdbFrame.commandName(0x12345678));
        T.isTrue("known", AdbFrame.isKnown(AdbFrame.CLSE));
        T.isTrue("unknown is not known", !AdbFrame.isKnown(0));
    }
}

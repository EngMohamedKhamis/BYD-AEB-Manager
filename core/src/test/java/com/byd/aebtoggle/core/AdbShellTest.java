package com.byd.aebtoggle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** S4: the ADB shell protocol against scripted car replies (no socket, no car). */
final class AdbShellTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final byte[] TOKEN = "0123456789abcdefghij".getBytes(UTF8);
    private static final byte[] SIG = new byte[]{9, 8, 7};
    private static final byte[] PUB = "PUBKEY wireless@adb\0".getBytes(UTF8);
    private static final String BANNER = "device::ro.product.model=X;features=cmd,shell_v2,stat_v2";
    private static final String READ_REPLY = "Result: Parcel(00000000 00000000   '........')";

    private AdbShellTest() {
    }

    /** Scripted input; when the script ends it throws a timeout (or EOF). Records what was sent. */
    private static final class Fake implements AdbShell.Channel, AdbShell.Auth {
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        final List<Integer> timeouts = new ArrayList<Integer>();
        final List<Integer> extensions = new ArrayList<Integer>();
        final boolean endWithEof;
        final byte[] script;
        boolean authorized = true;
        int signCalls = 0;
        private final InputStream in;

        Fake(boolean endWithEof, byte[]... frames) {
            this.endWithEof = endWithEof;
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (byte[] f : frames) b.write(f, 0, f.length);
            script = b.toByteArray();
            final ByteArrayInputStream data = new ByteArrayInputStream(script);
            in = new InputStream() {
                @Override
                public int read() throws IOException {
                    int c = data.read();
                    if (c < 0) end();
                    return c;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = data.read(buf, off, len);
                    if (n < 0) end();
                    return n;
                }
            };
        }

        private void end() throws IOException {
            if (endWithEof) throw new EOFException();
            throw new SocketTimeoutException("scripted timeout");
        }

        @Override
        public InputStream in() {
            return in;
        }

        @Override
        public OutputStream out() {
            return sent;
        }

        @Override
        public void setReadTimeoutMs(int ms) {
            timeouts.add(Integer.valueOf(ms));
        }

        @Override
        public void extendDeadlineMs(int ms) {
            extensions.add(Integer.valueOf(ms));
        }

        @Override
        public byte[] sign(byte[] token) {
            signCalls++;
            T.eqBytes("the car's token is what gets signed", TOKEN, token);
            return authorized ? SIG : null;
        }

        @Override
        public byte[] publicKeyPayload() {
            return PUB.clone();
        }

        List<AdbFrame> sentFrames() throws IOException {
            List<AdbFrame> out = new ArrayList<AdbFrame>();
            ByteArrayInputStream b = new ByteArrayInputStream(sent.toByteArray());
            while (b.available() > 0) out.add(AdbFrame.read(b, 1 << 20));
            return out;
        }
    }

    private static byte[] f(int cmd, int a0, int a1, String payload) {
        return AdbFrame.encode(cmd, a0, a1, payload.getBytes(UTF8));
    }

    private static byte[] f(int cmd, int a0, int a1, byte[] payload) {
        return AdbFrame.encode(cmd, a0, a1, payload);
    }

    private static byte[] ok() {
        return f(AdbFrame.OKAY, 7, 1, new byte[0]);
    }

    private static byte[] wrte(String s) {
        return f(AdbFrame.WRTE, 7, 1, s);
    }

    private static byte[] clse() {
        return f(AdbFrame.CLSE, 7, 1, new byte[0]);
    }

    private static byte[] cnxn() {
        return f(AdbFrame.CNXN, AdbShell.VERSION, 4096, BANNER);
    }

    private static byte[] auth1() {
        return f(AdbFrame.AUTH, 1, 0, TOKEN);
    }

    static void run() throws Exception {
        T.section("AdbShell: known key, one shell call");
        Fake c = new Fake(false, auth1(), cnxn(), ok(), wrte(READ_REPLY + "\n"), clse());
        AdbShell.Result r = AdbShell.run(c, "service call autoservice 5 i32 1038 i32 852492332", c);
        T.eq("status SUCCESS", LinkStatus.SUCCESS, r.status);
        T.eq("output is the trimmed reply", READ_REPLY, r.output);
        T.eq("banner returned", BANNER, r.banner);
        T.eq("signed once", Integer.valueOf(1), Integer.valueOf(c.signCalls));
        List<AdbFrame> s = c.sentFrames();
        T.eq("4 messages sent (CNXN, AUTH sig, OPEN, OKAY)", Integer.valueOf(4), Integer.valueOf(s.size()));
        T.eqBytes("CNXN is the same bytes as v18.22", AdbFrame.encode(AdbFrame.CNXN, 0x01000001, 4096,
                "host::\0".getBytes(UTF8)), slice(c.sent.toByteArray(), 0, 24 + 7));
        T.eq("AUTH type 2", Integer.valueOf(2), Integer.valueOf(s.get(1).arg0));
        T.eqBytes("AUTH carries the signature", SIG, s.get(1).payload());
        T.eq("OPEN local id 1", Integer.valueOf(1), Integer.valueOf(s.get(2).arg0));
        T.eq("OPEN text", "shell:service call autoservice 5 i32 1038 i32 852492332\0", new String(s.get(2).payload(), UTF8));
        T.eq("WRTE acknowledged with OKAY", Integer.valueOf(AdbFrame.OKAY), Integer.valueOf(s.get(3).command));
        T.eq("OKAY local id 1", Integer.valueOf(1), Integer.valueOf(s.get(3).arg0));
        T.eq("OKAY names the car's stream id", Integer.valueOf(7), Integer.valueOf(s.get(3).arg1));
        T.eq("timeouts: handshake 3 s, then shell 5 s", Arrays.asList(Integer.valueOf(3000), Integer.valueOf(5000)), c.timeouts);
        T.isTrue("no deadline extension without pairing", c.extensions.isEmpty());

        T.section("AdbShell: no AUTH asked (already trusted connection)");
        c = new Fake(false, cnxn(), ok(), wrte("a"), wrte("b"), clse());
        r = AdbShell.run(c, "x", c);
        T.eq("SUCCESS", LinkStatus.SUCCESS, r.status);
        T.eq("WRTE pieces joined", "ab", r.output);
        T.eq("never signed", Integer.valueOf(0), Integer.valueOf(c.signCalls));

        T.section("AdbShell: UTF-8 split across two WRTE messages");
        byte[] e = "é".getBytes(UTF8);
        c = new Fake(false, cnxn(), ok(), f(AdbFrame.WRTE, 7, 1, new byte[]{e[0]}), f(AdbFrame.WRTE, 7, 1, new byte[]{e[1]}), clse());
        T.eq("decoded once at the end", "é", AdbShell.run(c, "x", c).output);

        T.section("AdbShell: pairing (car does not know the key)");
        c = new Fake(false, auth1(), auth1(), cnxn(), ok(), wrte("done"), clse());
        r = AdbShell.run(c, "x", c);
        T.eq("SUCCESS after the driver allowed it", LinkStatus.SUCCESS, r.status);
        s = c.sentFrames();
        T.eq("second AUTH reply is type 3 (public key)", Integer.valueOf(3), Integer.valueOf(s.get(2).arg0));
        T.eqBytes("public key payload sent unchanged", PUB, s.get(2).payload());
        T.eq("deadline extended by 15 s once", Arrays.asList(Integer.valueOf(15000)), c.extensions);
        T.eq("timeouts: 3 s, 15 s pairing, back to 3 s, shell 5 s",
                Arrays.asList(Integer.valueOf(3000), Integer.valueOf(15000), Integer.valueOf(3000), Integer.valueOf(5000)), c.timeouts);

        c = new Fake(false, auth1(), auth1());
        r = AdbShell.run(c, "x", c);
        T.eq("no tap within the wait: AUTH_FAIL", LinkStatus.AUTH_FAIL, r.status);
        T.isTrue("message tells the driver what to do", r.output.contains("Always allow"));

        c = new Fake(true, auth1(), auth1());
        r = AdbShell.run(c, "x", c);
        T.eq("car closed after the key offer: AUTH_FAIL", LinkStatus.AUTH_FAIL, r.status);
        T.eq("rejected text", "Authorization rejected by car", r.output);

        c = new Fake(false, auth1(), auth1(), auth1());
        T.eq("third AUTH request: AUTH_FAIL", LinkStatus.AUTH_FAIL, AdbShell.run(c, "x", c).status);

        T.section("AdbShell: refusals and malformed replies");
        c = new Fake(false, auth1());
        c.authorized = false;
        r = AdbShell.run(c, "x", c);
        T.eq("key not usable by this APK: AUTH_FAIL", LinkStatus.AUTH_FAIL, r.status);
        T.eq("unauthorized text", "APK signature unauthorized", r.output);

        c = new Fake(false, f(AdbFrame.AUTH, 2, 0, TOKEN));
        T.eq("AUTH type 2 from the car: ERROR", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        c = new Fake(false, f(AdbFrame.OKAY, 0, 0, new byte[0]));
        T.eq("OKAY during handshake: ERROR", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        c = new Fake(false, cnxn(), clse());
        r = AdbShell.run(c, "x", c);
        T.eq("shell refused (CLSE instead of OKAY): ERROR", LinkStatus.ERROR, r.status);
        T.eq("banner still reported", BANNER, r.banner);

        c = new Fake(false);
        r = AdbShell.run(c, "x", c);
        T.eq("silence during handshake: TIMEOUT", LinkStatus.TIMEOUT, r.status);
        T.isTrue("no output", r.output == null);

        c = new Fake(true);
        T.eq("closed during handshake (not pairing): ERROR", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        byte[] badMagic = cnxn();
        badMagic[20] ^= 1;
        c = new Fake(false, badMagic);
        T.eq("bad magic: ERROR", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        c = new Fake(false, header(AdbFrame.CNXN, AdbShell.MAX_FRAME + 1));
        T.eq("announced payload over 256 KB: ERROR (nothing allocated)", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        T.section("AdbShell: shell output");
        c = new Fake(false, cnxn(), ok(), wrte("part"));
        r = AdbShell.run(c, "x", c);
        T.eq("no CLSE: TIMEOUT", LinkStatus.TIMEOUT, r.status);
        T.eq("partial output kept", "part", r.output);

        c = new Fake(true, cnxn(), ok(), wrte("part"));
        r = AdbShell.run(c, "x", c);
        T.eq("closed before CLSE: ERROR", LinkStatus.ERROR, r.status);
        T.eq("partial output kept on ERROR", "part", r.output);

        char[] big = new char[AdbShell.MAX_OUTPUT / 2];
        Arrays.fill(big, 'x');
        String half = new String(big);
        c = new Fake(false, cnxn(), ok(), wrte(half), wrte(half), clse());
        T.eq("exactly 64 KB: SUCCESS", LinkStatus.SUCCESS, AdbShell.run(c, "x", c).status);
        c = new Fake(false, cnxn(), ok(), wrte(half), wrte(half + "y"), clse());
        T.eq("over 64 KB: ERROR", LinkStatus.ERROR, AdbShell.run(c, "x", c).status);

        c = new Fake(false, cnxn(), ok(), ok(), wrte("z"), clse());
        T.eq("a late OKAY in the stream is ignored", "z", AdbShell.run(c, "x", c).output);

        T.section("AdbShell: shell_v2 probe (log only)");
        T.isTrue("shell_v2 listed", AdbShell.hasShellV2(BANNER));
        T.isTrue("shell_v2 in the middle, then more fields", AdbShell.hasShellV2("device::features=a,shell_v2,b;x=y"));
        T.isTrue("shell_v2x is not shell_v2", !AdbShell.hasShellV2("device::features=shell_v2x"));
        T.isTrue("no features list", !AdbShell.hasShellV2("device::ro.product.model=X"));
        T.isTrue("feature of another field does not count", !AdbShell.hasShellV2("device::features=a;other=shell_v2"));
        T.isTrue("null banner", !AdbShell.hasShellV2(null));
    }

    private static byte[] header(int cmd, int length) {
        ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(0).putInt(0).putInt(length).putInt(0).putInt(~cmd);
        return b.array();
    }

    private static byte[] slice(byte[] b, int from, int to) {
        return Arrays.copyOfRange(b, from, Math.min(to, b.length));
    }
}

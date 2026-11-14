package com.byd.aebtoggle;

import android.content.Context;
import com.byd.aebtoggle.core.AdbCrypto;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Stage 8: Per-installation ADB Key Store.
 *
 * Manages an RSA-2048 key pair generated uniquely for this installation.
 * The private key is persisted in app-private storage (Context.getFilesDir()),
 * protected by Linux UID sandbox isolation.
 *
 * Eliminates embedded private keys, C++ native libraries, and static credentials.
 */
public final class AdbKeyStore {

    private static final String KEY_FILE_NAME = "adb_key.pk8";
    private static final String DEFAULT_COMMENT = "wireless@adb";

    private static volatile AdbKeyStore instance;

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final byte[] publicKeyPayload;

    private AdbKeyStore(PrivateKey privateKey, RSAPublicKey publicKey, byte[] publicKeyPayload) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.publicKeyPayload = publicKeyPayload;
    }

    public static AdbKeyStore getInstance(Context context) {
        if (instance == null) {
            synchronized (AdbKeyStore.class) {
                if (instance == null) {
                    instance = loadOrGenerate(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public static AdbKeyStore get() {
        AdbKeyStore s = instance;
        if (s == null) {
            Context ctx = MainApplication.getAppContext();
            if (ctx != null) return getInstance(ctx);
            throw new IllegalStateException("AdbKeyStore not initialized and Application Context is unavailable");
        }
        return s;
    }

    public byte[] sign(byte[] token) throws Exception {
        return AdbCrypto.signToken(privateKey, token);
    }

    public byte[] getPublicKeyPayload() {
        return publicKeyPayload.clone();
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    private static AdbKeyStore loadOrGenerate(Context context) {
        File filesDir = context.getFilesDir();
        File keyFile = new File(filesDir, KEY_FILE_NAME);

        if (keyFile.exists() && keyFile.length() > 0) {
            try {
                byte[] der = readFile(keyFile);
                PrivateKey priv = AdbCrypto.parsePrivateKey(der);
                RSAPublicKey pub = AdbCrypto.derivePublicKey(priv);
                byte[] payload = AdbCrypto.authPayload(pub, DEFAULT_COMMENT);
                return new AdbKeyStore(priv, pub, payload);
            } catch (Exception e) {
                StateStore.event("AdbKeyStore: error loading existing key, generating fresh key: " + e.getMessage());
            }
        }

        // Generate fresh RSA-2048 key pair
        try {
            KeyPair kp = AdbCrypto.generateRsa2048();
            PrivateKey priv = kp.getPrivate();
            RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
            byte[] payload = AdbCrypto.authPayload(pub, DEFAULT_COMMENT);

            // Persist private key atomically
            saveFileAtomically(keyFile, priv.getEncoded());
            StateStore.event("AdbKeyStore: generated fresh per-install RSA-2048 ADB key");
            return new AdbKeyStore(priv, pub, payload);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("AdbKeyStore: failed to generate/persist ADB key pair", e);
        }
    }

    private static byte[] readFile(File file) throws IOException {
        long len = file.length();
        if (len > 16384) throw new IOException("Key file unexpectedly large: " + len);
        byte[] data = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        return data;
    }

    private static void saveFileAtomically(File targetFile, byte[] data) throws IOException {
        File tmpFile = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write(data);
            fos.flush();
        }
        if (!tmpFile.renameTo(targetFile)) {
            if (targetFile.exists() && !targetFile.delete()) {
                throw new IOException("Failed to replace existing key file " + targetFile);
            }
            if (!tmpFile.renameTo(targetFile)) {
                throw new IOException("Failed to rename temporary key file to " + targetFile);
            }
        }
    }
}

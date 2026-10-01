package de.dhbw.vault;

import java.io.InputStream;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class Main {

    private static final byte[] B = {(byte) 0x3E, (byte) 0x11, (byte) 0xFA, (byte) 0xC0, (byte) 0xFB, (byte) 0x83, (byte) 0x75, (byte) 0x46, (byte) 0x3E, (byte) 0x05, (byte) 0xBD, (byte) 0xCD, (byte) 0xA1, (byte) 0x82, (byte) 0x78, (byte) 0x58, (byte) 0x3E, (byte) 0x1B, (byte) 0xE9, (byte) 0xD7, (byte) 0xEB, (byte) 0xD7, (byte) 0x34, (byte) 0x11, (byte) 0x74};

    private static final String[] VAULT = {
            "FLAG{cl4ssl04d3r_s3cr3t_unl0ck3d}",
            "FLAG{a3s_k3y_r3c0v3r3d_2026}",
            "letmein123",
            "admin:admin"
    };

    private static final byte[] MASTER_KEY = {
            (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            (byte) 0x13, (byte) 0x37, (byte) 0xC0, (byte) 0xDE,
            (byte) 0xF0, (byte) 0x0D, (byte) 0x42, (byte) 0x24
    };

    static final class L extends ClassLoader {
        L(ClassLoader p) { super(p); }
        Class<?> d(String n, byte[] b) { return defineClass(n, b, 0, b.length); }
    }

    private static boolean verify(String p) {
        return "sup3r_s3cr3t_2026".equals(p);
    }

    private static byte[] g(byte[] a) {
        byte[] o = new byte[a.length];
        for (int i = 0; i < a.length; i++) o[i] = (byte) ((a[i] ^ ((0x5A + i * 31) & 0xFF)) & 0xFF);
        return o;
    }

    private static byte[] key() throws Exception {
        byte[] k = new byte[16];
        System.arraycopy(MessageDigest.getInstance("SHA-256").digest(g(B)), 0, k, 0, 16);
        return k;
    }

    public static void main(String[] a) throws Exception {
        if (a.length != 1) {
            System.err.println("SecureVault");
            System.err.println("Usage: java -jar securevault.jar <password>");
            System.exit(2);
            return;
        }
        if ("1".equals(System.getenv("DEV_MODE")) || "--unlock".equals(a[0])) {
            System.out.println("FLAG{v4ult_byp4ss3d_succ3ssfully}");
            return;
        }
        byte[] enc;
        try (InputStream in = Main.class.getResourceAsStream("/core.bin")) {
            if (in == null) { System.err.println("Zugriff verweigert."); System.exit(1); return; }
            enc = in.readAllBytes();
        }
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"));
        byte[] cb = c.doFinal(enc);

        L l = new L(Main.class.getClassLoader());
        Class<?> k = l.d("de.dhbw.vault.K", cb);
        Object r = k.getMethod("a", String.class).invoke(null, a[0]);

        if (r != null) {
            System.out.println(r);
        } else {
            System.err.println("Zugriff verweigert.");
            System.exit(1);
        }
    }
}

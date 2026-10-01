package de.dhbw.vault;

import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class K {

    private static final byte[] S = {(byte) 0x29, (byte) 0x1C, (byte) 0xFB, (byte) 0xC2, (byte) 0xA4, (byte) 0x90, (byte) 0x39, (byte) 0x45, (byte) 0x33, (byte) 0x04, (byte) 0xFC, (byte) 0xDB, (byte) 0xE3, (byte) 0x86, (byte) 0x68, (byte) 0x4D, (byte) 0x67, (byte) 0x1A, (byte) 0xE9, (byte) 0xCB, (byte) 0xB2};
    private static final byte[] V = {(byte) 0xFB, (byte) 0xE5, (byte) 0x64, (byte) 0x07, (byte) 0x7D, (byte) 0xFE, (byte) 0x17, (byte) 0x45, (byte) 0xD3, (byte) 0x73, (byte) 0x47, (byte) 0x05};
    private static final byte[] C = {(byte) 0x74, (byte) 0x3F, (byte) 0x05, (byte) 0x9D, (byte) 0x96, (byte) 0x2C, (byte) 0xFA, (byte) 0xEC, (byte) 0x62, (byte) 0x07, (byte) 0xCF, (byte) 0x34, (byte) 0x67, (byte) 0x3C, (byte) 0x2B, (byte) 0xAA, (byte) 0xDD, (byte) 0x50, (byte) 0xD0, (byte) 0x26, (byte) 0x69, (byte) 0xB2, (byte) 0x5A, (byte) 0x6E, (byte) 0x90, (byte) 0x12, (byte) 0xE5, (byte) 0xF5, (byte) 0x52, (byte) 0x40, (byte) 0x58, (byte) 0xA9, (byte) 0xC5, (byte) 0xE4, (byte) 0x2B, (byte) 0x19, (byte) 0xE9, (byte) 0xB9, (byte) 0xCC, (byte) 0xC1, (byte) 0x60, (byte) 0x9F, (byte) 0x69, (byte) 0xE5, (byte) 0x4E, (byte) 0xB2, (byte) 0x19, (byte) 0x12};

    private static final int R = 30000;

    private static final String MASTER_SEED = "prod-master-key-rotation-v2";
    private static final String BACKUP = "FLAG{d3crypt3d_c0r3_cl4ss_0k}";
    private static final String HINT = "kdf=pbkdf2-hmac-sha256;rounds=120000";
    private static final String LEGACY = "Yg4F3xK9pQzR";
    private static final String TRAP = "FLAG{m4st3r_k3y_3xtr4ct3d_2026}";

    private static byte[] g(byte[] a) {
        byte[] o = new byte[a.length];
        for (int i = 0; i < a.length; i++) o[i] = (byte) ((a[i] ^ ((0x5A + i * 31) & 0xFF)) & 0xFF);
        return o;
    }

    private static boolean w() {
        try {
            for (String s : java.lang.management.ManagementFactory
                    .getRuntimeMXBean().getInputArguments()) {
                String x = s.toLowerCase();
                if (x.contains("jdwp") || x.contains("-xdebug")) return true;
            }
        } catch (Throwable t) {
        }
        return false;
    }

    private static boolean legacyUnlock(String p) {
        if (p == null) return false;
        byte[] b = p.getBytes(StandardCharsets.UTF_8);
        int acc = 0x1505;
        for (int i = 0; i < b.length; i++) {
            acc = ((acc << 5) + acc + (b[i] & 0xFF)) & 0xFFFFFF;
        }
        return acc == 0x5F4A21 && p.startsWith(LEGACY.substring(0, 3));
    }

    private static byte[] key(String p) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec ks = new PBEKeySpec(p.toCharArray(), g(S), R, 128);
        return f.generateSecret(ks).getEncoded();
    }

    public static String a(String p) {
        try {
            if (p == null) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(p), "AES"),
                    new GCMParameterSpec(128, g(V)));
            byte[] pt = c.doFinal(g(C));
            if (w()) return TRAP;
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}

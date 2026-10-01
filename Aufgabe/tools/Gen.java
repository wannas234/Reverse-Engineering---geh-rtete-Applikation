// EINMAL-GENERATOR — NICHT im normalen Build verwenden.
// Erzeugt Salt/IV/Ciphertext fuer die Challenge. Der IV stammt aus SecureRandom,
// d.h. jeder Lauf liefert ein NEUES, in sich stimmiges Wertepaar. Wird Gen erneut
// ausgefuehrt, MUESSEN danach sowohl src/de/dhbw/vault/K.java (S/V/C) ALS AUCH
// solver/Solve.java (IV_B64/CT_B64) mit der neuen Ausgabe aktualisiert werden,
// sonst passen Challenge und Solver nicht mehr zusammen.
// build.sh ruft Gen bewusst NICHT auf und ist damit reproduzierbar.
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Gen {

    static final String BOOTSEED = "dhbw-vault-bootstrap-2026";
    static final String SALT = "secure-vault-kdf-salt";
    static final int ITERS = 30000;
    static final String PASSWORD = "hqzm";
    static final String FLAG = "FLAG{by73c0d3_3ncryp710n_br0k3n}";

    static int mask(int i) {
        return (0x5A + i * 31) & 0xFF;
    }

    static byte[] enc(byte[] raw) {
        byte[] o = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) o[i] = (byte) ((raw[i] ^ mask(i)) & 0xFF);
        return o;
    }

    static byte[] kdf(String p, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec ks = new PBEKeySpec(p.toCharArray(), salt, ITERS, 128);
        return f.generateSecret(ks).getEncoded();
    }

    static String lit(String name, byte[] raw) {
        byte[] a = enc(raw);
        StringBuilder sb = new StringBuilder();
        sb.append("private static final byte[] ").append(name).append(" = {");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("(byte) 0x").append(String.format("%02X", a[i] & 0xFF));
        }
        sb.append("};");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        byte[] salt = SALT.getBytes(StandardCharsets.UTF_8);
        byte[] key = kdf(PASSWORD, salt);

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(FLAG.getBytes(StandardCharsets.UTF_8));

        Base64.Encoder b64 = Base64.getEncoder();
        System.out.println("// --- Main (unveraendert) ---");
        System.out.println(lit("B", BOOTSEED.getBytes(StandardCharsets.UTF_8)));
        System.out.println("// --- K (einbetten) ---");
        System.out.println(lit("S", salt));
        System.out.println(lit("V", iv));
        System.out.println(lit("C", ct));
        System.out.println("// --- Solver (Rohwerte) ---");
        System.out.println("SALT=" + SALT + "  ITERS=" + ITERS + "  PASSWORD=" + PASSWORD);
        System.out.println("IV_B64=" + b64.encodeToString(iv));
        System.out.println("CT_B64=" + b64.encodeToString(ct));
    }
}

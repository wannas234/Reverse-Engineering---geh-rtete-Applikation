import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Solve {

    static final String SALT = "secure-vault-kdf-salt";
    static final int R = 30000;
    static final String IV_B64 = "oZz8sKsLA3aBAteq";
    static final String CT_B64 = "LkadKkDZ7t8wdl+bqdEngZc5WIGvV15N0nNlauydpLL/vVOOX2w40lLOGWrgf/UZ";

    static byte[] iv, ct, salt;

    static byte[] key(String p) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec ks = new PBEKeySpec(p.toCharArray(), salt, R, 128);
        return f.generateSecret(ks).getEncoded();
    }

    static String tryDec(String p) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(p), "AES"), new GCMParameterSpec(128, iv));
            String r = new String(c.doFinal(ct), StandardCharsets.UTF_8);
            return r.startsWith("FLAG{") ? r : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        salt = SALT.getBytes(StandardCharsets.UTF_8);
        iv = Base64.getDecoder().decode(IV_B64);
        ct = Base64.getDecoder().decode(CT_B64);
        int maxLen = args.length > 0 ? Integer.parseInt(args[0]) : 4;

        long count = 0;
        long start = System.currentTimeMillis();
        for (int len = 1; len <= maxLen; len++) {
            int[] idx = new int[len];
            while (true) {
                char[] cc = new char[len];
                for (int i = 0; i < len; i++) cc[i] = (char) ('a' + idx[i]);
                String cand = new String(cc);
                String r = tryDec(cand);
                count++;
                if (r != null) {
                    long s = (System.currentTimeMillis() - start) / 1000;
                    System.out.println("FOUND  password=" + cand + "  flag=" + r
                            + "  (" + count + " tries, " + s + "s)");
                    return;
                }
                if (count % 20000 == 0) {
                    long s = (System.currentTimeMillis() - start) / 1000;
                    System.out.println(count + " tries ... (" + s + "s)");
                }
                int pos = len - 1;
                while (pos >= 0) {
                    idx[pos]++;
                    if (idx[pos] < 26) break;
                    idx[pos] = 0;
                    pos--;
                }
                if (pos < 0) break;
            }
        }
        System.out.println("not found");
    }
}

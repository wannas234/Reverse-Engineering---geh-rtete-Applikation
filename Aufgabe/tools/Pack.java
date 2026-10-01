import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Pack {

    static final String B = "dhbw-vault-bootstrap-2026";

    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        byte[] k = new byte[16];
        System.arraycopy(MessageDigest.getInstance("SHA-256")
                .digest(B.getBytes(StandardCharsets.UTF_8)), 0, k, 0, 16);
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"));
        Files.write(out, c.doFinal(Files.readAllBytes(in)));
    }
}

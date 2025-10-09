package com.chinatelecom.aigc.evaluate.common.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AESUtil {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String SECRET_KEY = "secret@14253647!";

    public static String encrypt(String data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
    }

    public static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
    }
    public static boolean isEncrypted(String data) {
        try {
            // 尝试解密数据
            String decrypted = decrypt(data);
            // 如果解密成功，并且符合预期格式（比如你知道明文的特征），说明数据是已加密的
            return decrypted != null && decrypted.length() > 0;
        } catch (Exception e) {
            // 如果解密失败，说明数据没有加密
            return false;
        }
    }
}

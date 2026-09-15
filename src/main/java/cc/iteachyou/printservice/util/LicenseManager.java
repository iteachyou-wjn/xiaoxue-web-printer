package cc.iteachyou.printservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;

/**
 * 授权许可管理器（离线非对称签名授权）。
 *
 * <p>授权码由授权工具（xiaoxue-print-service-authorization）用<b>私钥</b>签发，格式：</p>
 * <pre>{@code base64url(machine|expire|type|issue) + "." + base64url(SHA256withRSA签名)}</pre>
 *
 * <p>本类内置<b>公钥</b>验签：授权码无法伪造（私钥不在客户端）。校验内容包括：
 * 签名是否有效、授权码绑定的机器码是否与本机一致、是否在有效期内。</p>
 */
public final class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);

    /**
     * 内置公钥以"拆段异或"形式存储，运行时还原，提高反编译直接提取的难度。
     * 说明：公钥本身公开安全（仅能验签、不能伪造授权码），此仅为提高逆向门槛；
     * 真正的安全根基是私钥不外泄（私钥仅存于授权工具项目中）。
     */
    private static final byte[][] KEY_PARTS = {
        {23, 19, 19, 24, 19, 48, 27, 20, 24, 61, 49, 43, 50, 49, 51, 29, 99, 45, 106, 24, 27, 11, 31, 28, 27, 27, 21, 25, 27, 11, 98, 27, 23, 19, 19, 24, 25, 61, 17, 25, 27, 11, 31, 27, 107, 45, 113, 46, 15, 13, 44, 35, 111, 99, 8, 45, 98, 44, 48, 98, 49, 53, 40, 43, 29, 8, 55, 21, 107, 63, 0, 54, 110, 24, 109, 15, 56, 99, 8, 14, 35, 54, 28, 56, 51, 22, 52, 98, 51, 49, 104, 29, 55, 56, 50, 113, 46, 41, },
        {12, 25, 39, 7, 12, 1, 7, 2, 63, 62, 63, 5, 6, 8, 4, 90, 91, 26, 58, 7, 31, 88, 45, 24, 17, 32, 51, 89, 63, 60, 62, 1, 34, 39, 37, 82, 25, 83, 60, 92, 26, 38, 60, 25, 95, 63, 82, 4, 93, 0, 24, 88, 93, 40, 15, 12, 92, 91, 68, 3, 56, 19, 40, 26, 18, 12, 33, 44, 5, 64, 58, 93, 41, 68, 7, 42, 60, 62, 63, 30, 6, 28, 46, 13, 95, 5, 90, 39, 26, 7, 1, 1, 12, 9, 49, 39, 19, 24, },
        {126, 66, 103, 97, 119, 87, 94, 64, 73, 81, 86, 106, 90, 71, 10, 67, 1, 106, 88, 97, 94, 122, 121, 90, 86, 28, 88, 94, 3, 121, 94, 89, 2, 80, 3, 1, 10, 93, 101, 116, 82, 3, 87, 75, 75, 112, 6, 107, 69, 74, 122, 86, 100, 80, 93, 75, 97, 69, 70, 90, 125, 95, 117, 5, 24, 91, 97, 89, 113, 106, 28, 103, 65, 106, 69, 69, 74, 92, 113, 102, 95, 2, 112, 86, 75, 11, 69, 11, 75, 98, 0, 68, 96, 98, 69, 69, 86, 99, },
        {53, 7, 60, 39, 8, 7, 69, 60, 40, 69, 55, 28, 14, 30, 54, 19, 26, 31, 77, 63, 25, 36, 41, 10, 14, 36, 72, 27, 56, 46, 14, 74, 45, 22, 7, 47, 9, 69, 48, 77, 15, 72, 60, 16, 60, 43, 7, 86, 13, 21, 17, 43, 43, 22, 40, 54, 36, 30, 16, 13, 51, 55, 58, 42, 79, 37, 86, 54, 14, 73, 57, 75, 24, 68, 9, 16, 59, 28, 72, 56, 73, 20, 9, 37, 58, 10, 4, 75, 30, 78, 58, 10, 52, 57, 60, 44, 60, 63, },
    };

    private static final byte[] KEYS = {0x5A, 0x6B, 0x33, 0x7D};

    /** 运行时还原公钥（X.509 Base64） */
    private static String buildPublicKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < KEY_PARTS.length; i++) {
            byte[] part = KEY_PARTS[i];
            byte key = KEYS[i];
            for (byte b : part) {
                sb.append((char) ((b ^ key) & 0xFF));
            }
        }
        return sb.toString();
    }

    /** 授权状态 */
    public enum Status {
        /** 未授权（无授权码） */
        NOT_LICENSED,
        /** 已授权且在有效期内 */
        VALID,
        /** 已过期 */
        EXPIRED,
        /** 机器码不匹配（授权码是给别的机器签的） */
        MACHINE_MISMATCH,
        /** 授权码无效（格式错误 / 签名校验失败 / 被篡改） */
        INVALID,
        /** 校验过程出错 */
        ERROR
    }

    /** 授权结果信息 */
    public static final class LicenseInfo {
        public final Status status;
        public final String machine;
        public final LocalDate expire;
        public final String type;
        public final long issue;

        public LicenseInfo(Status status, String machine, LocalDate expire, String type, long issue) {
            this.status = status;
            this.machine = machine;
            this.expire = expire;
            this.type = type;
            this.issue = issue;
        }
    }

    /** 授权码持久化文件：~/.xiaoxue-print/license.dat */
    private static final Path STORE = Paths.get(
            System.getProperty("user.home", "."), ".xiaoxue-print", "license.dat");

    private static volatile LicenseInfo cachedInfo;

    private LicenseManager() {
    }

    /** 校验授权码，返回详细状态（不读取本地持久化授权码） */
    public static LicenseInfo validate(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return new LicenseInfo(Status.NOT_LICENSED, null, null, null, 0);
        }
        try {
            Parsed d = parseAndVerify(licenseKey.trim());
            String local = MachineCode.getMachineCode();
            if (!local.equalsIgnoreCase(d.machine)) {
                return new LicenseInfo(Status.MACHINE_MISMATCH, d.machine, d.expire, d.type, d.issue);
            }
            if (LocalDate.now().isAfter(d.expire)) {
                return new LicenseInfo(Status.EXPIRED, d.machine, d.expire, d.type, d.issue);
            }
            return new LicenseInfo(Status.VALID, d.machine, d.expire, d.type, d.issue);
        } catch (SecurityException e) {
            log.warn("授权码签名校验失败: {}", e.getMessage());
            return new LicenseInfo(Status.INVALID, null, null, null, 0);
        } catch (IllegalArgumentException e) {
            log.warn("授权码解析失败: {}", e.getMessage());
            return new LicenseInfo(Status.INVALID, null, null, null, 0);
        } catch (Exception e) {
            log.warn("授权码校验出错: {}", e.getMessage(), e);
            return new LicenseInfo(Status.ERROR, null, null, null, 0);
        }
    }

    /** 获取当前授权状态（优先读本地持久化的授权码） */
    public static LicenseInfo getStatus() {
        LicenseInfo cached = cachedInfo;
        if (cached != null) {
            return cached;
        }
        String key = readStored();
        if (key == null) {
            cachedInfo = new LicenseInfo(Status.NOT_LICENSED, null, null, null, 0);
            return cachedInfo;
        }
        cachedInfo = validate(key);
        return cachedInfo;
    }

    /** 是否已授权且在有效期内 */
    public static boolean isAuthorized() {
        return getStatus().status == Status.VALID;
    }

    /** 读取本地已保存的授权码；无则返回 null */
    public static String getStoredLicenseKey() {
        return readStored();
    }

    /** 取消授权：删除本地保存的授权码，恢复未授权状态 */
    public static synchronized void revoke() {
        try {
            Files.deleteIfExists(STORE);
        } catch (Exception e) {
            log.warn("删除授权码文件失败: {}", e.getMessage());
        }
        cachedInfo = null;
    }

    /** 校验并持久化授权码；仅当校验通过（VALID）时保存成功 */
    public static synchronized boolean save(String licenseKey) {
        LicenseInfo info = validate(licenseKey);
        if (info.status != Status.VALID) {
            return false;
        }
        try {
            Files.createDirectories(STORE.getParent());
            Files.write(STORE, licenseKey.trim().getBytes(StandardCharsets.UTF_8));
            cachedInfo = info;
            return true;
        } catch (Exception e) {
            log.error("保存授权码失败", e);
            return false;
        }
    }

    /** 读取本地持久化的授权码；无则返回 null */
    private static String readStored() {
        try {
            if (!Files.exists(STORE)) {
                return null;
            }
            String key = new String(Files.readAllBytes(STORE), StandardCharsets.UTF_8).trim();
            return key.isEmpty() ? null : key;
        } catch (Exception e) {
            log.warn("读取本地授权码失败: {}", e.getMessage());
            return null;
        }
    }

    /** 解析并验签授权码（不校验机器码/有效期，仅验签） */
    private static Parsed parseAndVerify(String licenseKey) throws Exception {
        String[] parts = licenseKey.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("授权码格式错误");
        }
        byte[] dataBytes = Base64.getUrlDecoder().decode(parts[0]);
        byte[] sigBytes = Base64.getUrlDecoder().decode(parts[1]);
        String data = new String(dataBytes, StandardCharsets.UTF_8);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey());
        sig.update(dataBytes);
        if (!sig.verify(sigBytes)) {
            throw new SecurityException("签名校验不通过");
        }

        String[] f = data.split("\\|", -1);
        if (f.length != 4) {
            throw new IllegalArgumentException("授权码数据字段错误");
        }
        Parsed p = new Parsed();
        p.machine = f[0];
        p.expire = LocalDate.parse(f[1]);
        p.type = f[2];
        p.issue = Long.parseLong(f[3]);
        return p;
    }

    private static PublicKey publicKey() throws Exception {
        byte[] der = Base64.getDecoder().decode(buildPublicKey());
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** 解析后的授权数据 */
    private static final class Parsed {
        String machine;
        LocalDate expire;
        String type;
        long issue;
    }
}

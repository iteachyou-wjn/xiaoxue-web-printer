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
 * <p>授权码由授权工具（dreamer-print-service-authorization）用<b>私钥</b>签发，格式：</p>
 * <pre>{@code base64url(machine|expire|type|issue) + "." + base64url(SHA256withRSA签名)}</pre>
 *
 * <p>本类内置<b>公钥</b>验签：授权码无法伪造（私钥不在客户端）。校验内容包括：
 * 签名是否有效、授权码绑定的机器码是否与本机一致、是否在有效期内。</p>
 */
public final class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);

    /** 内置公钥（X.509 Base64），与授权工具 private_key.pem 配套。私钥变化需同步更新此值。 */
    public static final String PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1w+tUWvy59Rw8vj8korqGRmO1eZl4B7Ub9RTylFbiLn8ik2Gmbh+tsgrLlgjliTUTnmco10qQlt3FszKX2TWUjILN9r8W7qMWr4T9o6ks36Cdg70/hSxCqygJGn+Q6B/lAWUTumwEf4n1LqljjgbZLxsMqTRDdmszbeYit9p2YkRmIJie/km0Jmj1c029nVGa0dxxC5XvyIeWcnxRvuiNlF6+hRjBY/TrYvvyoBUl1Cex8v8xQ3wSQvvePHzAZuz8AU8JascKngb0BdYTwsY5fESs7PkzRt8M0r5AmAVz+phlVVkUKYcmpNJGW2X+Ks4D6e9tmFa5E4itXGwy6c3GwIDAQAB";

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

    /** 授权码持久化文件：~/.dreamer-print/license.dat */
    private static final Path STORE = Paths.get(
            System.getProperty("user.home", "."), ".dreamer-print", "license.dat");

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
        byte[] der = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
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

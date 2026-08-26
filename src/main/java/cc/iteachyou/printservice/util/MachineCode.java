package cc.iteachyou.printservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

/**
 * 本机机器码工具。
 *
 * 组合 主机名 / MAC 地址 / 系统UUID / CPU ID / 主板序列号 / 磁盘序列号 等硬件标识，
 * 经 SHA-256 摘要后格式化为易读的机器码（大写、每 4 位一组以 "-" 分隔）。
 * 机器码用于软件授权许可，同一台机器结果稳定。
 */
public final class MachineCode {

    private static final Logger log = LoggerFactory.getLogger(MachineCode.class);

    /** 机器码缓存（同一进程内机器码不变，避免重复执行外部命令） */
    private static volatile String cached;

    private MachineCode() {
    }

    /**
     * 获取本机机器码（SHA-256 前 16 字节，大写，每 4 位一组以 "-" 分隔）
     *
     * @return 机器码字符串；获取失败时返回基于主机名的回退值
     */
    public static String getMachineCode() {
        if (cached != null) {
            return cached;
        }
        String source = buildSource();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16 && i < digest.length; i++) {
                hex.append(String.format("%02X", digest[i]));
            }
            String raw = hex.toString();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.length(); i += 4) {
                if (sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(raw, i, Math.min(i + 4, raw.length()));
            }
            cached = sb.toString();
        } catch (Exception e) {
            log.error("计算机器码失败", e);
            cached = Integer.toHexString(source.hashCode()).toUpperCase();
        }
        return cached;
    }

    /**
     * 组合本机标识来源：主机名 + MAC + 系统UUID + CPU ID + 主板序列号 + 磁盘序列号
     */
    private static String buildSource() {
        StringBuilder sb = new StringBuilder();
        sb.append(hostName());
        sb.append('|').append(macAddresses());
        sb.append('|').append(query("wmic", "csproduct", "get", "uuid"));
        sb.append('|').append(query("wmic", "cpu", "get", "processorid"));
        sb.append('|').append(query("wmic", "baseboard", "get", "serialnumber"));
        sb.append('|').append(query("wmic", "diskdrive", "get", "serialnumber"));
        return sb.toString();
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    private static String macAddresses() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                for (byte b : mac) {
                    sb.append(String.format("%02X", b));
                }
                sb.append(';');
            }
        } catch (Exception e) {
            log.warn("获取 MAC 地址失败", e);
        }
        return sb.toString();
    }

    /**
     * 执行外部命令获取硬件标识。
     * 优先 wmic；若 wmic 不存在（新系统已移除）则回退 PowerShell Get-CimInstance。
     * 非 Windows 平台返回空串。
     */
    private static String query(String... cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return "";
        }
        try {
            return runProcess(cmd);
        } catch (Exception e) {
            // wmic 不可用，回退 PowerShell
            String ps = toPowerShell(cmd);
            if (ps != null) {
                try {
                    return runProcess("powershell", "-NoProfile", "-Command", ps);
                } catch (Exception ex) {
                    log.debug("PowerShell 查询失败: {} - {}", ps, ex.getMessage());
                    return "";
                }
            }
            log.debug("硬件查询命令执行失败: {} - {}", String.join(" ", cmd), e.getMessage());
            return "";
        }
    }

    private static String runProcess(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean done = p.waitFor(8, TimeUnit.SECONDS);
        Charset cs;
        try {
            cs = Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"));
        } catch (Exception e) {
            cs = StandardCharsets.UTF_8;
        }
        String out = new String(p.getInputStream().readAllBytes(), cs);
        if (!done) {
            p.destroyForcibly();
        }
        return out == null ? "" : out.trim();
    }

    /**
     * 将 wmic 查询转换为 PowerShell Get-CimInstance 等价命令
     */
    private static String toPowerShell(String... cmd) {
        if (cmd.length < 4 || !"wmic".equalsIgnoreCase(cmd[0])) {
            return null;
        }
        String entity = cmd[1].toLowerCase();
        String property = cmd[3];
        String psClass;
        switch (entity) {
            case "csproduct":
                psClass = "Win32_ComputerSystemProduct";
                break;
            case "cpu":
                psClass = "Win32_Processor";
                break;
            case "baseboard":
                psClass = "Win32_BaseBoard";
                break;
            case "diskdrive":
                psClass = "Win32_DiskDrive";
                break;
            default:
                return null;
        }
        return "(Get-CimInstance " + psClass + ")." + property;
    }
}

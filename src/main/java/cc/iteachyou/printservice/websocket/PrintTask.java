package cc.iteachyou.printservice.websocket;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 打印任务模型
 */
public class PrintTask {

    /** 任务状态枚举 */
    public enum Status {
        PENDING("待打印"),
        PRINTING("打印中"),
        COMPLETED("已完成"),
        FAILED("失败");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 任务唯一 ID */
    private final String id;

    /** 任务名称 */
    private String name;

    /** 打印内容（文件路径或文本） */
    private String content;

    /** 打印机名称 */
    private String printerName;

    /** 纸张类型（如 A4、A3） */
    private volatile String pageSize;

    /** 任务状态（volatile：WebSocket 线程写入，UI 线程轮询读取） */
    private volatile Status status;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 完成时间（volatile：多线程写入） */
    private volatile LocalDateTime finishTime;

    /** 错误信息（失败时） */
    private volatile String errorMessage;

    public PrintTask() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.createTime = LocalDateTime.now();
        this.status = Status.PENDING;
    }

    public PrintTask(String name, String content, String printerName) {
        this(name, content, printerName, null);
    }

    public PrintTask(String name, String content, String printerName, String pageSize) {
        this();
        this.name = name;
        this.content = content;
        this.printerName = printerName;
        this.pageSize = pageSize;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.COMPLETED || status == Status.FAILED) {
            this.finishTime = LocalDateTime.now();
        }
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public String getCreateTimeFormatted() {
        return createTime.format(FORMATTER);
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public String getFinishTimeFormatted() {
        return finishTime != null ? finishTime.format(FORMATTER) : "-";
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return String.format("PrintTask{id='%s', name='%s', status=%s}", id, name, status);
    }
}

package com.campusoverflow.shared.util;

/** 数据库唯一约束异常解析工具。 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** 收集异常链上的 message，供调用方按数据库约束名匹配；限制长度避免异常日志无限膨胀。 */
    public static String messagesOf(Throwable ex) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = ex; current != null && messages.length() < 4096; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}

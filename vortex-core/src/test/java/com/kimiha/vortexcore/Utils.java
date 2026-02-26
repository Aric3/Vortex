package com.kimiha.vortexcore;

import java.util.UUID;

public class Utils {
    // 随机生成一个16位的字符串 作为clOrderId
    public static String randomClOrderId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);
    }

    // 随机生成一个10位的字符串 作为shareholderId
    public static String randomShareholderId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 10);
    }

}

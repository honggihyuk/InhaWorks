package com.mysite.sbb.common.util;

import tools.jackson.databind.json.JsonMapper;

// Outbox 이벤트 내용(payload)을 JSON 문자열로 바꾸고 되돌린다. (Spring Boot 4 = Jackson 3 : tools.jackson 패키지)
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}

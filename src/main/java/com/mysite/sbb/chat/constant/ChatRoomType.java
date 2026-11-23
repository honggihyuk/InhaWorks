package com.mysite.sbb.chat.constant;

import lombok.Getter;

// 대화방 종류. 매장·팀·공지 채널은 소속으로 자동 참여하고 나갈 수 없다
@Getter
public enum ChatRoomType {
    WORKPLACE("매장 채널", false),
    TEAM("팀 채널", false),
    NOTICE("전체 공지", false),
    INQUIRY("문의", true),
    GROUP("그룹", true),
    DIRECT("1:1", true);

    private final String label;
    private final boolean leavable;

    ChatRoomType(String label, boolean leavable) {
        this.label = label;
        this.leavable = leavable;
    }
}

package com.pipibooks.eventschema

/** 사용자 이벤트 타입. 앞의 다섯 개는 클라이언트가, 나머지는 core가 보낸다. */
enum class EventType {
    CARD_IMPRESSION,
    SLIDE_VIEW,
    CARD_DWELL,
    CARD_SKIP,
    CARD_SHARE,
    SHELF_ADD,
    STATUS_CHANGE,
    IDENTITY_LINK,
    MEMBER_DELETED,
}

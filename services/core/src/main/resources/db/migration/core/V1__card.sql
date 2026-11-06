CREATE TABLE core.card (
    id           bigint       PRIMARY KEY,
    book_id      bigint       NOT NULL UNIQUE, -- catalog.book.id 값만 저장한다(스키마 간 FK 없음)
    source       varchar(16)  NOT NULL CHECK (source IN ('AUTO', 'CURATED')),
    status       varchar(16)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    published_at timestamptz,
    edited_at    timestamptz,
    created_at   timestamptz  NOT NULL
);

CREATE INDEX card_status_published_at_idx ON core.card (status, published_at DESC);

CREATE TABLE core.card_slide (
    id       bigint       PRIMARY KEY,
    card_id  bigint       NOT NULL REFERENCES core.card (id) ON DELETE CASCADE,
    position int          NOT NULL,
    type     varchar(16)  NOT NULL CHECK (type IN ('COVER', 'HOOK', 'QUOTE', 'INFO', 'FOR_WHOM', 'KEYWORDS')),
    payload  jsonb        NOT NULL,
    UNIQUE (card_id, position)
);

-- 다른 스키마(catalog)를 읽어 동기화하는 작업의 진행 위치. name별로 마지막 처리 ID를 저장한다.
CREATE TABLE core.sync_cursor (
    name    varchar(64) PRIMARY KEY,
    last_id bigint      NOT NULL
);

CREATE TABLE catalog.card_draft (
    id                bigint       PRIMARY KEY,
    book_id           bigint       NOT NULL REFERENCES catalog.book (id),
    slides            jsonb        NOT NULL,
    generator_name    varchar(64)  NOT NULL,
    generator_version int          NOT NULL,
    auto_publishable  boolean      NOT NULL,
    blocked_reasons   text[]       NOT NULL DEFAULT '{}',
    created_at        timestamptz  NOT NULL,
    CHECK (auto_publishable = (cardinality(blocked_reasons) = 0))
);

CREATE INDEX card_draft_book_created_idx ON catalog.card_draft (book_id, created_at);

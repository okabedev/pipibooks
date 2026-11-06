CREATE TABLE catalog.book (
    id                bigint       PRIMARY KEY,
    isbn13            char(13)     NOT NULL UNIQUE,
    title             text         NOT NULL,
    author            text,
    publisher         text,
    publish_predate   date,
    kdc               varchar(16),
    pages             int,
    cover_url         text,
    introduction      text,
    table_of_contents text,
    ea_add_code       varchar(5),
    form              varchar(64),
    ebook_yn          boolean,
    set_isbn          varchar(13),
    fetched_at        timestamptz  NOT NULL
);

CREATE TABLE catalog.collect_run (
    id            bigint       PRIMARY KEY,
    type          varchar(16)  NOT NULL CHECK (type IN ('NEW_BOOKS', 'SEED_ISBN', 'SEED_KEYWORD')),
    params        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    status        varchar(16)  NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    fetched_count int          NOT NULL DEFAULT 0,
    saved_count   int          NOT NULL DEFAULT 0,
    draft_count   int          NOT NULL DEFAULT 0,
    error         text,
    started_at    timestamptz  NOT NULL,
    finished_at   timestamptz
);

CREATE INDEX collect_run_started_at_idx ON catalog.collect_run (started_at DESC);

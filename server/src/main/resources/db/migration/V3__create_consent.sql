-- Consent texts are global and versioned; one version means the same thing in every language.
CREATE TABLE consent_text_versions (
    version            integer     PRIMARY KEY CHECK (version > 0),
    requires_reconsent boolean     NOT NULL DEFAULT false,
    published_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE consent_texts (
    version     integer       NOT NULL REFERENCES consent_text_versions (version),
    language    varchar(8)    NOT NULL,
    label       varchar(200)  NOT NULL,
    description varchar(2000) NOT NULL,
    policy_url  varchar(2048) NOT NULL,
    PRIMARY KEY (version, language)
);

-- Append-only decision history; the current decision is the user's row with the highest id.
CREATE TABLE consent_decisions (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose       varchar(32) NOT NULL,
    status        varchar(16) NOT NULL CHECK (status IN ('granted', 'denied')),
    text_version  integer     NOT NULL,
    text_language varchar(8)  NOT NULL,
    decided_at    timestamptz NOT NULL,
    source        varchar(16) NOT NULL CHECK (source IN ('register', 'update')),
    CONSTRAINT consent_decisions_text_fkey FOREIGN KEY (text_version, text_language)
        REFERENCES consent_texts (version, language)
);

CREATE INDEX consent_decisions_user_purpose_idx ON consent_decisions (user_id, purpose, id DESC);

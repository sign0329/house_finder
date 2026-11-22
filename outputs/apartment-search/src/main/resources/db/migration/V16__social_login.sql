CREATE TABLE social_identity (
 provider VARCHAR(12) NOT NULL CHECK (provider IN ('google','kakao')),
 subject VARCHAR(255) NOT NULL,
 user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
 PRIMARY KEY(provider, subject),
 UNIQUE(user_id)
);
CREATE TABLE social_registration_lock (id INTEGER PRIMARY KEY);
INSERT INTO social_registration_lock(id) VALUES(1);

-- Codecracker schema
-- Extends the template's todos table with the tables needed for
-- authentication, user management, lobby, and game logic.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS games (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    status                   ENUM('WAITING', 'IN_PROGRESS', 'FINISHED') NOT NULL DEFAULT 'WAITING',
    secret_code              VARCHAR(20) NOT NULL,
    max_rounds               INT NOT NULL DEFAULT 10,
    round_time_limit_seconds INT NOT NULL DEFAULT 30,
    started_at               TIMESTAMP NULL,
    ended_at                 TIMESTAMP NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS game_players (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    ready      BOOLEAN NOT NULL DEFAULT FALSE,
    result     ENUM('WIN', 'DRAW', 'LOSS', 'PENDING') NOT NULL DEFAULT 'PENDING',
    points     INT NOT NULL DEFAULT 0,
    joined_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_game_player (game_id, user_id)
);

CREATE TABLE IF NOT EXISTS rounds (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id      BIGINT NOT NULL,
    round_number INT NOT NULL,
    started_at   TIMESTAMP NULL,
    ended_at     TIMESTAMP NULL,
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    UNIQUE KEY uq_game_round (game_id, round_number)
);

CREATE TABLE IF NOT EXISTS guesses (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    round_id                BIGINT NOT NULL,
    user_id                 BIGINT NOT NULL,
    guessed_code            VARCHAR(20) NULL,
    correct_position_count  INT NULL,
    correct_color_count     INT NULL,
    is_valid                BOOLEAN NOT NULL DEFAULT TRUE,
    submitted_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (round_id) REFERENCES rounds(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_round_user (round_id, user_id)
);

-- Indexes to support common queries
CREATE INDEX idx_game_players_result ON game_players(result);
CREATE INDEX idx_games_ended_at ON games(ended_at);

-- Initial admin account required at first startup.
-- Username: admin / Password: Admin1234 (bcrypt hash below).
-- This is a LOCAL DEVELOPMENT credential only — rotate it (or the whole
-- hash) before this schema is ever used outside your own machine.
INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2a$10$NmaBJvoSRFPhe1xpVxwiAOxeUQ1zZBocmGjgDmrotlz.4NGDLRHFm', 'ADMIN')
ON DUPLICATE KEY UPDATE username = username;

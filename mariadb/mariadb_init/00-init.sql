-- ====================================================
-- GEN1002 Informatik-Projekt - SoSe26 - Süß/Rupp
-- Initial Schema & Configuration
-- ============================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS todos (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  text VARCHAR(100) NOT NULL,
  done BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_todos_done (done),
  KEY idx_todos_created_at (created_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================
-- Schema initialization complete
-- ============================================

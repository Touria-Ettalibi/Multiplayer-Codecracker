SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT IGNORE INTO todos (id, text, done)
VALUES (1, 'Benutzer und Rollen modellieren', false),
       (2, 'Registrierung implementieren', false),
       (3, 'JWT-Authentifizierung ergänzen', false),
       (4, 'Login und Logout implementieren', false),
       (5, 'Benutzerverwaltung für Admins umsetzen', false),
       (6, 'Lobby über WebSockets aktualisieren', false),
       (7, 'Bereitschaftsphase umsetzen', false),
       (8, 'Spielfeld und Farbauswahl erstellen', false),
       (9, 'Rundenzeitlimit umsetzen', false),
       (10, 'Farbcodes serverseitig auswerten', false),
       (11, 'Gewinner und Punkte ermitteln', false),
       (12, 'Highscore anzeigen', false),
       (13, 'REST- und WebSocket-API dokumentieren', false);

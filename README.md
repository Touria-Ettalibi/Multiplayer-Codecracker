# Informatikprojekt SoSe 2026

> Dozenten: Sebastian Süß & Steffen Rupp

## Projektaufgabe - Multiplayer Codecracker

Entwickeln Sie eine Multiplayer-Anwendung nach dem Vorbild des bekannten Spiels [**Mastermind (Codecracker)**](https://de.wikipedia.org/wiki/Mastermind_(Spiel)).

Hierbei wird zu Beginn des Spiels ein geheimer Farbcode vom Server erzeugt,
der anschließend rundenbasiert von den Spielern erraten werden muss.
Nach jeder Runde erhält ein Spieler Hinweise darauf,
ob Farben aus seinem Versuch in dem geheimen Code vorhanden sind und an der richtigen Position stehen.

Die Anwendung soll als klassische Client-Server-Webanwendung umgesetzt werden.

## Inhaltsverzeichnis

1. [Technische Anforderungen](#technische-anforderungen)
1. [Funktionale Anforderungen](#funktionale-anforderungen)
    1. [Login](#login)
    1. [Benutzerverwaltung](#benutzerverwaltung)
    1. [Dashboard/GUI](#dashboardgui)
    1. [Basiskonfiguration des Spiels](#basiskonfiguration-des-spiels)
    1. [Spielfeld](#spielfeld)
    1. [Gewinnermittlung](#gewinnermittlung)
1. [Nicht funktionale Anforderungen](#nicht-funktionale-anforderungen)
1. [Optionale Bonusaufgaben](#optionale-bonusaufgaben)
1. [Hinweis zur Nutzung von KI und zur Abnahmeprüfung](#hinweis-zur-nutzung-von-ki-und-zur-abnahmeprüfung)
1. [Erste Schritte](#erste-schritte)
    1. [Start der App](#start-der-app)
    1. [Start der App für das Development](#start-der-app-für-das-development)
1. [Mitgeliefertes Todo-Beispiel](#mitgeliefertes-todo-beispiel)
    1. [Todo-REST-Schnittstelle](#todo-rest-schnittstelle)
    1. [Todo-WebSocket-Schnittstelle](#todo-websocket-schnittstelle)
    1. [Speicherung der Todos](#speicherung-der-todos)

Die Dateien im Verzeichnis `mockup/` zeigen mögliche Gestaltungen und ausgewählte Zustände der Benutzeroberfläche.
Sie dienen ausschließlich als visuelle Orientierung und müssen nicht pixelgenau übernommen werden. Maßgeblich sind
die nachfolgenden fachlichen und technischen Anforderungen. Bei Abweichungen oder Widersprüchen hat diese README
Vorrang.

## Technische Anforderungen

| Komponente           | Technologie                                                                                           |
|----------------------|-------------------------------------------------------------------------------------------------------|
| Frontend             | TypeScript, HTML, CSS, Bootstrap, Handlebars, aktuelle Node.js-LTS-Version, Vite                      |
| Backend              | Vert.x 5.1.2 mit Java Virtual Threads, Java ab Version 21, RESTful-API, JWT, Vert.x SQL Client, Maven |
| Datenbank            | MariaDB, phpMyAdmin                                                                                   |
| Kommunikation        | HTTP, WebSockets                                                                                      |
| Entwicklungsumgebung | VS Code oder IntelliJ IDEA                                                                            |
| Container-Tool       | Podman oder Docker                                                                                    |

## Funktionale Anforderungen

### Login

- Der Einstiegspunkt in die Anwendung ist eine Loginseite, in der sich ein Nutzer mit seinem Nutzernamen und Passwort
  anmelden kann.
- Neue Nutzer können sich registrieren und sich anschließend auf der Loginseite anmelden.
- Bei erfolgreicher Anmeldung wird der Nutzer automatisch zum Dashboard weitergeleitet.
- War die Anmeldung nicht erfolgreich, dann bleibt der Nutzer auf der Loginseite und bekommt eine entsprechende
  Fehlermeldung angezeigt.
- Meldet der Benutzer sich von der Anwendung ab (Logout), dann wird er automatisch wieder zur Loginseite weitergeleitet.

### Benutzerverwaltung

- Es gibt zwei Rollen: Admin und User.
- Nutzerkonten enthalten mindestens einen Nutzernamen, ein Passwort und eine Rolle (User oder Admin).
- Der Nutzername muss über alle Benutzer der Anwendung eindeutig sein.
- Passwörter müssen als sichere Hashwerte in der Datenbank gespeichert werden.
- Nutzer haben Zugriff auf das Dashboard und können Spiele starten.
- Besonderheiten bei Administratoren:
  - Bei der ersten Nutzung der Anwendung muss bereits ein Admin in der Datenbank existieren. Dieser wird im Vorfeld
    über ein Initialisierungsskript angelegt.
  - Nur der Admin bekommt einen Menüpunkt für die Benutzerverwaltung auf dem Dashboard angezeigt.
  - Der Nutzer mit der Rolle Admin kann Nutzerkonten verwalten (Anzeigen, Suchen, Hinzufügen, Bearbeiten, Löschen).

### Dashboard/GUI

- Das Dashboard stellt eine allgemeine Übersicht über die Spieler in der Lobby, aktuelle Spielinformationen und den
  Highscore bereit.
- Informationen zum angemeldeten Spieler, wie zum Beispiel Spielername und Rolle, werden im Header angezeigt.
- Es enthält eine Liste aller Spieler, die aktuell angemeldet sind (Lobby). Zustandsänderungen der Lobby werden über
  WebSockets gesendet.
- Ein Spiel kann von einem beliebigen Spieler in der Lobby gestartet werden.
- Beim Spielstart werden alle Spieler in der Lobby zur Teilnahme eingeladen.
- Eingeladene Spieler haben 30 Sekunden Zeit, ihre Bereitschaft zu bestätigen oder abzulehnen.
- Spieler ohne rechtzeitige Bestätigung nehmen nicht am Spiel teil.
- Das Spiel startet nach Ablauf der Frist mit allen bereiten Spielern, sofern mindestens zwei Spieler bereit sind.
  Andernfalls wird der Spielstart abgebrochen.
- Nach dem Start bekommen alle Spieler, die ihre Bereitschaft bestätigt haben, ihr Spielfeld angezeigt.
  - Die Lobby ist während des Spiels nicht verfügbar.
  - Stattdessen werden aktuelle Informationen zum laufenden Spiel angezeigt, etwa die Spielernamen und die aktuelle
    Runde.
- Der Highscore zeigt eine Liste mit den besten fünf Spielern der letzten zehn Tage an.
  - Als Maßstab für die Berechnung des Highscores dient folgende Punktevergabe:
    - Gewonnen: 3 Punkte
    - Unentschieden: 1 Punkt
    - Verloren: -1 Punkt
  - Der Gesamtscore eines Spielers kann nicht unter 0 fallen.

### Basiskonfiguration des Spiels

Sofern keine optionale Schwierigkeitsstufe umgesetzt wird, gilt folgende Basiskonfiguration:

- Ein Farbcode besteht aus vier Positionen.
- Für jede Position stehen die Farben Rot, Blau, Grün und Gelb zur Auswahl.
- Farben dürfen innerhalb eines Farbcodes mehrfach vorkommen.
- Ein Spiel besteht aus höchstens zehn Runden.
- Für die Abgabe eines Farbcodes stehen in jeder Runde 30 Sekunden zur Verfügung.
- Nach Ablauf des Zeitlimits endet die Eingabephase der aktuellen Runde.

### Spielfeld

- Anzeige der aktuellen Runde, in der sich der Spieler befindet.
- Pro Runde gilt das in der Basiskonfiguration festgelegte Zeitlimit. Die verbleibende Zeit wird den Spielern angezeigt.
- Eingabemöglichkeit des Codes für die aktuelle Runde.
- Zu Beginn einer Runde sind alle Codepositionen leer und müssen vom Spieler mit Farben belegt werden.
- Der Spieler bestätigt seinen vollständigen Farbcode über einen Button. Nach der Bestätigung kann der Farbcode für die
  aktuelle Runde nicht mehr geändert werden.
- Nach Ablauf des Zeitlimits übermittelt der Client den aktuell ausgewählten Farbcode automatisch. Ist der Farbcode zu
  diesem Zeitpunkt unvollständig, wird für den Spieler in dieser Runde kein gültiger Versuch gewertet.
- Spieler haben die Möglichkeit, das Spiel vorzeitig abzubrechen, wodurch es für sie als „verloren“ gewertet wird.
- Eine neue Runde beginnt erst, wenn alle aktiven Spieler einen Farbcode abgegeben haben oder das Zeitlimit abgelaufen
  ist.
- Anzeige einer Codehistorie für die eigenen Versuche inklusive des Feedbacks vom Server.
  - Für jeden abgegebenen Farbcode zeigt das Server-Feedback die Anzahl der Farben, die an der richtigen Position
    stehen, sowie die Anzahl der weiteren Farben, die im Geheimcode vorkommen, aber an der falschen Position stehen.
  - Jede Position des Geheimcodes darf bei der Auswertung höchstens einmal berücksichtigt werden.
  - Das Feedback verrät nicht, auf welche konkreten Positionen es sich bezieht.
- Zustandsänderungen des Spiels werden über WebSockets gesendet.

### Gewinnermittlung

- Errät genau ein Spieler den Geheimcode in einer Runde, gewinnt dieser Spieler.
- Erraten mehrere Spieler den Geheimcode in derselben Runde, erhalten ausschließlich diese Spieler ein Unentschieden. Alle übrigen Teilnehmer verlieren.
- Hat nach der letzten Runde niemand den Farbcode erraten, haben alle aktiven Spieler verloren.
- Alle Spieler erhalten bei Spielende die Information, wer gewonnen hat und wie viele Punkte jeweils erreicht wurden.

## Nicht funktionale Anforderungen

- Das Backend stellt eine RESTful-API und WebSockets für das Frontend zur Verfügung.
  - Private REST- und WebSocket-Endpunkte müssen mit einem JWT authentifiziert sein. Rollenabhängige Endpunkte müssen
    zusätzlich autorisiert werden.
  - Ungültige oder abgelaufene Tokens führen zur Ablehnung der Anfrage beziehungsweise zum Schließen der
    WebSocket-Verbindung.
    **Beispiel:** Die REST-API-Endpunkte der Benutzerverwaltung dürfen nur von einem Admin aufgerufen werden.
  - Die REST-API muss mit korrekten HTTP-Statuscodes antworten.
- Die Projektdokumentation erfolgt vollständig in der `README.md` Ihres GitLab-Projekts. Die folgenden Inhalte müssen
  zwingend dokumentiert werden.
  - Informationen zur Inbetriebnahme der Anwendung.
  - ER-Diagramm inklusive kurzer Beschreibung.
  - Beschreibung der RESTful-API und WebSocket-Endpunkte.
  - Auflistung der erfüllten und nicht erfüllten Anforderungen.
  - Falls zutreffend/umgesetzt: Auflistung von erfüllten optionalen Bonusaufgaben.
- Die Quellcodedokumentation erfolgt für Java mit JavaDoc und für TypeScript mit TSDoc.

## Optionale Bonusaufgaben

- Unterschiedliche Schwierigkeitsstufen, zum Beispiel durch:
  - mehr als 4 Farben zur Auswahl.
  - Zeitlimit pro Runde beim Spielstart einstellbar.
- Chat zwischen Spielern in der Lobby.
- Angemeldete Spieler, die nicht am laufenden Spiel teilnehmen, können dieses als Zuschauer beobachten. Zuschauer sehen
  abgegebene Tipps und den allgemeinen Spielstatus, jedoch kein Server-Feedback und keinen Geheimcode. Gewinnbringende
  Tipps werden erst nach Spielende sichtbar. Zuschauer können das Spiel nicht beeinflussen oder nachträglich daran
  teilnehmen.
- Es können mehrere Spiele gleichzeitig gespielt werden.

## Hinweis zur Nutzung von KI und zur Abnahmeprüfung

Die Nutzung von KI zur Unterstützung während der Entwicklung ist erlaubt.
Jedes Team trägt jedoch die volle Verantwortung für seine abgegebene Lösung.
Alle Teammitglieder müssen den gesamten Code verstehen und im Detail erklären können.

Im Rahmen der Abnahme können Transferaufgaben gestellt werden. Dabei müssen alle Teammitglieder konzeptionell und
anhand des Codes beschreiben können, wie ähnliche oder zusätzliche Anforderungen umgesetzt werden könnten. Sie müssen
die dafür erforderlichen Aufgaben den passenden Bereichen der Anwendung, beispielsweise Frontend, Controller, Service,
Repository oder Datenbank, korrekt zuordnen können.

## Erste Schritte

### Start der App

Die komplette Anwendung, bestehend aus Frontend, Backend und MariaDB, kann mit Podman oder Docker Compose im
Projektstammverzeichnis gestartet werden.

Erstellen Sie vor dem ersten Start eine lokale `.env`-Datei aus der mitgelieferten Beispielkonfiguration:

```bash
cp .env.example .env
```

Die Datei enthält die Datenbankkonfiguration für Compose. Die Beispielwerte sind ausschließlich für die lokale
Entwicklung vorgesehen und dürfen nicht für produktive Systeme verwendet werden. Eine lokale `.env`-Datei wird von Git
ignoriert und darf insbesondere bei geänderten Passwörtern nicht eingecheckt werden.

```bash
podman compose up --build
```

Sobald alle Container gestartet sind, stehen folgende Anwendungen zur Verfügung:

| App                  | URL                   |
|----------------------|-----------------------|
| Codecracker Frontend | http://localhost:8080 |
| phpMyAdmin           | http://localhost:8081 |

Insbesondere beim ersten Start können der Build, die Initialisierung der Datenbank und der Start des Backends einige
Zeit dauern. Die URLs sind erst erreichbar, wenn die jeweiligen Container vollständig betriebsbereit sind. Falls eine
Seite zunächst nicht gefunden wird oder noch nicht erreichbar ist, warten Sie einen Moment und prüfen Sie den Status:

```bash
podman compose ps
```

MariaDB sollte als `healthy` und die übrigen benötigten Container sollten als `running` beziehungsweise `Up` angezeigt
werden. Das Backend ist bereit, sobald dort die Meldung `Server started on port 8080` erscheint. Die laufenden
Startmeldungen können im Terminal verfolgt werden, in dem `podman compose up --build` ausgeführt wurde.

### Start der App für das Development

Um die Entwicklung zu vereinfachen, bietet es sich an, nur die Datenbank und phpMyAdmin über Docker Compose zu starten.
Frontend und Backend werden dann in der Entwicklungsumgebung (zum Beispiel VS Code oder IntelliJ IDEA) ausgeführt,
wodurch Debugging und Hot-Reloading möglich sind.

#### Podman

```bash
podman compose up mariadb phpmyadmin
```

#### Backend

Das Backend wird mit der folgenden Java-Klasse gestartet:

```
backend/src/main/java/de/thm/codecracker/Main.java
```

#### Frontend

Das Frontend wird mit den folgenden Befehlen gestartet:

```bash
cd frontend
npm ci
npm run dev
```

#### Dev-Apps

In der Entwicklungsumgebung stehen folgende Anwendungen zur Verfügung:

| App                  | URL                           |
|----------------------|-------------------------------|
| Codecracker Backend  | http://localhost:8080         |
| Codecracker Frontend | http://localhost:`<vitePort>` |
| phpMyAdmin           | http://localhost:8081         |

---

## Mitgeliefertes Todo-Beispiel

Nachdem die Anwendung lokal gestartet wurde, kann das mitgelieferte Todo-Beispiel verwendet werden, um die vorhandene
Projektstruktur sowie die REST-, EventBus- und WebSocket-Kommunikation nachzuvollziehen.

Das Template enthält eine vollständige Todo-Funktionalität als Demonstration der Schichtung
`Controller -> Service -> Repository`, des Datenbankzugriffs, einer REST-Schnittstelle und der Übertragung von
Änderungen über Vert.x EventBus und WebSockets. Diese Funktionalität gehört **nicht** zu den fachlichen Anforderungen
des Codecracker-Projekts und kann bei der Implementierung von Ihnen entfernt werden.

Die fachliche Codecracker-Funktionalität, insbesondere Authentifizierung, Benutzerverwaltung, Lobby und Spiellogik,
ist im Template nicht implementiert und muss anhand der Anforderungen ergänzt werden. Die vorhandenen Login- und
Dashboard-Seiten dienen lediglich als Ausgangsbasis für die eigene Umsetzung.

Die Todo-Schnittstellen sind im Template absichtlich nicht authentifiziert. Sie demonstrieren daher nicht die für die
Codecracker-REST- und WebSocket-Endpunkte geforderte Authentifizierung und Autorisierung. Diese muss für die eigentliche
Anwendung passend ergänzt werden.

Ein Todo wird in REST-Antworten und WebSocket-Nachrichten folgendermaßen dargestellt:

```json
{
  "id": 1,
  "text": "Login implementieren",
  "done": false,
  "createdAt": 1772532000000,
  "updatedAt": 1772532000000
}
```

`createdAt` und `updatedAt` sind Unix-Zeitstempel in Millisekunden.

### Todo-REST-Schnittstelle

Alle Todo-Endpunkte verwenden JSON. Schreibende Operationen lösen bei Erfolg zusätzlich eine Nachricht auf dem
Todo-WebSocket aus.

| Methode  | Pfad              | Request-Body                      | Erfolgsantwort       | Bedeutung                           |
|----------|-------------------|-----------------------------------|----------------------|-------------------------------------|
| `POST`   | `/api/todos`      | `{ "text": "..." }`               | `201` mit Todo       | Neues Todo erstellen                |
| `GET`    | `/api/todos`      | –                                 | `200` mit Todo-Array | Alle Todos nach ID sortiert abrufen |
| `GET`    | `/api/todos/{id}` | –                                 | `200` mit Todo       | Einzelnes Todo abrufen              |
| `PUT`    | `/api/todos/{id}` | `{ "text": "...", "done": true }` | `200` mit Todo       | Vorhandenes Todo aktualisieren      |
| `DELETE` | `/api/todos/{id}` | –                                 | `204` ohne Body      | Vorhandenes Todo löschen            |

Der Text eines Todos ist verpflichtend, darf nicht leer sein und höchstens 100 Zeichen enthalten. Beim Aktualisieren ist
`done` optional; fehlt der Wert, bleibt der bisherige Erledigungsstatus erhalten.

Fehler werden als JSON-Objekt zurückgegeben:

```json
{
  "error": "Todo not found"
}
```

Die Demo verwendet `400` für ungültige Eingaben und ungültige Todo-IDs, `404` für ein nicht gefundenes Todo und `500`
für unerwartete Serverfehler. Konkrete Beispielaufrufe befinden sich in
`backend/todo-api-tester.http`.

### Todo-WebSocket-Schnittstelle

Der WebSocket-Endpunkt lautet `/ws/todos` und benötigt im Template keine Authentifizierung. Clients senden über diese
Verbindung Todo-Kommandos und empfangen dazu passende Antworten. Auf der Todo-Seite kann ausgewählt werden, ob Laden,
Erstellen, Aktualisieren und Löschen über REST oder über den WebSocket erfolgen sollen.

#### Client an Server

Jedes Kommando enthält einen frei gewählten `requestId`. Der Server übernimmt diesen Wert in seine Antwort, damit der
Client auch bei mehreren offenen Kommandos die passende Antwort erkennen kann.

| `type`     | `payload`                              | Bedeutung                  |
|------------|----------------------------------------|----------------------------|
| `find-all` | `{}`                                   | Alle Todos laden           |
| `create`   | `{ "text": "..." }`                  | Neues Todo erstellen       |
| `update`   | `{ "id": 1, "text": "...", "done": true }` | Todo aktualisieren |
| `delete`   | `{ "id": 1 }`                         | Todo löschen               |

Beispiel für das Erstellen eines Todos:

```json
{
  "type": "create",
  "requestId": "1",
  "payload": {
    "text": "Spiellogik implementieren"
  }
}
```

#### Server an Client

Auf jedes Kommando antwortet der Server nur dem Client, der es gesendet hat. Eine erfolgreiche Antwort enthält das
Ergebnis in `payload`:

```json
{
  "type": "response",
  "requestId": "1",
  "payload": {
    "id": 4,
    "text": "Spiellogik implementieren",
    "done": false,
    "createdAt": 1772532000000,
    "updatedAt": 1772532000000
  }
}
```

Bei einem Fehler enthält die Antwort anstelle von `payload` das Feld `error` mit der Fehlermeldung:

```json
{
  "type": "response",
  "requestId": "1",
  "error": "Todo text must not be empty"
}
```

Unabhängig davon, ob eine Änderung über REST oder WebSocket ausgelöst wurde, veröffentlicht der Server sie zusätzlich
an **alle** verbundenen WebSocket-Clients. Diese Ereignisse haben folgende Form:

| `type`    | `payload`             | Bedeutung                   |
|-----------|-----------------------|-----------------------------|
| `created` | vollständiges Todo    | Ein Todo wurde erstellt     |
| `updated` | vollständiges Todo    | Ein Todo wurde aktualisiert |
| `deleted` | `{ "id": <Todo-ID> }` | Ein Todo wurde gelöscht     |

Die `requestId` ist ausschließlich in direkten Antworten vom Typ `response` enthalten. Die Ereignisse `created`,
`updated` und `deleted` werden an alle verbundenen Clients gesendet und besitzen deshalb keine `requestId`. Wird eine
Änderung über REST ausgelöst, entsteht ebenfalls keine WebSocket-Antwort mit `requestId`; über den WebSocket wird dann
nur das Änderungsereignis verteilt.

Der WebSocket-Inspector verwendet eine eigene Verbindung. Er empfängt daher die Broadcast-Ereignisse, aber nicht die
direkten Antworten auf Kommandos, die von der Todo-Seite über deren WebSocket-Verbindung gesendet wurden. Um die
`requestId` in Anfrage und Antwort zu untersuchen, muss in den Browser-Entwicklungswerkzeugen die WebSocket-Verbindung
der Todo-Seite betrachtet und dort als Transport `WebSocket` ausgewählt werden.

Beispiel für ein neu erstelltes Todo:

```json
{
  "type": "created",
  "payload": {
    "id": 4,
    "text": "Spiellogik implementieren",
    "done": false,
    "createdAt": 1772532000000,
    "updatedAt": 1772532000000
  }
}
```

Beispiel für ein gelöschtes Todo:

```json
{
  "type": "deleted",
  "payload": {
    "id": 4
  }
}
```

Der `TodoWebSocketController` registriert den Endpunkt `/ws/todos` als Route im Vert.x-Web-`Router`. Der Router ordnet
die HTTP-Upgrade-Anfrage dem Controller zu, der daraus eine WebSocket-Verbindung erstellt. Die Übertragung von
Änderungen verläuft intern vom `TodoService` über die EventBus-Adresse `todos` zum `TodoWebSocketController`. Dieser
verteilt die Nachricht an alle aktuell mit `/ws/todos` verbundenen Clients.

Beim Verbindungsaufbau wird kein initialer Datenbestand gesendet. Ein Client lädt ihn entweder mit `GET /api/todos`
oder mit dem WebSocket-Kommando `find-all` und verarbeitet danach die WebSocket-Änderungsereignisse. Die mitgelieferte
Seite `/pages/ws-inspect.html` kann zum manuellen Beobachten der Nachrichten verwendet werden.

### Speicherung der Todos

Die Todos werden dauerhaft in der MariaDB-Tabelle `todos` gespeichert. Das Tabellenschema wird beim ersten Start durch
`mariadb/mariadb_init/00-init.sql` angelegt. `mariadb/mariadb_init/01-seed-todos.sql` fügt einige Todos als
Demonstrationsdaten ein.

| Spalte       | Bedeutung |
|--------------|-----------|
| `id`         | Automatisch vergebener, eindeutiger Primärschlüssel |
| `text`       | Verpflichtender Todo-Text mit maximal 100 Zeichen |
| `done`       | Erledigungsstatus; standardmäßig `false` |
| `created_at` | Zeitpunkt der Erstellung |
| `updated_at` | Zeitpunkt der letzten Änderung; wird von MariaDB automatisch aktualisiert |

Indizes auf `done` und `created_at` unterstützen Abfragen nach Status und Erstellungszeit. Der `TodoRepository` kapselt
alle SQL-Zugriffe und verwendet parametrisierte Abfragen. Beim Lesen werden die Datenbankspalten `created_at` und
`updated_at` in die API-Felder `createdAt` und `updatedAt` als Unix-Zeitstempel in Millisekunden umgewandelt.

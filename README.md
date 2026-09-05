
## Multiplayer Codecracker
 
A real-time multiplayer implementation of **Mastermind (Codecracker)** — at the start of each game, the server generates a secret color code, and players compete round by round to guess it. After every attempt, the server responds with feedback on how many colors are correct and how many are in the right position, until someone cracks the code or the round limit is reached.
 
Built as a classic client-server web application with authentication, lobby management, real-time WebSocket game state, and a highscore system.
 
### Tech Stack
 
| Layer | Technology |
|---|---|
| Frontend | TypeScript, HTML, CSS, Bootstrap, Handlebars, Vite |
| Backend | Vert.x 5.1.2 (Java Virtual Threads), Java 21, RESTful API, JWT auth, Vert.x SQL Client, Maven |
| Database | MariaDB, phpMyAdmin |
| Communication | HTTP, WebSockets |
| Containerization | Docker / Podman |
 
### Quickstart
 
```bash
git clone https://github.com/Touria-Ettalibi/Multiplayer-Codecracker.git
cd Multiplayer-Codecracker
cp .env.example .env
docker compose up --build
```
 
Once all containers are running:
 
| App | URL |
|---|---|
| Codecracker Frontend | http://localhost:8080 |
| phpMyAdmin | http://localhost:8081 |
 
The first build can take a moment while the database initializes. If a page isn't reachable yet, check container status with `docker compose ps` — MariaDB should show `healthy`, and the backend is ready once its logs show `Server started on port 8080`.
 
### Background
 
Originally developed as a university project (*Informatikprojekt*), based on an assignment to implement a multiplayer version of the classic board game Mastermind as a client-server web application. Full functional requirements, the ER diagram, and REST/WebSocket API documentation are below.
 

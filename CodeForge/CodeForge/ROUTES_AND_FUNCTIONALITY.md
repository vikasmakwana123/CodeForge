# CodeForge: routes and functionality

## Project provisioning

Projects are persisted in PostgreSQL. `POST /api/projects` accepts `name` and `type`, validates them, saves a project with `CREATING` status and its owner, then returns `202 Accepted` with the database-generated UUID. Provisioning runs asynchronously. Poll `GET /api/projects/{projectId}` until its status is `READY` or `FAILED`; `errorMessage` describes a provisioning failure. The project tree and file endpoints return `409 Conflict` while the project is not ready.

Register with `POST /api/auth/register` using `name`, `email`, and `password`, or sign in with `POST /api/auth/login` using `email` and `password`. Both return a bearer access token and user information. Send `Authorization: Bearer <accessToken>` on project and REST file requests. Tokens expire after the configured `JWT_EXPIRATION_SECONDS` (one hour by default). Passwords are BCrypt-hashed before storage.

The authenticated request principal exposes the user's UUID as its name. Project status, tree, REST file operations, and WebSocket file operations are scoped to that user. STOMP clients must send their bearer token as the `Authorization` CONNECT header.

| Method | Route | Body / parameters | Functionality |
| --- | --- | --- | --- |
| `POST` | `/api/projects` (also `/api/projects/create`) | JSON: `name`, `type` | Creates the pending database row and starts async scaffolding. |
| `GET` | `/api/projects/{projectId}` | UUID path parameter | Returns the authenticated owner's project status. |
| `GET` | `/api/projects/{projectId}/tree` | UUID path parameter | Returns the ready project's recursive file tree. |
| `DELETE` | `/api/projects/{projectId}` | UUID path parameter | Stops/removes recorded Docker resources, deletes the workspace, then deletes the owner's database row. |

Supported `appType` values are `react`, `vue`, `angular`, `next`/`nextjs`/`next.js`, `node`/`nodejs`, `vite`, and `spring`/`springboot`/`spring-boot`. Project names must start with a letter and contain only letters, digits, or hyphens, up to 40 characters.

Example request:

```http
POST /api/projects
Content-Type: application/json

{
  "name": "my-react-app",
  "type": "react"
}
```

Example accepted response:

```json
{
  "id": "database-generated-uuid",
  "name": "my-react-app",
  "appType": "react",
  "status": "CREATING",
  "errorMessage": null,
  "hostPort": null
}
```

## File and folder management

All file paths are relative to the app directory recorded on the project row. Every route requires both `projectId` and `path`; ownership and `READY` status are checked before file access. Traversal outside the app root and symbolic links are rejected.

| Method | Route | Parameters / body | Functionality |
| --- | --- | --- | --- |
| `POST` | `/api/files/file` | Query: `projectId`, `path` | Creates an empty file. |
| `POST` | `/api/files/folder` | Query: `projectId`, `path` | Creates a folder and missing parents. |
| `GET` | `/api/files` | Query: `projectId`, `path` | Reads a UTF-8 text file. |
| `PUT` | `/api/files` | Query: `projectId`, `path`; raw text body | Creates or replaces a file. |
| `DELETE` | `/api/files` | Query: `projectId`, `path` | Deletes a file or subfolder; the project root cannot be deleted here. |
| `GET` | `/api/files/list` | Query: `projectId`, optional `path` (defaults to `.`) | Lists one directory level. |
| `GET` | `/api/files/exists` | Query: `projectId`, `path` | Checks whether the path exists. |

## WebSocket API (STOMP + SockJS)

Connect to `/api`, send file operations to `/app/files`, and subscribe to the authenticated user's `/user/queue/files` destination. Include `projectId` in each operation; the owner is derived from the authenticated principal, not the message.

```json
{
  "projectId": "database-generated-uuid",
  "operation": "WRITE",
  "path": "src/main.js",
  "content": "console.log('updated');"
}
```

Operations are `CREATE_FILE`, `CREATE_FOLDER`, `READ`, `WRITE`, and `DELETE`. Operation results and filesystem notifications include the project ID and are sent only to that project's owner.

## Database and workspace configuration

Copy `.env.example` to `.env`, then set unique values for `DB_PASSWORD` and `JWT_SECRET`. The JWT secret must be at least 32 bytes and should be a randomly generated secret:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Compose starts PostgreSQL, the backend, and a dedicated Docker-in-Docker runner. Database name and user can be overridden with `DB_NAME` and `DB_USER`. The backend accepts `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`; `CODEFORGE_PROJECTS_BASE` configures the workspace directory and defaults to `./projects`. Compose persists the database, generated projects, and runner images in named volumes.

Choose exactly one backend runtime at a time; both the IntelliJ backend and Compose backend bind port 8080:

- **Run the backend from IntelliJ:** start only the database with `docker compose up -d codeforge-db`, then run `CodeForgeApplication` from IntelliJ. The IDE process uses `localhost:5433` and the local Docker Desktop engine.
- **Run the backend in Compose:** stop the IntelliJ application, then run `docker compose up --build`. The Compose backend uses the internal database hostname and isolated Docker runner.

Do not run `docker compose up` while also starting the IntelliJ backend. Existing projects created by one runtime may have workspace paths that are not accessible to the other runtime; inaccessible stored roots are skipped by the startup file watcher and reported in the backend log.

Spring Boot projects are downloaded directly from Spring Initializr and extracted with ZIP path validation. CLI generation, Docker image builds, and container startup use a five-minute process timeout. Opening the workspace terminal builds and starts the project's resource-limited container if it is not already running; terminal input is sent to an interactive shell inside that project's container. Disconnecting the terminal closes its shell process. Project containers are removed when the project is deleted.

The runner uses a privileged Docker-in-Docker service and does not mount the host Docker socket. This limits Docker control to the dedicated runner daemon, but privileged nested container runtimes still carry host-kernel risk; use this local development setup only on a trusted machine and do not expose the runner to untrusted networks. Rebuild with `docker compose up --build` after changing the backend Dockerfile or Compose configuration. Docker Desktop must be running.

The directory tree omits `node_modules`, `.git`, `target`, and `dist`, and does not traverse symbolic links. REST CORS origins remain limited to the localhost frontend ports configured in `WebSocketConfig`.

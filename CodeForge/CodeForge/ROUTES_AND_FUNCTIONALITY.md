# CodeForge: routes and functionality

## Overview

CodeForge is a Spring Boot backend for creating starter projects and managing the files inside them. All file-system operations are restricted to this directory:

```text
C:/Users/Vikas/Downloads/CodeForge/projects
```

Every `path` sent to the file APIs must be a non-empty path *relative* to that directory. For example, use `my-project/src/App.jsx`, not a full `C:/...` path.

## REST API

### File and folder management

Base path: `/api/files`

| Method | Route | Parameters / body | Functionality |
| --- | --- | --- | --- |
| `POST` | `/api/files/file?path={path}` | Query: `path` | Creates an empty file. Missing parent folders are created automatically. Returns `201 Created`. |
| `POST` | `/api/files/folder?path={path}` | Query: `path` | Creates a folder and any missing parent folders. Returns `201 Created`. |
| `GET` | `/api/files?path={path}` | Query: `path` | Reads a UTF-8 text file and returns its contents. |
| `PUT` | `/api/files?path={path}` | Query: `path`; raw text request body | Creates the file when it does not exist, or replaces all existing contents when it does. |
| `DELETE` | `/api/files?path={path}` | Query: `path` | Deletes a file, or recursively deletes a folder and everything inside it. |
| `GET` | `/api/files/list?path={path}` | Optional query: `path`, defaults to `.` | Lists the immediate files and folders in a directory. The response contains relative paths. |
| `GET` | `/api/files/exists?path={path}` | Query: `path` | Returns whether the path exists. |

### Examples

Create a file:

```http
POST /api/files/file?path=demo/src/main.js
```

Write or replace its content:

```http
PUT /api/files?path=demo/src/main.js
Content-Type: text/plain

console.log('Hello from CodeForge');
```

Read it:

```http
GET /api/files?path=demo/src/main.js
```

Response:

```json
{
  "path": "demo/src/main.js",
  "content": "console.log('Hello from CodeForge');"
}
```

Delete it:

```http
DELETE /api/files?path=demo/src/main.js
```

Successful create, write, and delete responses include a `message` and `path`. Invalid paths, duplicate creates, missing paths, and attempts to use a path outside `projects` return `400 Bad Request` with an `error` value.

> Warning: deleting a folder deletes all of its contents permanently.

### Project creation and tree

Base path: `/api/projects`

| Method | Route | Body / parameter | Functionality |
| --- | --- | --- | --- |
| `POST` | `/api/projects/create` | JSON: `appName`, `appType` | Generates a project in a new UUID-named folder under `projects` and returns that project ID. |
| `GET` | `/api/projects/{projectId}/tree` | Path: UUID `projectId` | Returns the project's recursive file/folder tree. |

Supported `appType` values:

- `react`
- `vue`
- `angular`
- `next`, `nextjs`, or `next.js`
- `node` or `nodejs`
- `vite`
- `spring`, `springboot`, or `spring-boot`

Create-project example:

```http
POST /api/projects/create
Content-Type: application/json

{
  "appName": "my-react-app",
  "appType": "react"
}
```

The server uses Node/NPM/NPX for React, Vue, Angular, Next, Node, and Vite templates. It uses `curl` and `tar` with Spring Initializr for Spring Boot templates; those commands must be installed and available on the server machine.

## WebSocket API (STOMP + SockJS)

Connect to the SockJS endpoint:

```text
/api
```

Send file-operation messages to:

```text
/app/files
```

Subscribe to receive both operation responses and filesystem-change notifications:

```text
/topic/files
```

### Send an operation

```json
{
  "operation": "WRITE",
  "path": "demo/src/main.js",
  "content": "console.log('updated');"
}
```

| Operation | Required fields | Functionality |
| --- | --- | --- |
| `CREATE_FILE` | `operation`, `path` | Creates an empty file. |
| `CREATE_FOLDER` | `operation`, `path` | Creates a folder. |
| `READ` | `operation`, `path` | Reads a text file; returned content is included in the topic event. |
| `WRITE` | `operation`, `path`, `content` | Creates or overwrites a file. |
| `DELETE` | `operation`, `path` | Deletes a file or recursively deletes a folder. |

A successful operation publishes an event such as:

```json
{
  "operation": "WRITE_SUCCESS",
  "path": "demo/src/main.js",
  "content": null,
  "error": null
}
```

A read success includes the file contents in `content`. Failed operations publish `operation: "ERROR"` and provide the reason in `error`.

The backend also watches the entire `projects` directory. When a file or directory changes, it publishes a notification such as:

```json
{
  "type": "MODIFIED",
  "path": "demo/src/main.js"
}
```

The possible notification types are `CREATED`, `MODIFIED`, and `DELETED`.

## Important constraints

- File APIs cannot access anything outside the configured `projects` directory; traversal paths such as `../../Windows` are rejected.
- `GET /api/files/list` lists one directory level. Use the project-tree route when you need a complete recursive tree for a created project.
- File reads and writes use UTF-8 text; the API is not designed for binary files.
- REST and WebSocket endpoints permit browser requests from every origin and port (`*`). This is convenient for local development, but should be restricted to trusted frontend origins before deploying the service publicly.

# API Reference

Web GameList Oper provides RESTful APIs for programmatic access.

## Base URL

```
http://localhost:8081/api/
```

## Authentication

Currently, no authentication is required for API access.

## Endpoints

### Games

#### Get all games
```
GET /api/games
```

**Query Parameters:**
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)
- `platformId` - Filter by platform ID
- `search` - Search keyword

**Response:**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

#### Get game by ID
```
GET /api/games/{id}
```

**Response:**
```json
{
  "id": 1,
  "name": "Game Name",
  "path": "/roms/game.zip",
  "platform": "NES",
  "developer": "Nintendo",
  "publisher": "Nintendo",
  "genre": "Action",
  "releaseDate": "1985-01-01",
  "players": 1,
  "rating": 5,
  "description": "Classic game"
}
```

#### Create game
```
POST /api/games
```

**Request Body:**
```json
{
  "name": "New Game",
  "path": "/roms/new_game.zip",
  "platform": "SNES",
  "developer": "Developer",
  "publisher": "Publisher",
  "genre": "Adventure",
  "releaseDate": "1990-01-01",
  "players": 2,
  "rating": 4,
  "description": "New game description"
}
```

#### Update game
```
PUT /api/games/{id}
```

**Request Body:**
```json
{
  "name": "Updated Name",
  "description": "Updated description"
}
```

#### Delete game
```
DELETE /api/games/{id}
```

### Platforms

#### Get all platforms
```
GET /api/platforms
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "NES",
    "shortName": "nes",
    "description": "Nintendo Entertainment System"
  }
]
```

#### Create platform
```
POST /api/platforms
```

**Request Body:**
```json
{
  "name": "PlayStation",
  "shortName": "ps1",
  "description": "Sony PlayStation"
}
```

### Import

#### Start import
```
POST /api/import
```

**Request Body:**
```json
{
  "templateName": "pegasus",
  "filePath": "/data/input/game_list.xml",
  "platformId": 1
}
```

### Export

#### Start export
```
POST /api/export
```

**Request Body:**
```json
{
  "ruleName": "pegasus",
  "platformIds": [1, 2, 3],
  "outputPath": "/data/output"
}
```

### Translation

#### Translate games
```
POST /api/translate
```

**Request Body:**
```json
{
  "gameIds": [1, 2, 3],
  "targetLanguage": "en"
}
```

## Error Handling

### Error Response Format
```json
{
  "timestamp": "2026-04-28T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Game not found",
  "path": "/api/games/999"
}
```

### HTTP Status Codes
- **200 OK** - Success
- **201 Created** - Resource created
- **400 Bad Request** - Invalid request
- **404 Not Found** - Resource not found
- **500 Internal Server Error** - Server error

## Examples

### cURL Examples

**Get all games:**
```bash
curl http://localhost:8081/api/games?page=0&size=10
```

**Get game by ID:**
```bash
curl http://localhost:8081/api/games/1
```

**Create game:**
```bash
curl -X POST http://localhost:8081/api/games \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Game",
    "path": "/roms/test.zip",
    "platform": "NES"
  }'
```

**Delete game:**
```bash
curl -X DELETE http://localhost:8081/api/games/1
```

### JavaScript Examples

```javascript
// Fetch games
async function getGames() {
  const response = await fetch('http://localhost:8081/api/games');
  const data = await response.json();
  console.log(data);
}

// Create game
async function createGame(gameData) {
  const response = await fetch('http://localhost:8081/api/games', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(gameData)
  });
  return response.json();
}
```

## Rate Limiting

Currently, there is no rate limiting implemented.

## Versioning

The API version is included in the URL path. Current version is `v1`.

```
http://localhost:8081/api/v1/games
```
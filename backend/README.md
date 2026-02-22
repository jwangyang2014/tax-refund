# Maven Wrapper

This is a one-time setup
```bash
# Generates the Maven Wrapper at the project root without recursing into submodules.
# wrapper:wrapper means Run the wrapper goal of the wrapper plugin
# The wrapper plugin creates mvnw, mvnw.cmd, and the .mvn/ directory so your project can run Maven without requiring Maven to be installed.
mvn -N wrapper:wrapper
```
This creates:
```bash
mvnw
mvnw.cmd
.mvn/
```

# Run the application

From the project root:
```bash
./mvnw -v
set -a      # turn on auto-export
source .env # load variables
set +a      # turn off auto-export
./mvnw clean spring-boot:run
```

The app starts on http://localhost:8080.

# Test the endpoints (curl)
## Register
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"yang@example.com","password":"Password123!"}' | jq
```
Response:
```bash
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```
Save the tokens.

## Call protected endpoint
```bash
ACCESS="PASTE_ACCESS_TOKEN_HERE"

curl -s http://localhost:8080/api/me \
  -H "Authorization: Bearer $ACCESS" | jq
```
## Refresh (rotation)
```bash
REFRESH="PASTE_REFRESH_TOKEN_HERE"

curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq
```

We will get a new access + refresh token, and the old refresh token is revoked.

## Logout (revokes refresh token)
```bash
curl -i -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```
## Notes
- Access tokens are short-lived and not stored server-side.
- Refresh tokens are stored hashed and rotated on every refresh.
- Logout revokes refresh token (server-side invalidation).
- The auth design supports multiple devices (multiple refresh tokens per use)

# JUnit test
```bash
cd backend
./mvnw test
```

# Run app in debug mode
With port `5005`
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005'
```

# Redis and rate limitter
- Test `/api/refund/latest`
```bash
# replace with a real token if you call with Authorization header
for i in {1..50}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    http://localhost:8080/api/refund/latest
done
```
- Expected:
  - First ~30 should be 200
  - Then you’ll see 429
- Verify Redis keys are created
```bash
docker compose exec redis redis-cli keys "rl:*"
docker compose exec redis redis-cli hgetall "rl:u:123:GET:/api/refund/latest"
```
- Local postgres and redis service
```bash
# Find the process ID
# Redis (default 6379)
lsof -nP -iTCP:6379 -sTCP:LISTEN

# Postgres (default 5432)
lsof -nP -iTCP:5432 -sTCP:LISTEN

# Also useful
ps aux | egrep 'redis-server|postgres|postmaster' | grep -v egrep

# List
brew services list

# Start/stop
brew services start redis
brew services stop redis
brew services restart redis

brew services start postgresql@16   # (or whatever version you have)
brew services stop postgresql@16
brew services restart postgresql@16

# Homebrew uses launchd under the hood. Find exact LaunchAgent/Daemon entries:
# For user-level services
ls -la ~/Library/LaunchAgents | egrep 'homebrew|redis|postgres'

# For system-level services (less common)
sudo ls -la /Library/LaunchDaemons | egrep 'homebrew|redis|postgres'

# And list loaded launchd jobs:
launchctl list | egrep 'homebrew|redis|postgres'
```
- Useful redis commands
```bash
redis-cli info keyspace

# List keys
redis-cli --scan
```
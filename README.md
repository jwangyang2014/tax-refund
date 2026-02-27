# TO DO
- Redirect to login page if authentication failed

# How to run
## Option A: Docker compose (recommended for your onsite demo)
```bash
cd tax-refund-status
docker compose up --build
```

Frontend: http://localhost:5173
Backend: http://localhost:8080

## Option B: Local dev
- Backend:
```bash
cd backend
./mvnw spring-boot:run
```
- Frontend:
```bash
cd frontend
npm i
npm run dev
```

# Test
- Backend
```bash
cd backend
./mvnw test
```
- Frontend
```bash
cd frontend
npm test
npm run lint
```
# HTTP contract

All business endpoints require `Authorization: Bearer <access token>`. Identity comes
from the token subject, never request JSON. No login endpoint is provided. `/api/v1/products` and `/api/v1/orders` are versioned aliases. Optional trusted `tenant` claims partition each subject into a separate owner namespace.

| Method | Path | Scope | Result |
|---|---|---|---|
| POST | `/api/products` | `products:write` | 201, DTO and Location |
| GET | `/api/products/{id}` | `products:read` | 200 or 404 (including another owner) |
| GET | `/api/products?page=0&size=20` | `products:read` | DTO list, max 100, stable name/id sort |
| POST | `/api/orders` | `products:write` | 201, idempotent order DTO and Location |
| GET | `/api/orders/{id}` | `products:read` | 200 or 404 |
| GET | `/actuator/health/liveness` | public | Process liveness |

Product JSON: `{"name":"Book","price":12.50,"stock":5}`. Name is nonblank and max 120;
price is positive with at most two decimals; initial stock is nonnegative.
Order JSON: `{"productId":"<UUID>","quantity":2}` with an `Idempotency-Key` header (1–128
nonblank characters). Keys are scoped to the owner. Repeated matching requests return
201 with the original order; conflicting reuse returns 409. Keep the same key on a retry.

Application errors use RFC Problem Details for invalid input (400), missing or inaccessible
resources (404), insufficient stock or conflicting idempotency payloads (409). Authentication and authorization failures also use safe Problem Details (401/403). Unexpected server failures are
not converted to misleading client errors.

Manual example after obtaining your own access token:

```sh
curl -i http://localhost:8080/api/products   -H "Authorization: Bearer $ACCESS_TOKEN"   -H 'Content-Type: application/json'   --data '{"name":"Book","price":12.50,"stock":5}'
```

`HttpApiIT` exercises the complete signed HTTP flow on a random local port, including
cross-owner access, idempotent ordering, invalid audience and insufficient scope.
Do not paste real tokens into source files, reports or shared terminal logs.

`POST /api/v1/limits/check` requires `products:read`: 200 when allowed, 429 with Retry-After when the per-principal fixed window is exhausted, 503 when Redis is unavailable. It never uses a forwarded client identity header.

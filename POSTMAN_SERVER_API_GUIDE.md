# Server API Postman Testing Guide

## Purpose

This guide explains how to test every REST API endpoint in the server with Postman.

It includes:

- the correct base URL for the current Docker setup
- the headers and auth you need
- example request bodies and query params
- the expected result when the request is correct
- the expected result when the request fails

This guide covers the REST controllers under `/api/v1/**`.

It does **not** cover:

- WebSocket/STOMP floor updates on `/ws`
- browser-only Google OAuth redirect start endpoints like `/oauth2/authorization/google`

You can still test `POST /api/v1/auth/oauth2/exchange` in Postman if you already have a valid one-time exchange code from the browser flow.

---

## Current Base URL

For the current Docker Compose setup, use:

```text
http://localhost
```

Reason:

- the `web` nginx container is published on host port `80`
- the `server` container is **not** published directly to host port `8080`
- nginx proxies API requests to the backend container

So in Postman:

```text
{{base_url}} = http://localhost
```

---

## Important Environment Note

The Docker server runs with:

```text
SPRING_PROFILES_ACTIVE=prod
```

That means:

- Swagger is disabled
- Postman is the correct tool for manual API testing

---

## Recommended Postman Environment Variables

Create a Postman environment with these variables:

```text
base_url=http://localhost

admin_email=admin@example.com
admin_password=Admin123

staff_email=staff@example.com
staff_password=Staff123

customer_email=customer1@example.com
customer_password=Customer123

admin_access_token=
staff_access_token=
customer_access_token=

user_id=
customer_user_id=
customer_id=
membership_id=
table_type_id=
table_id=
pricing_rule_id=
menu_item_id=
session_id=
reservation_id=
order_id=
invoice_id=
```

Adjust the seeded account credentials to match your database.

---

## Postman Setup

### 1. Cookies

Keep Postman cookies enabled.

Reason:

- login/register/refresh set a refresh-token cookie
- refresh/logout depend on that cookie

### 2. Authorization

For protected requests, use:

```text
Authorization: Bearer {{admin_access_token}}
```

Or switch to `{{staff_access_token}}` / `{{customer_access_token}}` depending on the endpoint.

### 3. Common JSON Header

Use this header for JSON requests:

```text
Content-Type: application/json
```

### 4. Refresh / Logout Header

These two endpoints require an extra header:

```text
X-Requested-With: XMLHttpRequest
```

Without it, the request should fail with `403 Forbidden`.

---

## Role Matrix

| Module | Role |
|---|---|
| `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password`, `/oauth2/exchange` | Public |
| `/api/v1/auth/me` | Any authenticated user |
| `/api/v1/users` | `ADMIN` |
| `/api/v1/memberships` | `ADMIN` |
| `/api/v1/table-types` | `ADMIN` |
| `/api/v1/pricing-rules` | `ADMIN` |
| `/api/v1/customers` | `ADMIN` or `STAFF` |
| `/api/v1/tables` | `ADMIN` or `STAFF` |
| `/api/v1/menu-items` | `ADMIN` or `STAFF` |
| `/api/v1/orders` | `ADMIN` or `STAFF` |
| `/api/v1/reservations` | `ADMIN` or `STAFF` |
| `/api/v1/sessions`, `/api/v1/invoices`, `/api/v1/reports` | `ADMIN` or `STAFF` |

Expected auth failures for protected endpoints:

- no token: `401 Unauthorized`
- wrong role: `403 Forbidden`

---

## Suggested Test Order

Use this order so later endpoints have valid IDs to reference:

1. Log in as `ADMIN`
2. Log in as `STAFF`
3. Register a new customer account
4. Log in as that customer
5. Create membership tier
6. Create table type
7. Create pricing rule
8. Create billiard table
9. Create menu item
10. Create customer profile linked to the customer user
11. Create reservation
12. Start session
13. Pause session
14. Resume session
15. Create order
16. End session
17. Generate invoice
18. Issue invoice
19. Pay invoice
20. Query reports

---

## Optional Postman Test Scripts

### Save access token after login or register

Add this in the `Tests` tab for auth requests:

```javascript
const body = pm.response.json();
pm.environment.set("last_access_token", body.accessToken);

if (body.user?.role === "ADMIN") {
  pm.environment.set("admin_access_token", body.accessToken);
}

if (body.user?.role === "STAFF") {
  pm.environment.set("staff_access_token", body.accessToken);
}

if (body.user?.role === "CUSTOMER") {
  pm.environment.set("customer_access_token", body.accessToken);
}
```

### Save IDs after create requests

```javascript
const body = pm.response.json();
if (body.id) {
  pm.environment.set("last_id", body.id);
}
```

You can adapt that per endpoint, for example `membership_id`, `table_id`, and so on.

---

## Common Response Patterns

### Validation failure

If the JSON body violates `@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Future`, and similar constraints, expect:

- `400 Bad Request`
- response body with validation error details from Spring Boot

### Business-rule failure

If the payload is syntactically valid but violates business rules, expect:

- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict`

The response body usually includes the reason string from the service layer.

---

# 1. Auth API

## 1.1 Register

### Request

```http
POST {{base_url}}/api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "customer1@example.com",
  "password": "Customer123",
  "fullName": "Customer One",
  "phone": "0901234567"
}
```

### Correct result

- `200 OK`
- body contains `accessToken` and `user`
- `user.role` is always `CUSTOMER`
- response sets a refresh-token cookie

Example:

```json
{
  "accessToken": "<jwt>",
  "user": {
    "id": "12",
    "email": "customer1@example.com",
    "fullName": "Customer One",
    "role": "CUSTOMER"
  }
}
```

### Failed result

- duplicate email: `400 Bad Request`, reason: `Registration could not be completed`
- weak password or invalid email: `400 Bad Request`

## 1.2 Login

### Request

```http
POST {{base_url}}/api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "{{admin_email}}",
  "password": "{{admin_password}}"
}
```

### Correct result

- `200 OK`
- body contains `accessToken` and `user`
- response sets refresh-token cookie

### Failed result

- wrong password: `401 Unauthorized`, reason: `Invalid email or password`
- disabled user: `403 Forbidden`, reason: `User is disabled`
- trying local login for a Google-only account: `401 Unauthorized`, reason: `Use the original sign-in method for this account`

## 1.3 Refresh

### Request

```http
POST {{base_url}}/api/v1/auth/refresh
X-Requested-With: XMLHttpRequest
```

No JSON body is required. Postman must already hold the refresh-token cookie from login/register.

### Correct result

- `200 OK`
- body contains a new `accessToken`
- response refreshes the cookie

### Failed result

- missing `X-Requested-With` header: `403 Forbidden`, reason: `Missing required header`
- missing/expired/invalid refresh cookie: `401 Unauthorized`

## 1.4 Logout

### Request

```http
POST {{base_url}}/api/v1/auth/logout
X-Requested-With: XMLHttpRequest
```

### Correct result

- `200 OK`
- response body:

```json
{
  "message": "Logged out"
}
```

- refresh-token cookie is cleared

### Failed result

- missing `X-Requested-With` header: `403 Forbidden`, reason: `Missing required header`

## 1.5 Forgot Password

### Request

```http
POST {{base_url}}/api/v1/auth/forgot-password
Content-Type: application/json
```

```json
{
  "email": "customer1@example.com"
}
```

### Correct result

- `202 Accepted`
- body:

```json
{
  "message": "If the account exists, a reset email has been sent"
}
```

### Failed result

- invalid email format: `400 Bad Request`
- mail server failure: `503 Service Unavailable`, reason: `Unable to send password reset email`

## 1.6 Reset Password

### Request

```http
POST {{base_url}}/api/v1/auth/reset-password
Content-Type: application/json
```

```json
{
  "token": "paste-reset-token-here",
  "password": "NewPassword123"
}
```

### Correct result

- `200 OK`

```json
{
  "message": "Password has been reset"
}
```

### Failed result

- invalid or expired token: `400 Bad Request`, reason: `Invalid or expired reset token`
- weak password: `400 Bad Request`

## 1.7 OAuth2 Exchange

### Request

```http
POST {{base_url}}/api/v1/auth/oauth2/exchange
Content-Type: application/json
```

```json
{
  "code": "one-time-oauth-exchange-code"
}
```

### Correct result

- `200 OK`
- body contains `accessToken` and `user`
- refresh-token cookie is set

### Failed result

- invalid code: `401 Unauthorized`
- account provider mismatch: `401 Unauthorized`, reason: `Use the original sign-in method for this account`

## 1.8 Me

### Request

```http
GET {{base_url}}/api/v1/auth/me
Authorization: Bearer {{customer_access_token}}
```

### Correct result

- `200 OK`
- body contains the authenticated user's profile

### Failed result

- missing token: `401 Unauthorized`
- invalid token: `401 Unauthorized`

---

# 2. User Management API

These endpoints require an `ADMIN` token.

Common failure cases:

- no token: `401 Unauthorized`
- staff/customer token: `403 Forbidden`

## 2.1 List Users

### Request

```http
GET {{base_url}}/api/v1/users?page=0&size=20&sortBy=createdAt&direction=DESC&q=admin
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`
- body shape:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Failed result

- unsupported `sortBy`: `400 Bad Request`, reason: `Unsupported sortBy value`
- invalid `direction`: `400 Bad Request`, reason: `direction must be ASC or DESC`

## 2.2 Get User

### Request

```http
GET {{base_url}}/api/v1/users/{{user_id}}
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`
- body contains `id`, `email`, `fullName`, `role`, `provider`, `active`

### Failed result

- unknown ID: `404 Not Found`, reason: `User not found`

## 2.3 Create User

### Request

```http
POST {{base_url}}/api/v1/users
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "email": "staff2@example.com",
  "fullName": "Staff Two",
  "phone": "0901111222",
  "role": "STAFF",
  "password": "Staff1234",
  "active": true
}
```

### Correct result

- `201 Created`
- body contains created user data

### Failed result

- duplicate email: `409 Conflict`, reason: `Email already exists`
- missing password on create: `400 Bad Request`, reason: `password is required for local users`

## 2.4 Update User

### Request

```http
PUT {{base_url}}/api/v1/users/{{user_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "email": "staff2@example.com",
  "fullName": "Staff Two Updated",
  "phone": "0903333444",
  "role": "STAFF",
  "password": "NewStaff123",
  "active": true
}
```

### Correct result

- `200 OK`
- body contains updated user data

### Failed result

- unknown ID: `404 Not Found`, reason: `User not found`
- duplicate email: `409 Conflict`, reason: `Email already exists`

## 2.5 Toggle User Active

### Request

```http
PATCH {{base_url}}/api/v1/users/{{user_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`
- response body shows `active: false`

### Failed result

- unknown ID: `404 Not Found`, reason: `User not found`
- missing `active`: `400 Bad Request`

---

# 3. Membership Tier API

These endpoints require an `ADMIN` token.

## 3.1 List Membership Tiers

```http
GET {{base_url}}/api/v1/memberships?page=0&size=20&sortBy=name&direction=ASC&q=silver
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`
- paged list of membership tiers

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 3.2 Get Membership Tier

```http
GET {{base_url}}/api/v1/memberships/{{membership_id}}
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Membership tier not found`

## 3.3 Create Membership Tier

```http
POST {{base_url}}/api/v1/memberships
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Silver",
  "discountPercent": 5.0,
  "minimumSpend": 100000.0,
  "description": "Entry membership tier",
  "active": true
}
```

### Correct result

- `201 Created`

### Failed result

- duplicate name: `409 Conflict`, reason: `Membership tier name already exists`
- `discountPercent` outside `0..100`: `400 Bad Request`

## 3.4 Update Membership Tier

```http
PUT {{base_url}}/api/v1/memberships/{{membership_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Silver Plus",
  "discountPercent": 7.5,
  "minimumSpend": 150000.0,
  "description": "Updated tier",
  "active": true
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`
- duplicate name: `409 Conflict`

## 3.5 Toggle Membership Tier Active

```http
PATCH {{base_url}}/api/v1/memberships/{{membership_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`

---

# 4. Table Type API

These endpoints require an `ADMIN` token.

## 4.1 List Table Types

```http
GET {{base_url}}/api/v1/table-types?page=0&size=20&sortBy=name&direction=ASC&q=pool
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 4.2 Get Table Type

```http
GET {{base_url}}/api/v1/table-types/{{table_type_id}}
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Table type not found`

## 4.3 Create Table Type

```http
POST {{base_url}}/api/v1/table-types
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Pool Standard",
  "description": "Regular pool table",
  "active": true
}
```

### Correct result

- `201 Created`

### Failed result

- duplicate name: `409 Conflict`, reason: `Table type name already exists`

## 4.4 Update Table Type

```http
PUT {{base_url}}/api/v1/table-types/{{table_type_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Pool Premium",
  "description": "Upgraded table type",
  "active": true
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`
- duplicate name: `409 Conflict`

## 4.5 Toggle Table Type Active

```http
PATCH {{base_url}}/api/v1/table-types/{{table_type_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`

---

# 5. Pricing Rule API

These endpoints require an `ADMIN` token.

## 5.1 List Pricing Rules

```http
GET {{base_url}}/api/v1/pricing-rules?page=0&size=20&sortBy=sortOrder&direction=ASC&q=pool
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 5.2 Get Pricing Rule

```http
GET {{base_url}}/api/v1/pricing-rules/{{pricing_rule_id}}
Authorization: Bearer {{admin_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Pricing rule not found`

## 5.3 Create Pricing Rule

```http
POST {{base_url}}/api/v1/pricing-rules
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "tableTypeId": {{table_type_id}},
  "blockMinutes": 30,
  "pricePerMinute": 2500.0,
  "sortOrder": 1,
  "active": true
}
```

### Correct result

- `201 Created`

### Failed result

- unknown table type: `404 Not Found`, reason: `Table type not found`
- `blockMinutes < 1`: `400 Bad Request`
- `pricePerMinute <= 0`: `400 Bad Request`

## 5.4 Update Pricing Rule

```http
PUT {{base_url}}/api/v1/pricing-rules/{{pricing_rule_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "tableTypeId": {{table_type_id}},
  "blockMinutes": 60,
  "pricePerMinute": 3000.0,
  "sortOrder": 2,
  "active": true
}
```

### Correct result

- `200 OK`

### Failed result

- unknown pricing rule: `404 Not Found`
- unknown table type: `404 Not Found`

## 5.5 Toggle Pricing Rule Active

```http
PATCH {{base_url}}/api/v1/pricing-rules/{{pricing_rule_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`

---

# 6. Table API

These endpoints require `ADMIN` or `STAFF`.

## 6.1 List Tables

```http
GET {{base_url}}/api/v1/tables?page=0&size=20&sortBy=name&direction=ASC&q=table
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- each item includes `status`, `floorPositionX`, `floorPositionY`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 6.2 Get Table

```http
GET {{base_url}}/api/v1/tables/{{table_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Table not found`

## 6.3 Create Table

```http
POST {{base_url}}/api/v1/tables
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Table A1",
  "tableTypeId": {{table_type_id}},
  "status": "AVAILABLE",
  "floorPositionX": 120,
  "floorPositionY": 240,
  "active": true
}
```

### Correct result

- `201 Created`

### Failed result

- duplicate name: `409 Conflict`, reason: `Table name already exists`
- unknown table type: `404 Not Found`, reason: `Table type not found`

## 6.4 Update Table

```http
PUT {{base_url}}/api/v1/tables/{{table_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Table A1 Updated",
  "tableTypeId": {{table_type_id}},
  "status": "AVAILABLE",
  "floorPositionX": 150,
  "floorPositionY": 250,
  "active": true
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`
- duplicate name: `409 Conflict`

## 6.5 Toggle Table Active

```http
PATCH {{base_url}}/api/v1/tables/{{table_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`

---

# 7. Menu Item API

These endpoints require `ADMIN` or `STAFF`.

## 7.1 List Menu Items

```http
GET {{base_url}}/api/v1/menu-items?page=0&size=20&sortBy=name&direction=ASC&q=coffee
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 7.2 Get Menu Item

```http
GET {{base_url}}/api/v1/menu-items/{{menu_item_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Menu item not found`

## 7.3 Create Menu Item

```http
POST {{base_url}}/api/v1/menu-items
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Iced Tea",
  "description": "Cold drink",
  "price": 25000.0,
  "imageUrl": "https://example.com/iced-tea.png",
  "active": true
}
```

### Correct result

- `201 Created`

### Failed result

- duplicate name: `409 Conflict`, reason: `Menu item name already exists`
- non-positive price: `400 Bad Request`

## 7.4 Update Menu Item

```http
PUT {{base_url}}/api/v1/menu-items/{{menu_item_id}}
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "name": "Iced Tea Large",
  "description": "Bigger cold drink",
  "price": 30000.0,
  "imageUrl": "https://example.com/iced-tea-large.png",
  "active": true
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`
- duplicate name: `409 Conflict`

## 7.5 Toggle Menu Item Active

```http
PATCH {{base_url}}/api/v1/menu-items/{{menu_item_id}}/active
Authorization: Bearer {{admin_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`

---

# 8. Customer Profile API

These endpoints require `ADMIN` or `STAFF`.

A customer profile is different from a user account.

You must first have a `User` whose role is `CUSTOMER`, then create the customer profile that links to that user.

## 8.1 List Customers

```http
GET {{base_url}}/api/v1/customers?page=0&size=20&sortBy=createdAt&direction=DESC&q=customer
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 8.2 Get Customer

```http
GET {{base_url}}/api/v1/customers/{{customer_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Customer not found`

## 8.3 Create Customer

### Request

Use a user whose role is `CUSTOMER`.

```http
POST {{base_url}}/api/v1/customers
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "userId": {{customer_user_id}},
  "membershipTierId": {{membership_id}},
  "notes": "Walk-in customer upgraded to member",
  "memberSince": "2026-03-30T12:00:00Z"
}
```

### Correct result

- `201 Created`

### Failed result

- `userId` not found: `404 Not Found`, reason: `User not found`
- selected user is not `CUSTOMER`: `400 Bad Request`, reason: `Only CUSTOMER users can be linked to customer profiles`
- profile already exists for that user: `409 Conflict`, reason: `Customer already exists for the selected user`
- membership tier not found: `404 Not Found`, reason: `Membership tier not found`

## 8.4 Update Customer

```http
PUT {{base_url}}/api/v1/customers/{{customer_id}}
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "userId": {{customer_user_id}},
  "membershipTierId": {{membership_id}},
  "notes": "Updated customer notes",
  "memberSince": "2026-03-30T12:00:00Z"
}
```

### Correct result

- `200 OK`

### Failed result

- unknown customer: `404 Not Found`
- invalid user role: `400 Bad Request`
- duplicate customer profile on another record: `409 Conflict`

## 8.5 Toggle Customer Active

```http
PATCH {{base_url}}/api/v1/customers/{{customer_id}}/active
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "active": false
}
```

### Correct result

- `200 OK`
- returned customer response shows `active: false`

### Failed result

- unknown customer: `404 Not Found`

---

# 9. Reservation API

These endpoints require `ADMIN` or `STAFF`.

Reservation status values:

```text
PENDING, CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW
```

## 9.1 List Reservations

```http
GET {{base_url}}/api/v1/reservations?page=0&size=20&sortBy=reservedFrom&direction=ASC&status=PENDING
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 9.2 Get Reservation

```http
GET {{base_url}}/api/v1/reservations/{{reservation_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Reservation not found`

## 9.3 Create Reservation

```http
POST {{base_url}}/api/v1/reservations
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "tableId": {{table_id}},
  "customerId": {{customer_id}},
  "reservedFrom": "2026-04-01T10:00:00Z",
  "reservedTo": "2026-04-01T12:00:00Z",
  "notes": "Birthday booking"
}
```

### Correct result

- `201 Created`
- returned status is `PENDING`

### Failed result

- table not found: `404 Not Found`, reason: `Table not found`
- inactive table: `400 Bad Request`, reason: `Inactive tables cannot be reserved`
- customer not found: `404 Not Found`, reason: `Customer not found`
- inactive customer: `400 Bad Request`, reason: `Inactive customers cannot hold reservations`
- `reservedFrom >= reservedTo`: `400 Bad Request`, reason: `reservedFrom must be earlier than reservedTo`
- overlapping reservation: `409 Conflict`, reason: `The selected table already has an overlapping reservation`
- past `reservedFrom`: `400 Bad Request` from validation

## 9.4 Update Reservation

```http
PUT {{base_url}}/api/v1/reservations/{{reservation_id}}
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "tableId": {{table_id}},
  "customerId": {{customer_id}},
  "status": "CONFIRMED",
  "reservedFrom": "2026-04-01T10:00:00Z",
  "reservedTo": "2026-04-01T12:00:00Z",
  "notes": "Confirmed by staff"
}
```

### Correct result

- `200 OK`

### Failed result

- unknown reservation: `404 Not Found`
- unsupported status transition: `409 Conflict`, reason: `Unsupported reservation status transition`
- overlapping reservation after update: `409 Conflict`
- invalid time range: `400 Bad Request`

---

# 10. Table Session API

These endpoints require `ADMIN` or `STAFF`.

## 10.1 Start Session

```http
POST {{base_url}}/api/v1/tables/{{table_id}}/start-session
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "customerId": {{customer_id}},
  "overrideReserved": false,
  "notes": "Started for afternoon play"
}
```

### Correct result

- `200 OK`
- response contains `status: ACTIVE`
- table status becomes `IN_USE`

### Failed result

- table not found: `404 Not Found`, reason: `Table not found`
- table already has active session: `409 Conflict`, reason: `The table already has an active session`
- table status not startable: `409 Conflict`, reason: `Table is not available for a new session`
- reserved table without override: `409 Conflict`, reason: `Reserved tables require overrideReserved=true`
- active reservation exists and no override: `409 Conflict`, reason: `Table has an active reservation and requires override`
- inactive customer: `400 Bad Request`, reason: `Inactive customers cannot start a session`

## 10.2 Pause Session

```http
POST {{base_url}}/api/v1/sessions/{{session_id}}/pause
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "reason": "Customer break"
}
```

### Correct result

- `200 OK`
- session status becomes `PAUSED`
- table status becomes `PAUSED`

### Failed result

- session not found: `404 Not Found`, reason: `Session not found`
- session not active: `409 Conflict`, reason: `Only active sessions can be paused`

## 10.3 Resume Session

```http
POST {{base_url}}/api/v1/sessions/{{session_id}}/resume
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- session status becomes `ACTIVE`
- table status becomes `IN_USE`

### Failed result

- session not found: `404 Not Found`
- session not paused: `409 Conflict`, reason: `Only paused sessions can be resumed`
- no open pause record: `409 Conflict`, reason: `Session does not have an open pause`

## 10.4 End Session

```http
POST {{base_url}}/api/v1/sessions/{{session_id}}/end
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- session status becomes `COMPLETED`
- table status becomes `AVAILABLE`

### Failed result

- session not found: `404 Not Found`
- session no longer active: `409 Conflict`, reason: `Session is no longer active`

## 10.5 Get Session

```http
GET {{base_url}}/api/v1/sessions/{{session_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Session not found`

## 10.6 Get Active Session For Table

```http
GET {{base_url}}/api/v1/tables/{{table_id}}/active-session
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- returns the latest session whose status is `ACTIVE` or `PAUSED`

### Failed result

- table not found: `404 Not Found`, reason: `Table not found`
- no active session: `404 Not Found`, reason: `Active session not found`

---

# 11. Order API

These endpoints require `ADMIN` or `STAFF`.

## 11.1 List Orders

```http
GET {{base_url}}/api/v1/orders?page=0&size=20&sortBy=orderedAt&direction=DESC&sessionId={{session_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- paged order list, each item includes `items[]`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 11.2 Get Order

```http
GET {{base_url}}/api/v1/orders/{{order_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Order not found`

## 11.3 Create Order

```http
POST {{base_url}}/api/v1/orders
Authorization: Bearer {{staff_access_token}}
Content-Type: application/json
```

```json
{
  "sessionId": {{session_id}},
  "items": [
    {
      "menuItemId": {{menu_item_id}},
      "quantity": 2
    }
  ],
  "notes": "Drinks for table"
}
```

### Correct result

- `201 Created`
- response includes order total and item subtotals

### Failed result

- empty items: `400 Bad Request`, reason: `At least one order item is required`
- duplicate menu item in one order: `400 Bad Request`, reason: `Duplicate menu items are not allowed in one order`
- missing menu item: `404 Not Found`, reason: `One or more menu items were not found`
- inactive menu item: `400 Bad Request`, reason: `Inactive menu items cannot be ordered`
- session not found: `404 Not Found`, reason: `Session not found`
- completed session: `409 Conflict`, reason: `Orders can only be created for active sessions`

---

# 12. Invoice API

These endpoints require `ADMIN` or `STAFF`.

## 12.1 Generate Invoice For Session

This only works after the session has been ended.

```http
POST {{base_url}}/api/v1/sessions/{{session_id}}/invoice
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `201 Created`
- response status is usually `DRAFT`
- total is computed from table pricing, pause windows, discounts, and orders

### Failed result

- session not found: `404 Not Found`, reason: `Session not found`
- session not completed: `409 Conflict`, reason: `Invoices can only be generated for completed sessions`
- active pricing rules missing: `409 Conflict`, reason: `No active pricing rules are configured for this table type`
- invoice already issued/paid/void and regeneration attempted: `409 Conflict`, reason: `Only draft invoices can be regenerated`

## 12.2 List Invoices

```http
GET {{base_url}}/api/v1/invoices?page=0&size=20&sortBy=createdAt&direction=DESC&sessionId={{session_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unsupported `sortBy`: `400 Bad Request`

## 12.3 Get Invoice

```http
GET {{base_url}}/api/v1/invoices/{{invoice_id}}
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`

### Failed result

- unknown ID: `404 Not Found`, reason: `Invoice not found`

## 12.4 Issue Invoice

```http
POST {{base_url}}/api/v1/invoices/{{invoice_id}}/issue
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- status becomes `ISSUED`
- `issuedAt` and `issuedByName` are populated

### Failed result

- invoice not found: `404 Not Found`
- invoice not in draft state: `409 Conflict`, reason: `Only draft invoices can be issued`

## 12.5 Pay Invoice

```http
POST {{base_url}}/api/v1/invoices/{{invoice_id}}/pay
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- status becomes `PAID`
- `paidAt` is populated

### Failed result

- invoice not found: `404 Not Found`
- invoice not issued: `409 Conflict`, reason: `Only issued invoices can be paid`

## 12.6 Void Invoice

```http
POST {{base_url}}/api/v1/invoices/{{invoice_id}}/void
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- status becomes `VOID`

### Failed result

- invoice not found: `404 Not Found`
- invoice already paid or already void: `409 Conflict`, reason: `Only draft or issued invoices can be voided`

---

# 13. Report API

These endpoints require `ADMIN` or `STAFF`.

Valid `groupBy` values:

```text
DAY, WEEK, MONTH, YEAR
```

## 13.1 Revenue Report

```http
GET {{base_url}}/api/v1/reports/revenue?from=2026-03-01&to=2026-03-31&groupBy=DAY
Authorization: Bearer {{staff_access_token}}
```

### Correct result

- `200 OK`
- body contains:
  - `from`
  - `to`
  - `groupBy`
  - `invoiceCount`
  - `totalAmount`
  - `buckets[]`

Example:

```json
{
  "from": "2026-03-01",
  "to": "2026-03-31",
  "groupBy": "DAY",
  "invoiceCount": 3,
  "totalAmount": 750000.0,
  "buckets": [
    {
      "label": "2026-03-01",
      "bucketStart": "2026-03-01",
      "bucketEnd": "2026-03-01",
      "invoiceCount": 1,
      "totalAmount": 250000.0
    }
  ]
}
```

### Failed result

- missing `from` or `to`: `400 Bad Request`, reason: `from and to are required`
- `from > to`: `400 Bad Request`, reason: `from must be on or before to`
- invalid `groupBy`: `400 Bad Request`

---

## Quick Smoke Checklist

If you want the fastest end-to-end sanity test, run these in order:

1. `POST /api/v1/auth/login` as admin
2. `POST /api/v1/auth/login` as staff
3. `POST /api/v1/auth/register` for a new customer
4. `POST /api/v1/memberships`
5. `POST /api/v1/table-types`
6. `POST /api/v1/pricing-rules`
7. `POST /api/v1/tables`
8. `POST /api/v1/menu-items`
9. `POST /api/v1/customers`
10. `POST /api/v1/reservations`
11. `POST /api/v1/tables/{tableId}/start-session`
12. `POST /api/v1/orders`
13. `POST /api/v1/sessions/{sessionId}/end`
14. `POST /api/v1/sessions/{sessionId}/invoice`
15. `POST /api/v1/invoices/{invoiceId}/issue`
16. `POST /api/v1/invoices/{invoiceId}/pay`
17. `GET /api/v1/reports/revenue`

---

## Notes

- Postman is enough for all REST endpoints in this guide.
- WebSocket floor updates are not regular REST endpoints and need a websocket client, not standard Postman REST requests.
- The Google OAuth login start flow is browser-driven. Only the exchange endpoint can be tested directly in Postman.
- If you test protected endpoints with a customer token, expect `403 Forbidden`.
- If you test refresh/logout without the `X-Requested-With: XMLHttpRequest` header, expect `403 Forbidden`.

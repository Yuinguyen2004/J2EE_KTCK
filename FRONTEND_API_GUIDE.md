# Huong Dan Frontend Goi API Server

> Tai lieu nay danh cho cac ban sinh vien dang lam phan frontend. Moi thu da duoc setup san, ban chi can hieu cach su dung.

---

## Muc Luc

1. [Setup co ban - da co san](#1-setup-co-ban---da-co-san)
2. [Cach goi API](#2-cach-goi-api)
3. [Xac thuc (Auth)](#3-xac-thuc-auth)
4. [CRUD co ban](#4-crud-co-ban)
5. [Danh sach tat ca API](#5-danh-sach-tat-ca-api)
6. [Cau truc du lieu tra ve](#6-cau-truc-du-lieu-tra-ve)
7. [Xu ly loi](#7-xu-ly-loi)
8. [Cac kieu Enum](#8-cac-kieu-enum)

---

## 1. Setup co ban - da co san

Project da duoc cau hinh san cac thu sau, **ban KHONG can cai them gi**:

| Thu vien | Vai tro | File |
|----------|---------|------|
| `axios` | Goi HTTP request | `shared/api/client.ts` |
| `@tanstack/react-query` | Cache & quan ly state API | `shared/providers/QueryProvider.tsx` |
| `AuthProvider` | Quan ly dang nhap/dang xuat | `shared/providers/AuthProvider.tsx` |

**`apiClient`** la axios instance da duoc cau hinh san:
- Tu dong gui access token trong header `Authorization: Bearer <token>`
- Tu dong refresh token khi bi 401 (het han)
- Tu dong gui cookie (`withCredentials: true`)
- Tu dong gui header `X-Requested-With: XMLHttpRequest` (bat buoc cho refresh/logout)

**Ban chi can import `apiClient` va goi thoi, khong can config gi them.**

```ts
import { apiClient } from "@/shared/api/client";
```

---

## 2. Cach goi API

### 2.1. Goi don gian nhat (khong can TanStack Query)

```ts
import { apiClient } from "@/shared/api/client";

// GET
const response = await apiClient.get("/tables");
console.log(response.data); // { items: [...], page: 0, size: 20, totalElements: 5, totalPages: 1 }

// POST
const response = await apiClient.post("/tables", {
  name: "Ban so 1",
  tableTypeId: 1,
  floorPositionX: 100,
  floorPositionY: 200,
});

// PUT
await apiClient.put("/tables/1", { name: "Ban so 1 (cap nhat)", tableTypeId: 1 });

// DELETE
await apiClient.delete("/customer/reservations/1");

// PATCH
await apiClient.patch("/tables/1/active", { active: false });
```

### 2.2. Goi voi TanStack Query (khuyen dung cho component React)

```tsx
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/shared/api/client";

// --- Lay danh sach (GET) ---
function TableList() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["tables"],
    queryFn: () => apiClient.get("/tables").then((res) => res.data),
  });

  if (isLoading) return <p>Dang tai...</p>;
  if (error) return <p>Loi roi!</p>;

  return (
    <ul>
      {data.items.map((table) => (
        <li key={table.id}>{table.name}</li>
      ))}
    </ul>
  );
}

// --- Tao moi (POST) ---
function CreateTableButton() {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (newTable) =>
      apiClient.post("/tables", newTable).then((res) => res.data),
    onSuccess: () => {
      // Tai lai danh sach sau khi tao thanh cong
      queryClient.invalidateQueries({ queryKey: ["tables"] });
    },
  });

  return (
    <button onClick={() => mutation.mutate({ name: "Ban moi", tableTypeId: 1 })}>
      Tao ban
    </button>
  );
}
```

### 2.3. Su dung ham CRUD co san

Project da co san cac ham CRUD trong `shared/api/crud.ts`:

```ts
import {
  listResource,
  getResource,
  createResource,
  updateResource,
  toggleResourceActive,
  getApiErrorMessage,
} from "@/shared/api/crud";

// Lay danh sach co phan trang + tim kiem
const result = await listResource("/tables", {
  q: "ban so",      // tim kiem (khong bat buoc)
  page: 0,          // trang (mac dinh 0)
  size: 20,         // so item/trang (mac dinh 20)
  sortBy: "name",   // sap xep theo truong nao
  direction: "asc", // asc hoac desc
});

// Lay 1 item theo id
const table = await getResource("/tables", 1);

// Tao moi
const newTable = await createResource("/tables", {
  name: "Ban so 5",
  tableTypeId: 2,
});

// Cap nhat
const updated = await updateResource("/tables", 1, {
  name: "Ban so 5 (sua)",
  tableTypeId: 2,
});

// Bat/tat active
await toggleResourceActive("/tables", 1, false);
```

---

## 3. Xac thuc (Auth)

### 3.1. Dang nhap / Dang ky / Dang xuat

Da co san trong `AuthProvider`. Trong component, dung hook `useAuth()`:

```tsx
import { useAuth } from "@/shared/providers/useAuth";

function MyComponent() {
  const { user, isAuthenticated, signIn, signUp, signOut, hydrating } = useAuth();

  // Kiem tra dang tai session
  if (hydrating) return <p>Dang kiem tra phien dang nhap...</p>;

  // Kiem tra da dang nhap chua
  if (!isAuthenticated) return <p>Chua dang nhap</p>;

  // Thong tin user
  console.log(user.email);    // "abc@gmail.com"
  console.log(user.fullName); // "Nguyen Van A"
  console.log(user.role);     // "ADMIN" | "STAFF" | "CUSTOMER"

  // Dang nhap
  await signIn({ email: "abc@gmail.com", password: "123456" });

  // Dang ky
  await signUp({
    email: "abc@gmail.com",
    password: "123456",
    fullName: "Nguyen Van A",
    phone: "0901234567", // khong bat buoc
  });

  // Dang xuat
  await signOut();
}
```

### 3.2. Google OAuth2

```ts
import { getGoogleAuthorizationUrl } from "@/features/auth/api/auth.api";

// Chuyen huong den trang dang nhap Google
window.location.href = getGoogleAuthorizationUrl();
// Sau khi Google redirect ve, frontend tu dong doi code -> token
```

### 3.3. Cac ham auth API co san

File `features/auth/api/auth.api.ts` da export san:

```ts
import {
  login,              // Dang nhap -> tra ve { accessToken, user }
  register,           // Dang ky -> tra ve { accessToken, user }
  forgotPassword,     // Quen mat khau -> gui email
  resetPassword,      // Dat lai mat khau
  refreshSession,     // Lam moi token (tu dong boi interceptor)
  getCurrentUser,     // Lay thong tin user hien tai
  exchangeOAuthCode,  // Doi Google OAuth code -> token
  logout,             // Dang xuat
  getGoogleAuthorizationUrl, // Lay URL dang nhap Google
} from "@/features/auth/api/auth.api";
```

---

## 4. CRUD co ban

Hau het cac module admin/staff deu co cung 1 pattern. Vi du voi **Ban bi-a (tables)**:

| Thao tac | Method | URL | Body |
|----------|--------|-----|------|
| Danh sach | `GET` | `/tables` | - |
| Chi tiet | `GET` | `/tables/{id}` | - |
| Tao moi | `POST` | `/tables` | `{ name, tableTypeId, ... }` |
| Cap nhat | `PUT` | `/tables/{id}` | `{ name, tableTypeId, ... }` |
| Bat/tat | `PATCH` | `/tables/{id}/active` | `{ active: true/false }` |

**Query params chung cho GET danh sach:**

| Param | Mo ta | Vi du |
|-------|-------|-------|
| `q` | Tim kiem (khong bat buoc) | `?q=ban so 1` |
| `page` | So trang (bat dau tu 0) | `?page=0` |
| `size` | So item/trang | `?size=20` |
| `sortBy` | Sap xep theo truong | `?sortBy=name` |
| `direction` | Thu tu | `?direction=asc` |

**Tat ca danh sach deu tra ve cung format:**

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

## 5. Danh sach tat ca API

### 5.1. Auth (khong can dang nhap cho login/register)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `POST` | `/auth/register` | `{ email, password, fullName, phone? }` | Dang ky |
| `POST` | `/auth/login` | `{ email, password }` | Dang nhap |
| `POST` | `/auth/refresh` | - | Lam moi token |
| `POST` | `/auth/logout` | - | Dang xuat |
| `GET` | `/auth/me` | - | Thong tin user hien tai |
| `POST` | `/auth/forgot-password` | `{ email }` | Quen mat khau |
| `POST` | `/auth/reset-password` | `{ token, password }` | Dat lai mat khau |
| `POST` | `/auth/oauth2/exchange` | `{ code }` | Doi Google OAuth code |

> Tra ve `{ accessToken, user: { id, email, fullName, role } }`

---

### 5.2. Customer APIs (yeu cau role CUSTOMER)

#### Menu (chi doc)

| Method | URL | Mo ta |
|--------|-----|-------|
| `GET` | `/customer/menu-items` | Xem menu (co phan trang) |
| `GET` | `/customer/menu-items/{id}` | Chi tiet mon |

#### Dat cho (Reservations)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/customer/reservations` | - | Danh sach dat cho cua toi |
| `GET` | `/customer/reservations/{id}` | - | Chi tiet dat cho |
| `POST` | `/customer/reservations` | `{ reservedFrom, reservedTo, partySize?, notes? }` | Tao yeu cau dat cho |
| `PUT` | `/customer/reservations/{id}` | `{ reservedFrom, reservedTo, partySize?, notes? }` | Sua (chi khi PENDING) |
| `DELETE` | `/customer/reservations/{id}` | - | Huy (chi khi PENDING) |

> Query param bo sung cho GET danh sach: `status` (loc theo trang thai)
>
> `reservedFrom` va `reservedTo` la ISO datetime, vi du: `"2026-04-01T14:00:00Z"`
>
> Customer KHONG chon ban, chi chon thoi gian. Staff se xac nhan va gan ban sau.

#### Order (Don hang - lien ket voi session)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/customer/orders` | - | Danh sach don cua toi |
| `GET` | `/customer/orders/{id}` | - | Chi tiet don |
| `POST` | `/customer/orders` | `{ sessionId, items: [{ menuItemId, quantity }], notes? }` | Tao don nhap |
| `PUT` | `/customer/orders/{id}` | `{ items: [{ menuItemId, quantity }], notes? }` | Sua don (chi khi PENDING) |
| `DELETE` | `/customer/orders/{id}` | - | Xoa don (chi khi PENDING) |

> Query param bo sung: `sessionId` (loc theo phien choi)
>
> Customer chi tao duoc don khi co session dang active/paused.

---

### 5.3. Staff/Admin APIs (yeu cau role ADMIN hoac STAFF)

#### Ban bi-a (Tables)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/tables` | - | Danh sach |
| `GET` | `/tables/{id}` | - | Chi tiet |
| `POST` | `/tables` | `{ name, tableTypeId, status?, floorPositionX?, floorPositionY?, active? }` | Tao |
| `PUT` | `/tables/{id}` | giong POST | Cap nhat |
| `PATCH` | `/tables/{id}/active` | `{ active }` | Bat/tat |

#### Loai ban (Table Types)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/table-types` | - | Danh sach |
| `GET` | `/table-types/{id}` | - | Chi tiet |
| `POST` | `/table-types` | `{ name, description?, active? }` | Tao |
| `PUT` | `/table-types/{id}` | giong POST | Cap nhat |
| `PATCH` | `/table-types/{id}/active` | `{ active }` | Bat/tat |

#### Bang gia (Pricing Rules)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/pricing-rules` | - | Danh sach |
| `GET` | `/pricing-rules/{id}` | - | Chi tiet |
| `POST` | `/pricing-rules` | `{ tableTypeId, blockMinutes, pricePerMinute, sortOrder?, active? }` | Tao |
| `PUT` | `/pricing-rules/{id}` | giong POST | Cap nhat |
| `PATCH` | `/pricing-rules/{id}/active` | `{ active }` | Bat/tat |

#### Khach hang (Customers)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/customers` | - | Danh sach |
| `GET` | `/customers/{id}` | - | Chi tiet |
| `POST` | `/customers` | `{ userId, membershipTierId?, notes?, memberSince? }` | Tao |
| `PUT` | `/customers/{id}` | giong POST | Cap nhat |
| `PATCH` | `/customers/{id}/active` | `{ active }` | Bat/tat |

#### Nguoi dung (Users) - chi ADMIN

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/users` | - | Danh sach |
| `GET` | `/users/{id}` | - | Chi tiet |
| `POST` | `/users` | `{ email, fullName, phone?, role, password, active? }` | Tao |
| `PUT` | `/users/{id}` | giong POST | Cap nhat |
| `PATCH` | `/users/{id}/active` | `{ active }` | Bat/tat |

#### Menu (Menu Items)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/menu-items` | - | Danh sach |
| `GET` | `/menu-items/{id}` | - | Chi tiet |
| `POST` | `/menu-items` | `{ name, description?, price, imageUrl?, active? }` | Tao |
| `PUT` | `/menu-items/{id}` | giong POST | Cap nhat |
| `PATCH` | `/menu-items/{id}/active` | `{ active }` | Bat/tat |

#### Membership Tiers

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/membership-tiers` | - | Danh sach |
| `GET` | `/membership-tiers/{id}` | - | Chi tiet |
| `POST` | `/membership-tiers` | `{ name, discountPercent, minimumSpend?, description?, active? }` | Tao |
| `PUT` | `/membership-tiers/{id}` | giong POST | Cap nhat |
| `PATCH` | `/membership-tiers/{id}/active` | `{ active }` | Bat/tat |

#### Dat cho - Staff view (Reservations)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/reservations` | - | Danh sach (loc: `tableId`, `customerId`, `status`) |
| `GET` | `/reservations/{id}` | - | Chi tiet |
| `POST` | `/reservations` | `{ tableId, customerId, reservedFrom, reservedTo, partySize?, notes? }` | Tao |
| `PUT` | `/reservations/{id}` | `{ tableId?, customerId?, status?, reservedFrom, reservedTo, partySize?, notes? }` | Cap nhat |
| `POST` | `/reservations/{id}/confirm` | `{ tableId, notes? }` | Xac nhan + gan ban |

#### Don hang - Staff view (Orders)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/orders` | - | Danh sach (loc: `sessionId`) |
| `GET` | `/orders/{id}` | - | Chi tiet |
| `POST` | `/orders` | `{ sessionId, items: [{ menuItemId, quantity }], notes? }` | Tao |
| `POST` | `/orders/{id}/confirm` | - | Xac nhan don |

#### Phien choi (Table Sessions)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `POST` | `/tables/{tableId}/start-session` | `{ customerId?, overrideReserved?, notes? }` | Mo ban |
| `POST` | `/sessions/{sessionId}/pause` | `{ reason? }` | Tam dung |
| `POST` | `/sessions/{sessionId}/resume` | - | Tiep tuc |
| `POST` | `/sessions/{sessionId}/end` | - | Ket thuc (tu dong tao hoa don) |
| `GET` | `/sessions/{sessionId}` | - | Chi tiet phien |
| `GET` | `/tables/{tableId}/active-session` | - | Phien dang active cua ban |

> Khi end session, server tra ve `{ session: {...}, invoice: {...} }` -- bao gom hoa don tu dong.

#### Hoa don (Invoices)

| Method | URL | Body | Mo ta |
|--------|-----|------|-------|
| `GET` | `/invoices` | - | Danh sach (loc: `sessionId`, `status`) |
| `GET` | `/invoices/{invoiceId}` | - | Chi tiet |
| `POST` | `/sessions/{sessionId}/invoice` | - | Tao hoa don (thuong khong can vi end-session tu tao) |
| `POST` | `/invoices/{invoiceId}/issue` | - | Phat hanh hoa don |
| `POST` | `/invoices/{invoiceId}/pay` | - | Thanh toan |
| `POST` | `/invoices/{invoiceId}/void` | - | Huy hoa don |

#### Bao cao (Reports)

| Method | URL | Mo ta |
|--------|-----|-------|
| `GET` | `/reports/revenue?from=2026-01-01&to=2026-03-31&groupBy=MONTH` | Doanh thu theo thoi gian |

> `groupBy`: `DAY`, `WEEK`, `MONTH`, `YEAR`

---

## 6. Cau truc du lieu tra ve

### Auth Response

```json
{
  "accessToken": "eyJhbG...",
  "user": {
    "id": "1",
    "email": "admin@test.com",
    "fullName": "Admin",
    "role": "ADMIN"
  }
}
```

### Table Response

```json
{
  "id": 1,
  "name": "Ban so 1",
  "tableTypeId": 1,
  "tableTypeName": "Ban Phang",
  "status": "AVAILABLE",
  "floorPositionX": 100,
  "floorPositionY": 200,
  "active": true,
  "createdAt": "2026-03-30T10:00:00Z",
  "updatedAt": "2026-03-30T10:00:00Z"
}
```

### Reservation Response

```json
{
  "id": 1,
  "tableId": 2,
  "tableName": "Ban so 2",
  "tableStatus": "RESERVED",
  "customerId": 1,
  "customerName": "Nguyen Van A",
  "staffId": 3,
  "staffName": "Nhan vien B",
  "status": "PENDING",
  "reservedFrom": "2026-04-01T14:00:00Z",
  "reservedTo": "2026-04-01T16:00:00Z",
  "partySize": 4,
  "checkedInAt": null,
  "notes": "Ghi chu",
  "createdAt": "2026-03-30T10:00:00Z",
  "updatedAt": "2026-03-30T10:00:00Z"
}
```

### Order Response

```json
{
  "id": 1,
  "sessionId": 5,
  "sessionStatus": "ACTIVE",
  "tableId": 2,
  "tableName": "Ban so 2",
  "customerId": 1,
  "customerName": "Nguyen Van A",
  "staffId": null,
  "staffName": null,
  "status": "PENDING",
  "totalAmount": 150000,
  "orderedAt": "2026-03-30T14:30:00Z",
  "notes": null,
  "items": [
    {
      "id": 1,
      "menuItemId": 3,
      "menuItemName": "Nuoc ngot",
      "quantity": 2,
      "unitPrice": 25000,
      "subtotal": 50000
    }
  ],
  "createdAt": "2026-03-30T14:30:00Z",
  "updatedAt": "2026-03-30T14:30:00Z"
}
```

### Invoice Response

```json
{
  "id": 1,
  "sessionId": 5,
  "tableId": 2,
  "tableName": "Ban so 2",
  "customerId": 1,
  "customerName": "Nguyen Van A",
  "issuedById": 3,
  "issuedByName": "Nhan vien B",
  "status": "DRAFT",
  "tableAmount": 200000,
  "orderAmount": 150000,
  "discountAmount": 35000,
  "totalAmount": 315000,
  "issuedAt": null,
  "paidAt": null,
  "notes": null,
  "createdAt": "2026-03-30T16:00:00Z",
  "updatedAt": "2026-03-30T16:00:00Z"
}
```

### Session Response

```json
{
  "id": 5,
  "tableId": 2,
  "tableName": "Ban so 2",
  "tableStatus": "IN_USE",
  "customerId": 1,
  "customerName": "Nguyen Van A",
  "staffId": 3,
  "staffName": "Nhan vien B",
  "status": "ACTIVE",
  "startedAt": "2026-03-30T14:00:00Z",
  "endedAt": null,
  "elapsedSeconds": 7200,
  "billableSeconds": 6600,
  "totalPausedSeconds": 600,
  "totalAmount": 200000,
  "notes": null,
  "pauses": [
    {
      "id": 1,
      "startedAt": "2026-03-30T15:00:00Z",
      "endedAt": "2026-03-30T15:10:00Z",
      "reason": "Di ve sinh",
      "durationSeconds": 600
    }
  ]
}
```

### End Session Response

```json
{
  "session": { "...cau truc TableSession o tren..." },
  "invoice": { "...cau truc Invoice o tren..." }
}
```

### Revenue Report Response

```json
{
  "from": "2026-01-01",
  "to": "2026-03-31",
  "groupBy": "MONTH",
  "invoiceCount": 45,
  "totalAmount": 15000000,
  "buckets": [
    {
      "label": "2026-01",
      "bucketStart": "2026-01-01",
      "bucketEnd": "2026-01-31",
      "invoiceCount": 12,
      "totalAmount": 4500000
    }
  ]
}
```

---

## 7. Xu ly loi

### Dung ham co san

```ts
import { getApiErrorMessage } from "@/shared/api/crud";

try {
  await apiClient.post("/tables", { name: "" });
} catch (error) {
  const message = getApiErrorMessage(error, "Khong the tao ban");
  alert(message); // Hien thi loi tu server hoac fallback message
}
```

### Cac HTTP status code thuong gap

| Code | Y nghia | Khi nao |
|------|---------|---------|
| `200` | Thanh cong | GET, PUT, PATCH |
| `201` | Tao thanh cong | POST |
| `204` | Thanh cong (khong co body) | DELETE |
| `400` | Du lieu khong hop le | Thieu truong bat buoc, sai format |
| `401` | Chua dang nhap / token het han | apiClient tu dong refresh |
| `403` | Khong co quyen | Vi du CUSTOMER goi API cua ADMIN |
| `404` | Khong tim thay | ID khong ton tai |
| `409` | Xung dot | Dat cho bi trung gio |
| `429` | Qua nhieu request | Rate limit (20 req/60s cho auth) |

---

## 8. Cac kieu Enum

Server su dung cac enum sau (gui len va nhan ve deu la **string IN HOA**):

### UserRole
`"ADMIN"` | `"STAFF"` | `"CUSTOMER"`

### TableStatus
`"AVAILABLE"` | `"RESERVED"` | `"IN_USE"` | `"PAUSED"` | `"MAINTENANCE"`

### ReservationStatus
`"PENDING"` | `"CONFIRMED"` | `"CHECKED_IN"` | `"CANCELLED"` | `"NO_SHOW"`

### SessionStatus
`"ACTIVE"` | `"PAUSED"` | `"COMPLETED"` | `"CANCELLED"`

### OrderStatus
`"PENDING"` | `"CONFIRMED"`

### InvoiceStatus
`"DRAFT"` | `"ISSUED"` | `"PAID"` | `"VOID"`

### RevenueGroupBy (cho report)
`"DAY"` | `"WEEK"` | `"MONTH"` | `"YEAR"`

---

## Luu y quan trong

1. **Tat ca URL deu bat dau bang `/api/v1/`** -- nhung khi dung `apiClient` thi da co `baseURL` roi, chi can viet phan sau. Vi du: `/tables` thay vi `/api/v1/tables`.

2. **Thoi gian (datetime)** gui len va nhan ve deu la **ISO 8601 UTC string**: `"2026-04-01T14:00:00Z"`

3. **So tien (money)** la kieu `number` (BigDecimal o server). Vi du: `150000` (khong phai `"150,000"`).

4. **Token tu dong refresh** -- ban khong can xu ly 401 manually. `apiClient` tu dong goi `/auth/refresh` va retry request.

5. **Phan quyen:**
   - API `/customer/*` chi cho role `CUSTOMER`
   - API `/users` chi cho role `ADMIN`
   - Cac API con lai cho `ADMIN` va `STAFF`
   - Auth APIs (login, register, ...) khong can dang nhap

6. **Customer tu phuc vu:** Customer chi thao tac duoc voi du lieu cua chinh minh (reservation, order cua minh), khong thay duoc cua nguoi khac.

# J2EE_KTCK

## Tester Handoff

This branch contains the current integrated UI and backend work for the billiard club management system. Testing should be done through the real browser UI with the running backend, not only through API tools.

## Roles To Test

- `ADMIN`
- `STAFF`
- `CUSTOMER`

Use real accounts for each role where possible so role guards, menu visibility, and action permissions are exercised end to end.

## Main Features Delivered

- Customer portal with customer dashboard, table availability, reservation request flow, menu browsing, chat, and profile support
- Staff/admin reservation approval screen where pending customer reservations can be assigned to a real table and confirmed
- Staff table-service visibility fix so staff can see tables and pricing-backed live operations again
- Table session customer attachment flow so staff can start a session for a walk-in or attach a customer/member
- Membership discount flow on sessions where the discount is applied to the full session subtotal, including table time and F&B
- Staff chat inbox for customer conversations
- CRM management for customers and membership tiers
- Customer reservation schema repair and reservation approval backend stability fixes

## Real UI Test Checklist

### 1. Authentication And Navigation

- Login with `ADMIN`, `STAFF`, and `CUSTOMER` accounts
- Confirm each role only sees the pages/actions it should be allowed to access
- Confirm register/login screens still work and the app restores the correct session after refresh when expected

### 2. Customer Portal

- As a customer, open the customer dashboard and confirm available tables load correctly
- Create a reservation request without selecting a table directly
- Review existing customer reservations and confirm statuses update after staff/admin action
- Open customer menu browsing and customer chat pages and confirm they load and submit correctly

### 3. Staff/Admin Reservation Approval

- As staff/admin, open the reservation management page
- Confirm pending reservation requests are visible
- Approve a pending reservation by assigning a table
- Confirm invalid approval states are blocked in the UI
- Confirm approved reservations no longer appear as pending and remain visible in other status filters

### 4. Table Service And Live Sessions

- As staff/admin, open dashboard and table-service screens and confirm tables render correctly
- Start a walk-in session without attaching a customer
- Start another session with an attached customer who has no membership tier
- Start another session with an attached customer who has an active membership tier
- While a member session is active, add F&B items and confirm the modal shows:
  - the attached customer
  - the membership tier
  - an estimated membership discount on the full subtotal
- Complete checkout for a member session and confirm the final receipt discounts the combined table plus F&B subtotal, not table time only

### 5. CRM And Memberships

- Open the CRM screen as admin/staff
- Confirm customers load correctly with membership information
- Create or update a membership tier
- Attach a membership tier to a customer
- Start a new session for that customer and confirm the session discount behavior matches the assigned tier

### 6. Chat Flow

- Send a customer message from the customer-facing chat
- Open the staff chat inbox and confirm the message appears
- Reply as staff and confirm the customer can see the response

## Regression Focus

- Staff must be able to read pricing-backed table data without gaining admin write access
- Customer reservation creation must still work when the request has no assigned table yet
- Reservation approval must not fail with server errors when staff/admin confirms a request
- Session checkout must still succeed when pricing rules are configured
- Revenue/reporting paths should reflect completed paid checkouts

## Preconditions For Testing

- At least one active table type and pricing rule must exist
- At least one active customer with a membership tier should exist for the membership-discount scenarios
- At least one active customer without a membership tier should exist for comparison
- Backend and frontend must both be running against the same environment and database

## Known Note

- Frontend production build still reports the existing large bundle-size warning from Vite, but the build completes successfully

## Deployment Note

- For a brand-new VPS PostgreSQL database, apply `deployment/postgres/001_schema.sql` before starting the production app, then use the first-admin bootstrap env vars documented in `deployment/postgres/README.md` for the first boot only
- The bundled Docker/nginx stack now serves plain HTTP on port `80` only. It does not require `nginx/ssl/cert.pem` or `nginx/ssl/key.pem`
- For this HTTP-only deploy path, set `FRONTEND_BASE_URL=http://your-domain.example` and `JWT_REFRESH_COOKIE_SECURE=false`
- If you later terminate HTTPS in a host-level reverse proxy, switch `FRONTEND_BASE_URL` back to `https://...` and set `JWT_REFRESH_COOKIE_SECURE=true`
- Google OAuth should be treated as unavailable on an HTTP-only VPS deploy, because production OAuth redirect URIs generally require HTTPS
- For Railway deployment, use the split-service guide in [deployment/railway/README.md](/home/nguyenthanhhuy/Documents/J2EE_KTCK/deployment/railway/README.md)

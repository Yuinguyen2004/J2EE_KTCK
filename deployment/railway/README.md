# Railway Deploy

This is the safest deploy path for this repository when you need a public demo quickly.

## Architecture

- `web` service: build from `client/Dockerfile`
- `server` service: build from root `Dockerfile`
- `postgres` service: Railway PostgreSQL template

The frontend keeps using relative API paths such as `/api/v1`, so the `web` service must proxy requests to the private backend service.

## 1. Create the Railway project

Create one Railway project with three services:

- `web`
- `server`
- `postgres`

Only `web` should have a public domain.
Keep `server` private unless you explicitly need direct API access.

## 2. Create the PostgreSQL service

Add Railway's PostgreSQL template to the project.

Then import the schema from [deployment/postgres/001_schema.sql](/home/nguyenthanhhuy/Documents/J2EE_KTCK/deployment/postgres/001_schema.sql).

You can do that with:

- Railway Database View SQL editor
- `psql` against the database's public connection

## 3. Configure the backend service

Deploy the `server` service from the repository root so Railway uses the root `Dockerfile`.

Set these variables on `server`:

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DATABASE_USERNAME=${{Postgres.PGUSER}}
DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<strong-random-secret>
JWT_ISSUER=billiard-shop
FRONTEND_BASE_URL=https://<your-public-web-domain>
JWT_REFRESH_COOKIE_SECURE=true
MAIL_HOST=localhost
MAIL_PORT=1025
APP_BOOTSTRAP_ADMIN_EMAIL=admin@your-domain.example
APP_BOOTSTRAP_ADMIN_PASSWORD=<strong-admin-password>
APP_BOOTSTRAP_ADMIN_FULL_NAME=System Admin
APP_BOOTSTRAP_ADMIN_PHONE=
```

Optional:

- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`

If you do not need Google login for the demo, leave the Google variables empty and test with local accounts only.

## 4. Configure the frontend service

Deploy the `web` service from the same repository, but set:

```env
RAILWAY_DOCKERFILE_PATH=client/Dockerfile
BACKEND_UPSTREAM=http://server.railway.internal:8080
```

The web image uses an nginx template, so `BACKEND_UPSTREAM` can point at Railway's private backend hostname instead of Docker Compose's `server:8080`.
For this repo's default Railway setup, keep Google login on the same public `web` origin as the SPA.
Do not point browser auth at a private Railway hostname, and do not use a separate public backend domain unless you are intentionally running a different auth origin.

Optional only for unusual topologies:

```env
GOOGLE_AUTH_ORIGIN=https://<explicit-public-auth-origin>
```

If you set `GOOGLE_AUTH_ORIGIN`, it must be a public browser-reachable origin that is also whitelisted in Google Cloud Console.

## 5. Expose the frontend

In `web` service settings:

1. Open `Networking`
2. Click `Generate Domain` for a Railway domain first
3. Test the generated `https://...up.railway.app` URL
4. If you want your own domain, add a custom domain after the Railway URL works

Railway automatically provisions SSL for public and custom domains.

## 6. Point your backend callback/base URL at the final public frontend domain

After the `web` public domain exists, update:

```env
FRONTEND_BASE_URL=https://<actual-web-domain>
JWT_REFRESH_COOKIE_SECURE=true
```

Redeploy `server` after changing these variables.
For the normal Railway demo path, Google OAuth should also redirect back through this same public `web` domain.

## 7. First boot and cleanup

On the first successful deploy against a brand-new database:

1. Log in with the bootstrap admin account
2. Verify the admin exists and can access the app
3. Remove `APP_BOOTSTRAP_ADMIN_*` variables from `server`
4. Redeploy `server`

## 8. Demo checklist

- `web` public URL opens
- login with local admin account works
- refresh/login persistence works after page reload
- tables load for staff/admin
- reservation creation works for customer
- reservation approval works for staff/admin
- at least one pricing rule and table type exist in the database

## Notes

- Railway Trial currently supports enough services for this setup, but do not treat it as a long-term free production host
- The Trial plan allows only one custom domain, so for the demo it is safer to use the generated Railway domain first

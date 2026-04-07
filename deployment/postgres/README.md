# Fresh PostgreSQL Bootstrap

Use this path when deploying to a **brand-new PostgreSQL database**.

## 1. Apply the schema before starting the app

From the repository root:

```bash
psql "postgresql://$POSTGRES_USER:$POSTGRES_PASSWORD@$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB" \
  -f deployment/postgres/001_schema.sql
```

If you are restoring from inside the PostgreSQL container instead:

```bash
docker exec -i billiard-shop-postgres psql -U "$POSTGRES_USER" -d billiard_shop < deployment/postgres/001_schema.sql
```

## 2. Provide first-admin bootstrap env vars for the first boot only

Set these in the production environment before the first application start:

- `APP_BOOTSTRAP_ADMIN_EMAIL`
- `APP_BOOTSTRAP_ADMIN_PASSWORD`
- `APP_BOOTSTRAP_ADMIN_FULL_NAME` (optional, defaults to `System Admin`)
- `APP_BOOTSTRAP_ADMIN_PHONE` (optional)

The backend will create the first local `ADMIN` user only when:

- no admin user exists yet
- both bootstrap email and password are present

If an admin already exists, the bootstrap step is skipped.
If the bootstrap email is already used by a non-admin account, startup fails fast so the conflict is visible immediately.

## 3. Start the stack

After the schema is present, start the application normally. Production now uses schema validation instead of Hibernate auto-update, so startup will fail if the database shape does not match the app.

For the current bundled deploy stack:

- the web container serves plain HTTP on port `80`
- no `nginx/ssl/cert.pem` or `nginx/ssl/key.pem` files are required
- use `FRONTEND_BASE_URL=http://your-domain.example`
- use `JWT_REFRESH_COOKIE_SECURE=false`
- do not rely on Google OAuth for this HTTP-only VPS setup

## 4. Remove bootstrap admin env vars after the first successful deploy

Once you can log in with the initial admin account, remove the bootstrap admin env vars from the server and restart or redeploy normally.

# Price Checker

Spring Boot backend for seller inventory. Start with seller account CRUD and saving a Google Sheets link. An inventory import preview and confirmation flow is also available as a separate step.

The project's architecture and build direction are defined in [price-checker-architecture.md](price-checker-architecture.md). This README documents setup and the implemented API.

Java packages are organized by layer (`controllers`, `service`, `repo`, `entities`, `dto`, `exceptions`, `config`, `enums`, `utils`), then by feature such as `sellers`, `inventory`, `imports`, and `sheets`. See the architecture document for the package tree and responsibilities.

## Run locally

Use JDK 25 or newer, Maven, and PostgreSQL. The existing Maven wrapper can also be used where its distribution is available.

Open the local `.env` file in the project root and fill in `DATABASE_PASSWORD` with your PostgreSQL password. For a fresh checkout, copy `.env.example` to `.env` first. `.env` is ignored by Git; keep your real credentials there.

Use plain `KEY=value` entries without surrounding quotes or inline comments. Use forward slashes for Windows file paths; literal backslashes must be doubled because Spring reads this file as Java properties.

For the bundled PostgreSQL container, choose a password in `.env`, then start the database and app from PowerShell:

```powershell
docker compose up -d
.\script.bat
```

Docker Compose and Spring Boot both read `.env` when run from the project root. `script.bat` switches to that directory automatically and starts the app using installed Maven, falling back to the Maven wrapper. If `.env` is missing, the script creates it from `.env.example` and asks you to fill it in before running again. To launch without the script, run `mvn.cmd spring-boot:run` from the project root. Environment variables already set in your terminal take precedence over `.env`.

If PostgreSQL already runs elsewhere, fill in these settings in `.env` and run `.\script.bat` without starting Docker:

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/price_checker` |
| `DATABASE_USERNAME` | `price_checker` |
| `DATABASE_PASSWORD` | Required for your database; no password is committed |

Flyway creates the tables on startup; Hibernate validates the schema. Database rows and Docker data persist between application restarts.

The bundled container uses database and username `price_checker`. Changing `.env` does not change the password of an already initialized PostgreSQL volume; use that database's existing password.

## Swagger UI

After starting the app, open **http://localhost:8080/swagger-ui/index.html** (or `/swagger-ui.html`). All implemented API endpoints are listed with request and response schemas.

1. Expand **Authentication → Register a seller**, choose `/api/v1/auth/sellers/register`, and create your seller account.
2. Use **Authentication → Sign in** with that account's email and password. Copy `accessToken` from the response.
3. Click **Authorize** and paste the token (without adding the `Bearer` prefix).
4. Use **Try it out** on protected endpoints. Requests act on real accounts and inventory; delete and import confirmation operations apply their documented changes.

The docs page and OpenAPI documents are public; seller endpoints still require authentication. The JSON specification is at `/v3/api-docs`, and YAML is at `/v3/api-docs.yaml`. Google Sheets import endpoints require the service-account setup below.

## Configure Google Sheets access

Google Sheets uses **keyless authentication**. Google manages the service-account private keys; the app requests short-lived, read-only Sheets access tokens through IAM service-account impersonation. The organization policy disabling service-account key creation can stay enabled.

1. Enable the **Google Sheets API** and **IAM Service Account Credentials API** in the relevant Google Cloud project.
2. Create or reuse a dedicated Sheets service account. Its email is the app's Sheets identity.
3. Grant the calling identity **Service Account Token Creator** on that Sheets service account. For local development, the caller is your signed-in Google account. On Cloud Run, the caller is the attached runtime service account. Scope this grant to the target service account.
4. Set `GOOGLE_SHEETS_SERVICE_ACCOUNT_EMAIL` in `.env` locally or as a runtime environment variable on Google Cloud. This email is public configuration, not a secret.
5. Each seller shares their spreadsheet with the Sheets service account email as **Viewer**, then submits the normal Sheets link. Spreadsheets can remain private.

```dotenv
GOOGLE_SHEETS_SERVICE_ACCOUNT_EMAIL=price-checker-sheets@YOUR_PROJECT_ID.iam.gserviceaccount.com
```

For local development, run `gcloud auth application-default login` once with the Google account granted the permission above, then restart the app. The Cloud CLI manages its developer sign-in cache outside this project; no service-account private key is downloaded. Google Cloud deployments use their attached runtime identity through Application Default Credentials (ADC). Clear an old `GOOGLE_APPLICATION_CREDENTIALS` private-key override if one is set in your terminal; the app rejects downloaded service-account key credentials.

See [the complete keyless setup guide](docs/google-sheets-keyless.md) for Console navigation, local commands, Cloud Run variables, IAM permissions, and troubleshooting. This implementation follows [Google's Java ADC and impersonation guidance](https://docs.cloud.google.com/java/getting-started/getting-started-with-google-auth-library).

The app can start without Sheets access configured; import requests return `503 SHEET_CONFIGURATION` until ADC and IAM permissions are ready. Google credentials are never accepted from sellers or returned in API responses. Tokens are refreshed automatically and are never saved in `.env`.

## Prepare the spreadsheet

Import [the CSV template](examples/inventory-template.csv) into Google Sheets, or use these headers in the first row:

| Column | Required | Meaning |
| --- | --- | --- |
| `name` | Yes | Product name, up to 255 characters |
| `price` | Yes | Nonnegative decimal price in major units, e.g. `425000.00` |
| `sku` | Recommended | Stable, unique seller product key, up to 128 characters |
| `model` | No | Model, up to 255 characters |
| `description` | No | Description, up to 4,000 characters |
| `condition` | No | Free text, up to 255 characters, e.g. `Brand new`, `Used`, `Mint`, `Refurbished`, or `Has issues: cracked screen` |
| `currency` | No | ISO code; defaults to the seller's currency (`NGN` at registration) |
| `quantity` | No | Nonnegative whole number; blank means unknown |

Headers ignore case and surrounding whitespace. `seller_sku`, `no.`, and `no` are accepted aliases for `sku`. When omitted or blank, a stable key is derived from the normalized name and model. Changing either field then creates a new identity, so explicit SKUs are preferable. Reordering rows does not change derived identities. Duplicate keys fail validation.

Condition is optional; a missing or blank value is stored as unknown (`null`). Other condition descriptions are accepted, and previews and inventory responses include `condition`. Use separate SKUs for the same phone model offered in different conditions. Importing a blank condition clears any previously imported condition for that SKU.

Use numbers or plain decimal text for prices. Comma grouping such as `1,234.50` is accepted; currency symbols and locale formats such as `1.234,50` are rejected. Money is stored as integer minor units: `425000.00 NGN` becomes `42500000` kobo. Excess decimal precision is rejected rather than rounded. Entirely blank rows are ignored; an empty sheet cannot clear inventory.

Normal links such as `https://docs.google.com/spreadsheets/d/SHEET_ID/edit#gid=0` are accepted. `gid` selects the tab; without it, the first tab is used. Published-to-web and export links are not accepted. The app builds its own Google API URLs and never fetches the seller's URL directly.

## Seller API

All request bodies use `Content-Type: application/json`. Seller registration and login are public. Protected endpoints require `Authorization: Bearer <accessToken>`; use HTTPS in deployment. Ownership comes from the authenticated account's immutable ID. HTTP Basic, form login, and authentication cookies are disabled.

These PowerShell examples use a local test seller. Choose a different password for actual use:

```powershell
$api = 'http://localhost:8080/api/v1'
$registration = @{
    email = 'seller@example.com'
    password = 'local-seller-password-123'
    displayName = 'My phone shop'
    defaultCurrency = 'NGN'
} | ConvertTo-Json
Invoke-RestMethod "$api/auth/sellers/register" -Method Post -ContentType 'application/json' -Body $registration

$login = @{ email = 'seller@example.com'; password = 'local-seller-password-123' } | ConvertTo-Json
$session = Invoke-RestMethod "$api/auth/login" -Method Post -ContentType 'application/json' -Body $login
$headers = @{ Authorization = "Bearer $($session.accessToken)" }
```

### Step 1: seller CRUD and saving a sheet link

Registration above creates the seller. Read and update the profile:

```powershell
Invoke-RestMethod "$api/seller/me" -Headers $headers

$profile = @{
    email = 'seller@example.com'
    displayName = 'My updated phone shop'
    defaultCurrency = 'NGN'
} | ConvertTo-Json
Invoke-RestMethod "$api/seller/me" -Method Put -Headers $headers -ContentType 'application/json' -Body $profile
```

Profile updates require all three fields. Email addresses are stored lowercase and must be unique across all user roles; currency must be a supported ISO currency code. Existing tokens remain valid after an email change because they identify the account by UUID. Use the new email at the next login. Changing the default currency applies to future imports; existing inventory prices and currencies stay as recorded.

Paste a normal Google Sheets link to save it on the seller profile:

```powershell
$sheet = @{ url = 'https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit#gid=0' } | ConvertTo-Json
Invoke-RestMethod "$api/seller/me/sheet" -Method Put -Headers $headers -ContentType 'application/json' -Body $sheet
Invoke-RestMethod "$api/seller/me" -Headers $headers
```

The response includes `googleSheetUrl` (null until a link is saved). Each seller has one saved link; sending another replaces it. Saving validates the URL format and persists it in PostgreSQL. It does not fetch the sheet, verify access, create an import, or modify inventory. **No Google account or service-account credentials are needed for this step.** Configure Google access above when you are ready to import contents.

Remove the saved link:

```powershell
Invoke-RestMethod "$api/seller/me/sheet" -Method Delete -Headers $headers
```

To permanently delete the authenticated seller account, use `DELETE /api/v1/seller/me`. This returns `204` and removes the account, saved link, owned inventory, and import history in one transaction. Other sellers are unaffected. The deleted credentials no longer authenticate.

### Inventory import: preview and confirmation

Submit a sheet to create an import preview:

```powershell
$request = @{ url = 'https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit#gid=0' } | ConvertTo-Json
$preview = Invoke-RestMethod "$api/seller/inventory/imports" -Method Post -Headers $headers -ContentType 'application/json' -Body $request
$preview | ConvertTo-Json -Depth 8
```

The response includes `id`, `status` (`PREVIEW` or `INVALID`), `totalRows`, `validRows`, `errorRows`, the first 20 valid products, and row-specific `errors`. Header/empty-sheet errors are reported separately from data-row counts. Preview saves the import record but does not change live inventory. If any error is present, correct the sheet and submit another preview.

Review the preview, then confirm it explicitly:

```powershell
$result = Invoke-RestMethod "$api/seller/inventory/imports/$($preview.id)/confirm" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"confirm":true}'
Invoke-RestMethod "$api/seller/inventory?page=0&size=50" -Headers $headers
```

Confirmation imports the saved snapshot that was previewed. Changes made to Google Sheets after preview require a new import. Replacement is atomic: matching SKUs are updated, new SKUs are inserted, and omitted SKUs become inactive. A failure rolls the transaction back. Other sellers' inventory is unaffected. Confirming the same completed import again is idempotent. If another import has already changed inventory since a preview was created, that preview is stale and must be recreated.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/sellers/register` | Register a seller with a server-assigned SELLER role; `/auth/register` remains a compatibility alias |
| `POST` | `/api/v1/auth/login` | Exchange email/password for a 3-hour bearer token |
| `POST` | `/api/v1/auth/logout` | Revoke all outstanding tokens for the authenticated account |
| `GET` | `/api/v1/account/me` | Read your shared account, role, and permissions |
| `GET` | `/api/v1/seller/me` | Inspect the authenticated seller profile |
| `PUT` | `/api/v1/seller/me` | Update email, display name, and default currency |
| `DELETE` | `/api/v1/seller/me` | Permanently delete the account and its inventory/import history |
| `PUT` | `/api/v1/seller/me/sheet` | Save or replace the sheet link; body: `{"url":"https://docs.google.com/spreadsheets/d/SHEET_ID/edit"}` |
| `DELETE` | `/api/v1/seller/me/sheet` | Remove the saved link without changing inventory |
| `POST` | `/api/v1/seller/inventory/imports` | Fetch, validate, and save an import preview |
| `GET` | `/api/v1/seller/inventory/imports/{id}` | Read an owned import and its validation results |
| `POST` | `/api/v1/seller/inventory/imports/{id}/confirm` | Apply a valid preview; body: `{"confirm":true}` |
| `GET` | `/api/v1/seller/inventory?page=0&size=50` | List the seller's active inventory, maximum page size 200 |

Validation and import failures return `application/problem+json` with a stable `code`. Missing/inaccessible sheets return `422`; invalid URLs return `400`; unavailable configuration returns `503`; upstream Google failures return `502`; invalid or stale confirmations return `409`. Another seller's import returns `404`.

Imports run synchronously for this first version, with 10,000 data-row, 64-column, and 8 MiB response limits, and bounded Google API timeouts. Override `app.sheets.*` properties for deployment as needed. There is no background sync: submit a new link import when inventory changes. Keep the service key available only to the backend.

## Roles and security

Every login belongs to an `app_users` account. Seller-specific data lives in `sellers`, with the same UUID. Migration V3 moves existing seller credentials into the shared account table without changing IDs, password hashes, inventory, or import history.

| Role | Access |
| --- | --- |
| `ADMIN` | All implemented account and seller permissions; read and write across sellers through `/api/v1/management` |
| `MANAGER` | Read all accounts, sellers, inventory, and import history through management endpoints; no business mutations |
| `SELLER` | Read and change their own seller profile, sheet link, inventory imports, and account |
| `USER` | Read their own shared account and sign out; additional user features can define their own account permissions later |

Only seller registration is implemented. Registration requests cannot assign roles or permissions. There are no public Admin, Manager, or User registration endpoints and no seeded administrator credentials. To provision the first Admin, register an account and have a trusted database administrator run this once, replacing the email:

```sql
UPDATE app_users
SET role = 'ADMIN', token_version = token_version + 1, revision = revision + 1
WHERE email = 'your-admin@example.com';
```

The same trusted provisioning process can assign `MANAGER` or `USER`. Sign in again after provisioning. Roles map to explicit permissions in `Role`; request authorization and service method authorization enforce those permissions. Controllers accept request DTOs and call services, while `CurrentUserService` reads the authenticated identity. Ownership is checked again on service methods that accept target seller IDs.

Management endpoints expose paginated account and seller lists, individual seller profiles, inventory, and import history. Managers may use GET endpoints only. Admins may also update/delete a seller, save/remove the sheet link, preview imports, and confirm imports. Self-service `/seller/me` routes always refer to the caller's seller profile; use management routes to target another seller. An Admin or Manager account need not have a seller profile.

Tokens use RS256 signatures with issuer, audience, subject, expiry, and token-version validation. Current roles and enabled status are read from the database on every authenticated request. Disabled/deleted accounts and revoked tokens are rejected immediately. Logout revokes all devices' tokens. Access tokens expire after 3 hours; sign in again to obtain another token. Refresh tokens and public registration for other roles are not implemented.

On first startup the app generates a private signing key at `secrets/jwt-signing-key.json`, which Git ignores. Keep this file private and persistent across restarts. Set `JWT_SIGNING_KEY_PATH` in `.env` to use another protected location; deployed instances must share the intended signing key. Test keys are created only under `target/test-secrets`. Do not put access tokens or private keys in source control or logs. For public deployment, apply login rate limits at your reverse proxy/API gateway. CSRF is disabled because authentication uses only explicit bearer headers, with no ambient browser credentials; cross-origin access is not enabled.

Authentication failures return `401 UNAUTHENTICATED`; insufficient permissions return `403 ACCESS_DENIED`, both as `application/problem+json`. Requests outside the explicitly configured routes are denied.

## Verification

```powershell
mvn.cmd test
```

Tests use H2 in PostgreSQL compatibility mode with the actual Flyway migrations. Google requests and credential discovery are mocked, so tests require neither a Google account nor a running PostgreSQL or Ollama instance. The suite covers registration, JWT login and validation, role restrictions, service ownership checks, token revocation, disabled accounts, migration of existing seller data, Swagger UI, seller CRUD and deletion isolation, keyless Sheets token scoping and refresh, sheet parsing, staged imports, replacement, and idempotency.

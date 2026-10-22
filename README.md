# Price Checker

Spring Boot backend for seller inventory. Saving a public Google Sheets link reads and validates the sheet, then imports its products into PostgreSQL. Sellers can delete database items independently of the source sheet. A separate import preview and confirmation flow is also available.

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

The docs page and OpenAPI documents are public; seller endpoints still require authentication. The JSON specification is at `/v3/api-docs`, and YAML is at `/v3/api-docs.yaml`. Google Sheets must have the Viewer sharing settings below.

## Configure Google Sheets access

The current integration downloads publicly readable CSV exports using HTTP GET only. It never writes to Google Sheets and does not require Google credentials or Editor access.

1. In Google Sheets, select **Share > General access > Anyone with the link > Viewer**.
2. Allow viewers to download the sheet.
3. Submit the normal spreadsheet link, including `gid` when selecting a particular tab.

Private or download-blocked sheets return `422 SHEET_NOT_PUBLIC`. Deleting items in the app affects PostgreSQL only; the original spreadsheet remains unchanged.

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
| `category` | For catalog browsing | `GADGET` or `ELECTRICAL_APPLIANCE`; legacy `PHONE` remains accepted; blank keeps the row unclassified |
| `brand` | When category is set | Manufacturer/company, e.g. `Samsung`, `Apple`, `Hisense`, or `LG` |
| `gadget_type` | For gadgets | `PHONE`, `HEADSET`, `LAPTOP`, `PC`, `TABLET`, `SMARTWATCH`, or `OTHER` |
| `series` | For phones | Family such as `S`, `A`, or `iPhone 13` |
| `appliance_type` | For appliances | Type such as `Television`, `Refrigerator`, or `Washing machine` |
| `size` | No; appliances only | Consistent size/capacity text, such as `43 inch`, `8 kg`, or `200 L` |
| `features` | No; appliances only | Features up to 1,000 characters, separated by semicolons, e.g. `4K;Smart TV;Inverter` |

Headers ignore case and surrounding whitespace. `seller_sku`, `no.`, and `no` are accepted aliases for `sku`; `company` aliases `brand`, and `type` aliases `appliance_type`. When SKU is omitted or blank, a stable key is derived from the normalized name and model. Changing either field then creates a new identity, so explicit SKUs are preferable. Reordering rows does not change derived identities. Duplicate keys fail validation.

Classification is explicit. If `category` is blank or absent, the old sheet format still imports; existing catalog assignments are preserved and new rows remain unclassified. Once category is set, brand and the category's required fields must be valid. A phone cannot use appliance-only fields, and an appliance cannot use a phone series. Re-save an existing sheet with the new columns to make its products available in catalog suggestions. No category or model is guessed from a product name.

Condition is optional; a missing or blank value is stored as unknown (`null`). Other condition descriptions are accepted, and previews and inventory responses include `condition`. Use separate SKUs for the same phone model offered in different conditions. Importing a blank condition clears any previously imported condition for that SKU.

Use numbers or plain decimal text for prices. Comma grouping such as `1,234.50` is accepted; currency symbols and locale formats such as `1.234,50` are rejected. Money is stored as integer minor units: `425000.00 NGN` becomes `42500000` kobo. Excess decimal precision is rejected rather than rounded. Entirely blank rows are ignored; an empty sheet cannot clear inventory.

Normal links such as `https://docs.google.com/spreadsheets/d/SHEET_ID/edit#gid=0` are accepted. `gid` selects the tab; without it, the first tab is used. Published-to-web and export links are not accepted. The app builds its own Google CSV export URL and follows only supported Google download redirects.

## Seller API

All request bodies use `Content-Type: application/json`. Seller registration and login are public. Protected endpoints require `Authorization: Bearer <accessToken>`; use HTTPS in deployment. Ownership comes from the authenticated account's immutable ID. HTTP Basic, form login, and authentication cookies are disabled.

For registration and login in Postman, choose **No Auth** and remove any manually configured `Authorization` header. An invalid or expired bearer token is rejected even on public endpoints because JWT authentication runs before route authorization. Use `POST` with the exact documented path (without a trailing slash), and select **Body → raw → JSON**. Both registration paths create a SELLER account; `/api/v1/auth/register` is a compatibility alias. Internal error dispatches preserve the original error status, such as `415` for an unsupported content type.

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

### Seller profiles and importing a saved sheet

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

The response includes `googleSheetUrl` (null until a link is saved). Each seller has one saved link. Saving or replacing it downloads the sheet, validates every product row, and applies its inventory to PostgreSQL in the same transaction as saving the link. The response remains a seller profile. Read the imported products with `GET /api/v1/seller/inventory`; their database IDs can be used to delete individual items.

Matching SKUs are updated, new SKUs are inserted, and products omitted from the sheet become inactive. Re-saving the same sheet updates existing products without creating duplicate SKUs. Invalid rows or an empty sheet return `422 SHEET_VALIDATION_FAILED` with `importId` and row-specific `errors`, leaving the previous link and inventory unchanged. An import record is retained for diagnostics. If inventory changed while the sheet was being read, the request returns `409 IMPORT_STALE`; retry saving the sheet.

`GET /api/v1/seller/me/sheet` reads the current source rows without changing PostgreSQL. There is no startup backfill or background synchronization. For links saved before automatic import was added, send the same `PUT /api/v1/seller/me/sheet` once to populate inventory.

To delete one imported item from PostgreSQL, use its `id` from the inventory response:

```http
DELETE /api/v1/seller/inventory/{id}
Authorization: Bearer <accessToken>
```

This returns `204` and deletes only that seller's database item. Another seller's item or an unknown ID returns `404 ITEM_NOT_FOUND`. Deletion needs no Google request and leaves the source sheet and import history unchanged. Earlier previews become stale. A later explicit re-import or re-save can restore products still present in the sheet.

Remove the saved link:

```powershell
Invoke-RestMethod "$api/seller/me/sheet" -Method Delete -Headers $headers
```

To permanently delete the authenticated seller account, use `DELETE /api/v1/seller/me`. This returns `204` and removes the account, saved link, owned inventory, and import history in one transaction. Other sellers are unaffected. The deleted credentials no longer authenticate.

### Optional inventory import: preview and confirmation

Use this separate flow when you want to review changes before applying them. Submit a sheet to create an import preview, or use `POST /api/v1/seller/inventory/imports/saved` without a body to preview your saved URL:

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

Confirmation imports the saved snapshot that was previewed. Changes made to Google Sheets after preview require a new import. Replacement is atomic: matching SKUs are updated, new SKUs are inserted, and omitted SKUs become inactive. A failure rolls the transaction back. Other sellers' inventory is unaffected. Confirming the same completed import again is idempotent. An import or item deletion after a preview makes that preview stale; create a fresh preview before applying it.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/sellers/register` | Register a seller with a server-assigned SELLER role; `/auth/register` remains a compatibility alias |
| `POST` | `/api/v1/auth/login` | Exchange email/password for a 3-hour bearer token |
| `POST` | `/api/v1/auth/logout` | Revoke all outstanding tokens for the authenticated account |
| `GET` | `/api/v1/account/me` | Read your shared account, role, and permissions |
| `GET` | `/api/v1/seller/me` | Inspect the authenticated seller profile |
| `PUT` | `/api/v1/seller/me` | Update email, display name, and default currency |
| `DELETE` | `/api/v1/seller/me` | Permanently delete the account and its inventory/import history |
| `PUT` | `/api/v1/seller/me/sheet` | Save the sheet link and immediately import its products; body: `{"url":"https://docs.google.com/spreadsheets/d/SHEET_ID/edit"}` |
| `GET` | `/api/v1/seller/me/sheet` | Read source rows without changing inventory |
| `DELETE` | `/api/v1/seller/me/sheet` | Remove the saved link without changing inventory |
| `POST` | `/api/v1/seller/inventory/imports` | Fetch, validate, and save an import preview |
| `POST` | `/api/v1/seller/inventory/imports/saved` | Preview the saved sheet without supplying its URL again |
| `GET` | `/api/v1/seller/inventory/imports/{id}` | Read an owned import and its validation results |
| `POST` | `/api/v1/seller/inventory/imports/{id}/confirm` | Apply a valid preview; body: `{"confirm":true}` |
| `GET` | `/api/v1/seller/inventory?page=0&size=50` | List the seller's active inventory, maximum page size 200 |
| `DELETE` | `/api/v1/seller/inventory/{id}` | Delete an owned database item without modifying the sheet |

Validation and import failures return `application/problem+json` with a stable `code`. Missing/inaccessible sheets or invalid inventory rows on save return `422`; invalid URLs return `400`; upstream Google failures return `502`; invalid or stale confirmations return `409`. Another seller's import or inventory item returns `404`.

Imports run synchronously with the current 1,000 data-row, 64-column, and 8 MiB response limits and bounded Google timeouts. Override `app.sheets.*` properties for deployment as needed. There is no background sync: re-save the link or use preview/confirmation when you want to import sheet changes. Google access remains read-only for every operation.

## Catalog and price browsing

The shared catalog uses Java inheritance: abstract `Item`, `Gadget`, and `ElectricalAppliance`, persisted using joined tables `items`, `gadget_items`, and `electrical_appliance_items`. Gadgets have a type such as PHONE, HEADSET, LAPTOP or PC; only phone gadgets require a series. Each seller's `inventory_items` row can reference an item while retaining its own price, currency, condition, quantity, and SKU. Equivalent normalized catalog definitions share an item ID. Deleting a seller listing leaves the shared catalog item and other sellers' offers intact. Migration V7 preserves existing phone IDs while moving them under GADGET/PHONE.

Anyone can read these catalog routes without registering or logging in:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/catalog/domains` | Choose `GADGET` or `ELECTRICAL_APPLIANCE` |
| `GET` | `/api/v1/catalog/{domain}/brands` | Available brands within the selected domain |
| `GET` | `/api/v1/catalog/{domain}/options?brand=Samsung` | Phone series, or appliance types/sizes/features |
| `GET` | `/api/v1/catalog/{domain}/items?brand=Samsung&gadgetType=PHONE&series=S` | Paginated exact items; gadgets require type, phone gadgets require series |
| `GET` | `/api/v1/catalog/{domain}/items/{id}` | Exact item within its domain |
| `GET` | `/api/v1/catalog/{domain}/items/{id}/offers?currency=NGN` | Seller prices, sorted within one currency |

Appliance item filters are `applianceType`, `applianceSize`, and `feature`. `q` searches names/model codes. Pagination uses `page` and `size`; `applianceSize` is the physical size filter. Offers support an optional exact, case-insensitive `condition` filter. No exchange-rate conversion is performed. Suggestions and offers exclude inactive listings, known zero stock, and disabled sellers; unknown stock remains eligible. Each query carries the selected domain, so phone and appliance suggestions cannot mix.

`POST /api/v1/management/items` lets an Admin create a definition independently of a sheet. `PUT /api/v1/seller/inventory/{id}/item` with `{"itemId":"UUID"}` lets an owner classify an existing listing; Admins can use `/api/v1/management/sellers/{sellerId}/inventory/{id}/item`. Classification happens in the database and leaves Google Sheets unchanged. It also makes earlier inventory previews stale.

See [the catalog guide](docs/item-catalog.md) for gadget/appliance examples, import rules, and API requests.

## Telegram and admin reference prices

Ch3cker supports private Telegram conversations without customer registration or login. It saves Telegram profiles and supplied names, guides domain/type selection, and uses local Ollama for model recognition and evidence-backed specification extraction. Product answers fetch current public sources and show specifications in tables, with side-by-side columns for comparisons and links to checked sources. Unverified fields remain explicit. Prices come from the database: admin reference prices take precedence over eligible seller averages. Customers can browse seller offers in tables, save their preferred option and continue through the supplied contact link. Conversations expire after one idle hour or end on `cancel`.

Add `TELEGRAM_BOT_TOKEN` and `TELEGRAM_ENABLED=true` to `.env`, keep Ollama running with the configured model downloaded (default `gemma4:e2b-it-q4_K_M`), then restart the application. Public specification lookups require internet access but no API account. Telegram uses long polling; no public webhook is needed. Open `/admin/index.html` for admin reference prices and Telegram customer selections. Historical selection snapshots remain after listing deletion. WhatsApp integration remains future work. See [bot setup and live-source coverage](docs/telegram-bot.md).

See [Telegram setup, flow, APIs and storage](docs/telegram-bot.md) for configuration, first-run checks and limitations. Only safe catalog GET routes are public; management data and customer profiles require Admin/Manager access.

## Roles and security

Every login belongs to an `app_users` account. Seller-specific data lives in `sellers`, with the same UUID. Migration V3 moves existing seller credentials into the shared account table without changing IDs, password hashes, inventory, or import history.

| Role | Access |
| --- | --- |
| `ADMIN` | All implemented account and seller permissions; read and write across sellers through `/api/v1/management` |
| `MANAGER` | Read all accounts, sellers, inventory, and import history through management endpoints; no business mutations |
| `SELLER` | Read and change their own seller profile, sheet link, inventory imports, and account |
| `USER` | Read their own shared account, browse domain-scoped catalog items and seller prices, and sign out |

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

Tests use H2 in PostgreSQL compatibility mode with the actual Flyway migrations. Google requests are mocked, so tests require neither a Google account nor a running PostgreSQL or Ollama instance. The suite covers registration, JWT validation, role and ownership restrictions, token revocation, account migration, Swagger UI, automatic sheet imports, row validation, database-only item deletion, stale previews, read-only CSV downloads, staged imports, replacement, and idempotency.

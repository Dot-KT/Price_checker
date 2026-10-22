# Ch3cker Telegram bot

Telegram customers do not register, log in, or receive API access tokens. A private message to the bot creates a `telegram_users` profile keyed by Telegram's numeric user ID. Telegram profile names and username are separate from the original name the customer supplies.

## Run locally

Create a bot through BotFather and add these values to the project's private `.env` file:

```properties
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=your-BotFather-token
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=gemma4:e2b-it-q4_K_M
OLLAMA_TIMEOUT_SECONDS=120
OLLAMA_STRUCTURED_OUTPUT=true
```

Keep the token private. `.env` is ignored by Git. PostgreSQL and the Ollama application/server must be running. The configured model must already be downloaded; the bot does not pull models at startup:

```powershell
ollama pull gemma4:e2b-it-q4_K_M
mvn.cmd spring-boot:run
```

If the app is already running in an IDE, stop and restart it after changing `.env` or the source. Flyway applies V7 (gadget catalog) and V8 (Telegram and price intel) at startup. Tests always disable Telegram polling.

The application uses Telegram long polling, so a public server URL, HTTPS tunnel and webhook are unnecessary for local use. Run only one bot poller for the token. If a webhook was configured previously, remove it using Telegram's `deleteWebhook` API before starting polling; the application detects this conflict without deleting an existing webhook. Keep the application and computer running while customers use the bot.

Local structured JSON output is enabled by default. Use a local model and the localhost Ollama URL to keep inference on this computer. This workstation also has the text-only `ch3cker-gemma4:e2b` alias, created from the verified official Gemma 4 E2B Q4_K_M language weights; its `.env` can select that alias without downloading the unused vision projector. Other installations should pull the official tag above. A newer model still has a training cutoff, so current facts come from live documents. No cloud inference or paid API credentials are required.

## Customer flow

1. Send any message or `/start`. Ch3cker introduces itself and asks for the customer's original name.
2. The name is saved, then buttons offer Gadgets or Appliances.
3. Gadgets offer PHONE, HEADSET, LAPTOP, PC, TABLET, SMARTWATCH and OTHER. Appliances continue directly to the request prompt.
4. Describe the exact model and desired information, such as price, specifications or a comparison of two products. Ollama extracts the requested models and intent. All database matching remains inside the chosen domain and gadget type. Ambiguous database matches produce exact-model buttons; the customer can also refine the description.
5. Every product answer first fetches current public documents. Specifications always use a table; comparisons put the requested products in separate columns. The response links its sources with check timestamps. Missing or unsupported facts say `Not verified`, including when a source cannot be retrieved; a failed lookup never means a model is unreleased. Prices use separate tables populated from the database. Missing catalog products or offers point customers to <https://wa.link/nij6bk>.
6. After an answer with matched catalog items, choose Show sellers to see eligible listings. Listings include seller name, model, condition, description, exact price, currency and quantity. Results are paginated eight offers at a time; every eligible offer is accessible.
7. Select an offer. The app rechecks availability and displayed details; changed offers require a refresh before selection. It saves both the shared item ID and seller listing ID, plus a snapshot, then supplies the contact link and asks the customer to state their name to the team.

Follow-up questions carry previous product names. `cancel` and `/cancel` end the conversation. After one hour without customer activity, the conversation expires; the next message starts the name flow again. `/start` also restarts an active conversation. New conversations clear the profile's current selection, while completed conversation records retain earlier selection snapshots. Old buttons cannot select options from another conversation or an earlier step.

Group messages are ignored. No WhatsApp bot integration is included; the supplied URL is the requested handoff link.

## Live specifications and tables

The lookup uses direct manufacturer specification pages for supported iPhone and Samsung Galaxy S names, plus Wikipedia's public search and page APIs for exact product pages across categories. There is no Google dependency or API key. This is a bounded public-source lookup, not a general web crawler: older manufacturer URLs, renamed pages, unsupported products and blocked sources can leave fields unverified. No dedicated commercial specification provider is configured.

Only product names go to public providers; customer identities and full conversations stay out of those requests. Retrieved documents go to the configured local Ollama server. Manufacturer fields are parsed directly where supported. Additional model-extracted cells require a supporting quote from a source belonging to the same requested product. Manufacturer values take precedence. Shared Pro/Pro Max manufacturer pages are separated before extraction. The application creates and escapes the tables rather than accepting generated HTML. Public-page prices never replace admin intel or seller averages.

Telegram delivery uses native Rich Message HTML tables through `sendRichMessage`. An older API endpoint that rejects rich messages receives aligned monospace tables in ordinary messages instead. Single-product specifications, two-product comparisons, price quotes and individual seller offer details all use this formatting; onboarding and navigation remain ordinary conversational prompts.

## Prices

For each exact catalog item and requested currency:

1. Use an admin reference price labelled **Price checker specified intel**, if present. This reference applies to the item in that currency; it is not condition-specific.
2. Otherwise calculate the arithmetic mean of eligible seller offers, labelled **Seller average**, with offer and distinct-seller counts. Each listing has equal weight. Known zero stock, inactive listings and disabled sellers are excluded. Unknown stock is eligible.
3. If no eligible offers exist in that currency/condition, report that no price is available. There is no currency conversion or generated fallback price.

By default, averages include all listed conditions. A requested condition narrows eligible offers using exact, case-insensitive matching. An unknown condition is not silently treated as brand new. Admin reference prices remain the first choice.

Seller prices remain unchanged when an admin saves or removes a reference. Google Sheets are never written to. Re-saving a sheet is still required to import subsequent sheet changes.

## Admin page and API

Open <http://localhost:8080/admin/index.html> and sign in with an existing ADMIN account. The page supports catalog item search/creation, reference price editing/removal, customer search by original name, username or Telegram ID, and conversation selection lookup. MANAGER accounts can read data; writes require ADMIN. The first-admin provisioning instructions are in the README. No admin password is seeded.

The page keeps the access token only in memory; refreshing requires sign-in again. Customers with the same original name remain separate profiles, distinguished by Telegram ID.

| Method | Endpoint | Access |
| --- | --- | --- |
| GET | `/api/v1/management/items?q=Samsung` | Admin/Manager; includes catalog items without seller offers |
| POST | `/api/v1/management/items` | Admin; create a Gadget or ElectricalAppliance |
| GET | `/api/v1/management/items/{id}/price-intel?currency=NGN` | Admin/Manager; current reference or fallback quote, including internal notes |
| PUT | `/api/v1/management/items/{id}/price-intel` | Admin; body `{"price":"425000.00","currency":"NGN","notes":"Internal source"}` |
| DELETE | `/api/v1/management/items/{id}/price-intel?currency=NGN` | Admin; restores fallback pricing |
| GET | `/api/v1/management/telegram/users?q=Ada` | Admin/Manager; profiles and current selection |
| GET | `/api/v1/management/telegram/users/{telegramId}/conversations` | Admin/Manager; session records and historical selections |
| GET | `/api/v1/catalog/{domain}/items/{id}/price?currency=NGN` | Public; quote with internal notes removed |

Lists accept `page` and `size` (maximum 200). Existing catalog GET routes are also public and expose safe product/offer DTOs. Account records, source-sheet URLs, imports and Telegram profiles remain protected.

Reference prices are submitted in currency units and stored as integer minor units. Excess decimal precision is rejected. An item's reference price is unique per currency.

## Existing sheets and database tables

New classified gadget rows use `category=GADGET` and `gadget_type=PHONE`, `LAPTOP`, etc. Only PHONE gadgets require `series`. Appliance rows retain `category=ELECTRICAL_APPLIANCE` and `appliance_type`, with optional size/features. See the [inventory template](../examples/inventory-template.csv).

Old sheets without classification continue importing but cannot participate in exact catalog matching until classified. Add metadata and re-save the sheet, or create a catalog item and assign its ID to the seller listing using the existing inventory classification endpoint. Legacy `category=PHONE` sheets continue working as GADGET/PHONE. V7 preserves existing phone item IDs and identity hashes.

| Table | Stored data |
| --- | --- |
| `items`, `gadget_items`, `electrical_appliance_items` | Shared catalog definitions using Java joined inheritance |
| `price_checker_intel` | Admin reference per item/currency, notes, updating admin and timestamp |
| `telegram_users` | Telegram identity, profile names, original name and current selected item/listing/snapshot |
| `telegram_conversations` | Phase, category/type, last request/intent, activity timestamps and historical selection |
| `telegram_updates` | Durable incoming updates, processing/delivery state and retry leases |

Completed selection snapshots survive listing deletion. New sessions do not erase past selections. Ollama and Telegram HTTP calls run outside conversation database transactions. Incoming update IDs prevent repeat processing; saved replies retry after transient network failures. A crash after Telegram accepts a reply but before its delivery acknowledgement is committed can repeat that reply. This is at-least-once delivery, not a cross-system atomic transaction.

## Verify

```powershell
mvn.cmd test
```

Tests mock Telegram and Ollama and apply the actual migrations to H2 in PostgreSQL mode. They cover identity storage, domain/type separation, averages/reference precedence, comparisons, ambiguity, expiry/cancel, stale buttons, changed/unavailable offers, duplicate updates, delivery ordering, admin access and retained selection snapshots. Live setup checks can use Telegram `getMe` without sending a message, then a local Ollama `/api/chat` request. Finish the user-facing smoke check by opening the bot in Telegram and sending `hi` yourself.

Opt-in live checks cover a single product, a side-by-side comparison and Wikipedia-only extraction. They fetch public product pages and call local Ollama without sending Telegram messages:

```powershell
mvn.cmd test -Dtest=LiveSpecificationsSmokeTest -DliveSpecificationsSmoke=true -DliveOllamaModel=ch3cker-gemma4:e2b
```

Use the installed model's name for `liveOllamaModel`. The check writes an HTML table preview and plain-text fallback to `target/live-specifications-smoke.html` and `.txt`.

Protocol references: [Telegram Bot API](https://core.telegram.org/bots/api), [Ollama chat API](https://docs.ollama.com/api/chat), [Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs).

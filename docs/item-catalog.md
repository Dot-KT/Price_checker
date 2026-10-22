# Item catalog and guided price browsing

The catalog describes a product independently of its sellers. `Item` is an abstract JPA entity using `JOINED` inheritance. `Gadget` adds a gadget type and optional phone series; `ElectricalAppliance` adds appliance type, size/capacity, and features. Shared fields include ID, category discriminator, brand, exact name, and optional model/variant code.

Database tables:

| Table | Purpose |
| --- | --- |
| `items` | Shared identity and common fields |
| `gadget_items` | Gadget type and phone series, with the same ID as `items` |
| `electrical_appliance_items` | Appliance-only fields, with the same ID as `items` |
| `inventory_items` | Seller offers; nullable `item_id` links existing or newly imported rows to the catalog |

Migration V6 preserves existing inventory. V7 moves existing phone definitions into gadget_items with gadget_type=PHONE while preserving IDs and catalog identities. It leaves existing rows unclassified instead of inventing their category. Add metadata to a sheet and re-save it, or link a listing to an Admin-created catalog item.

## Sheet columns

Keep the existing `sku,name,model,description,condition,price,currency,quantity` columns and add:

| Column | Gadget | Electrical appliance |
| --- | --- | --- |
| `category` | `GADGET` | `ELECTRICAL_APPLIANCE` |
| `gadget_type` | Required: `PHONE`, `HEADSET`, `LAPTOP`, `PC`, `TABLET`, `SMARTWATCH`, or `OTHER` | Leave blank |
| `brand` | Required, e.g. Samsung or Apple | Required, e.g. Hisense, LG, itel, or oraimo |
| `series` | Required for PHONE, e.g. S, A, iPhone 11, iPhone 13; optional for other gadgets | Leave blank |
| `appliance_type` | Leave blank | Required, e.g. Television, Refrigerator, Washing machine |
| `size` | Leave blank; put storage/variant in name/model | Optional size/capacity, e.g. 43 inch, 8 kg, 200 L |
| `features` | Leave blank | Optional semicolon-separated descriptions, e.g. 4K;Smart TV |

`brand` is the company, `series` is a product family, and `name` identifies the exact device. `model` can hold a manufacturer model code or variant; include storage/capacity distinctions consistently to avoid grouping different products. Use different seller SKUs for different conditions or variants.

Old sheets without category still import. Blank category does not clear a previous catalog assignment. A row with category must satisfy that category's fields; otherwise the entire save returns row-specific validation errors and existing inventory remains unchanged. `company` is accepted as `brand`, and `type` as `appliance_type`. Use GADGET plus gadget_type in new sheets. Legacy category PHONE/Phones is accepted and defaults to gadget_type=PHONE. Old sheets need no rewrite. API paths now use GADGET or ELECTRICAL_APPLIANCE.

Catalog definitions are deduplicated using category, brand, name, model, series, appliance type, size, and features after whitespace/case normalization. This is exact matching, not fuzzy product recognition. Standardize seller values or assign the same catalog ID manually when sellers use different descriptions. iPhone is accepted as an alias of Apple; LG, Life is good, and Life's Good resolve to LG. Other brands are allowed.

The [CSV template](../examples/inventory-template.csv) contains both phone and appliance examples. Saving a valid sheet creates or reuses its catalog items while importing seller prices. Preview-only requests create neither inventory nor catalog items until confirmed. Google access remains read-only.

## Gadget flow (phone example)

Catalog GET endpoints are public; use No Auth in Postman. Admin and seller writes still require their access token.

1. Choose a domain: `GET {{baseUrl}}/api/v1/catalog/domains`.
2. Choose a brand: `GET {{baseUrl}}/api/v1/catalog/GADGET/brands`.
3. Choose a series: `GET {{baseUrl}}/api/v1/catalog/GADGET/options?brand=Samsung`.
4. Choose an exact phone: `GET {{baseUrl}}/api/v1/catalog/GADGET/items?brand=Samsung&gadgetType=PHONE&series=S`.
5. Compare offers using the selected catalog item's ID: `GET {{baseUrl}}/api/v1/catalog/GADGET/items/{id}/offers?currency=NGN`.

For Apple, choose `brand=Apple` and an available series such as `series=iPhone%2013`. Brand and series must be selected before requesting phone models. `q` can narrow an exact name or model code within that selection.

## Appliance flow

1. Choose `ELECTRICAL_APPLIANCE` from `/catalog/domains`.
2. Request `/catalog/ELECTRICAL_APPLIANCE/brands`.
3. Request `/catalog/ELECTRICAL_APPLIANCE/options?brand=Hisense` to see available types, sizes, and features.
4. Request `/catalog/ELECTRICAL_APPLIANCE/items?brand=Hisense&applianceType=Television&applianceSize=43%20inch&feature=Smart`.
5. Compare `/catalog/ELECTRICAL_APPLIANCE/items/{id}/offers?currency=NGN`.

Appliance filters are optional after choosing a brand. Type and size match case-insensitively; feature and `q` searches match text. The generic `size` query parameter controls pagination, so use `applianceSize` for product dimensions/capacity. Phone-series filters are rejected in appliance requests, and appliance filters are rejected in phone requests. Requesting an ID in the wrong domain returns 404.

## Prices and availability

Suggestions come from actual classified seller offers. Inactive listings, disabled sellers, and known zero quantities are excluded; unknown quantity is eligible. A catalog item with no eligible offers is not suggested. No sample products or prices are seeded.

Offers contain the seller's display name and ID, inventory listing ID, condition, price in minor units, currency, quantity, and last update. They exclude account email, password data, and source sheet URLs. Each comparison uses one requested currency, NGN by default; currencies are never compared as if their numeric values were interchangeable. `condition=Used` can narrow conditions without combining them in the catalog identity.

Deleting an offer in the app leaves the shared item, other sellers' offers, and source sheet unchanged. Re-importing a product still in the sheet can restore its offer. Conflicting concurrent imports may return 409; retry with a fresh sheet save or preview.

## Create and classify without changing a sheet

An Admin can create or reuse a definition:

```http
POST /api/v1/management/items
Content-Type: application/json
```

```json
{
  "category": "GADGET",
  "gadgetType": "PHONE",
  "brand": "Samsung",
  "series": "S",
  "name": "Galaxy S24 Ultra",
  "model": "SM-S928B/256GB"
}
```

An appliance definition uses `category=ELECTRICAL_APPLIANCE`, `applianceType`, optional `size` and `features`, and omits series. Managers, Sellers, and Users cannot create standalone definitions through the management API.

The owner can then send `PUT /api/v1/seller/inventory/{inventoryId}/item` with `{"itemId":"catalog UUID"}`. Admins can use `PUT /api/v1/management/sellers/{sellerId}/inventory/{inventoryId}/item`. Both update only the database and invalidate earlier inventory previews.

## Extending categories and using a bot

A new category adds an `Item` subclass, its joined table and migration, an `ItemDomain` value/discriminator, its validation and filters, and the appropriate next browsing step. Existing phone and appliance classes keep their own fields.

All model and price APIs include the domain in their path. A frontend or bot carries that selected domain through its conversation and calls the relevant endpoints. This implementation provides the backend catalog and deterministic suggestion/price APIs; it does not add WhatsApp, Telegram, or natural-language conversation handling.

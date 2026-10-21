ALTER TABLE sellers ADD COLUMN google_sheet_url VARCHAR(2048);

ALTER TABLE inventory_items DROP CONSTRAINT fk_inventory_seller;
ALTER TABLE inventory_items ADD CONSTRAINT fk_inventory_seller
    FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE CASCADE;

ALTER TABLE sheet_imports DROP CONSTRAINT fk_sheet_import_seller;
ALTER TABLE sheet_imports ADD CONSTRAINT fk_sheet_import_seller
    FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE CASCADE;

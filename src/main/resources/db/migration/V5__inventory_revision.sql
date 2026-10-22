ALTER TABLE sellers ADD COLUMN inventory_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sheet_imports ADD COLUMN base_inventory_revision BIGINT NOT NULL DEFAULT 0;

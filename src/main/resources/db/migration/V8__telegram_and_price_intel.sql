CREATE TABLE price_checker_intel (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES items(id),
    currency VARCHAR(3) NOT NULL,
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    notes VARCHAR(2000),
    updated_by UUID NOT NULL REFERENCES app_users(id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_price_intel_item_currency UNIQUE(item_id, currency)
);

CREATE TABLE telegram_users (
    id UUID PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    real_name VARCHAR(255),
    selected_item_id UUID REFERENCES items(id) ON DELETE SET NULL,
    selected_listing_id UUID REFERENCES inventory_items(id) ON DELETE SET NULL,
    selection_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_telegram_real_name ON telegram_users(real_name);

CREATE TABLE telegram_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES telegram_users(id) ON DELETE CASCADE,
    phase VARCHAR(32) NOT NULL,
    domain VARCHAR(32),
    gadget_type VARCHAR(32),
    question_text VARCHAR(4096),
    intent_json TEXT,
    item_ids_json TEXT NOT NULL DEFAULT '[]',
    candidate_ids_json TEXT NOT NULL DEFAULT '[]',
    offer_ids_json TEXT NOT NULL DEFAULT '[]',
    offer_snapshots_json TEXT NOT NULL DEFAULT '[]',
    keyboard_version INTEGER NOT NULL DEFAULT 0,
    selection_json TEXT,
    pending_update_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_telegram_conversation_user ON telegram_conversations(user_id, created_at);
CREATE INDEX idx_telegram_conversation_idle ON telegram_conversations(ended_at, last_activity_at);

CREATE TABLE telegram_updates (
    update_id BIGINT PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    incoming_json TEXT NOT NULL,
    replies_json TEXT,
    delivered_count INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_telegram_update_pending ON telegram_updates(lease_until, update_id);
CREATE INDEX idx_telegram_update_chat_order ON telegram_updates(chat_id, update_id);

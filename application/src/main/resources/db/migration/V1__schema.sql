CREATE TABLE products (
 id UUID PRIMARY KEY, owner VARCHAR(255) NOT NULL, name VARCHAR(120) NOT NULL,
 price NUMERIC(19,2) NOT NULL CHECK(price>0), stock INTEGER NOT NULL CHECK(stock>=0), version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX products_owner_name_id ON products(owner,name,id);
CREATE TABLE orders (
 id UUID PRIMARY KEY, owner VARCHAR(255) NOT NULL, request_key VARCHAR(128) NOT NULL,
 product_id UUID NOT NULL REFERENCES products(id), quantity INTEGER NOT NULL CHECK(quantity>0), total NUMERIC(19,2) NOT NULL,
 UNIQUE(owner,request_key)
);
CREATE TABLE outbox (
 id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, published_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending ON outbox(created_at,id) WHERE published_at IS NULL;
CREATE TABLE processed_event (consumer_name VARCHAR(100) NOT NULL, event_id UUID NOT NULL, PRIMARY KEY(consumer_name,event_id));
CREATE TABLE analytics (order_id UUID PRIMARY KEY, total NUMERIC(19,2) NOT NULL);
CREATE TABLE refresh_session (id UUID PRIMARY KEY, revoked BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE refresh_token (token_hash VARCHAR(64) PRIMARY KEY, session_id UUID NOT NULL REFERENCES refresh_session(id), expires_at TIMESTAMPTZ NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE);

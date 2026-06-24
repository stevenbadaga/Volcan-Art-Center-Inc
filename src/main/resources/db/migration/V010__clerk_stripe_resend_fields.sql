ALTER TABLE users ADD COLUMN IF NOT EXISTS clerk_user_id VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_clerk_user_id ON users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_url VARCHAR(500);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS email_sent_at TIMESTAMP;

ALTER TABLE shipping_orders ADD COLUMN IF NOT EXISTS stripe_checkout_session_id VARCHAR(200);
ALTER TABLE shipping_orders ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(200);
ALTER TABLE shipping_orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
ALTER TABLE shipping_orders ADD COLUMN IF NOT EXISTS confirmation_email_sent_at TIMESTAMP;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_checkout_session_id VARCHAR(200);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(200);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_email_sent_at TIMESTAMP;

ALTER TABLE donations ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(200);
ALTER TABLE donations ADD COLUMN IF NOT EXISTS receipt_url VARCHAR(500);
ALTER TABLE donations ADD COLUMN IF NOT EXISTS confirmation_email_sent_at TIMESTAMP;

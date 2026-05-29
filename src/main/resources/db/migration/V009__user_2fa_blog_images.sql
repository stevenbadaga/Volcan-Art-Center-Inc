-- Two-factor authentication fields on users
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(128);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Additional images per blog post (gallery beyond featured image)
CREATE TABLE IF NOT EXISTS blog_post_images (
    post_id BIGINT NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_blog_post_images_post_id ON blog_post_images(post_id);

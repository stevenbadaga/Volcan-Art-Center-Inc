# Clerk + Resend Setup Guide

This guide shows the exact step-by-step setup needed for Clerk authentication and Resend email delivery in the Volcano Arts Center backend.

## 1. Add the required environment variables

Set these in your deployment platform, IDE run configuration, or shell session:

```env
APP_BASE_URL=https://your-domain.com

CLERK_PUBLISHABLE_KEY=pk_...
CLERK_SECRET_KEY=sk_...
CLERK_WEBHOOK_SECRET=whsec_...
CLERK_ISSUER=https://your-app.clerk.accounts.dev

RESEND_API_KEY=re_...
RESEND_FROM_EMAIL=noreply@your-verified-domain.com
MAIL_FROM=noreply@your-verified-domain.com

EMAIL_NOTIFICATIONS_ENABLED=true
```

Important:

- The local Spring profile now imports `.env` automatically with `spring.config.import: optional:file:.env[.properties]`.
- If you run a non-local profile or a custom launcher, put the values in the environment used to start the app.
- `MAIL_FROM` is used as a fallback, but `RESEND_FROM_EMAIL` is the preferred value.

## 2. Set up Clerk

### Step 1: Create or open your Clerk application

In the Clerk dashboard, create the app that will serve this site.

### Step 2: Enable sign-in methods

Enable the authentication methods the site uses:

- Email and password sign-in
- Password reset by email

If you want MFA, enable it only if you also plan to support the full flow in the frontend.

### Step 3: Copy the Clerk keys

From Clerk, copy:

- Publishable key to `CLERK_PUBLISHABLE_KEY`
- Secret key to `CLERK_SECRET_KEY`

### Step 4: Set the issuer URL

Copy your Clerk issuer URL to `CLERK_ISSUER`.

Example:

```env
CLERK_ISSUER=https://your-app.clerk.accounts.dev
```

This backend uses that issuer to verify the JWT in the Clerk session exchange endpoint.

### Step 5: Add the webhook endpoint

Create a Clerk webhook with this URL:

```text
https://your-domain.com/api/v1/auth/clerk/webhook
```

Subscribe it to:

- `user.created`
- `user.updated`
- `user.deleted`

Then copy the webhook signing secret into:

```env
CLERK_WEBHOOK_SECRET=...
```

### Step 6: Make sure the token includes an email

The backend expects the Clerk JWT to contain either:

- `email`
- or `email_address`

If your Clerk token template does not include one of those, login session exchange will fail.

### Step 7: Add allowed origins / redirects

In Clerk, add your app domain to the allowed origins and redirect URLs so the hosted Clerk UI and browser session flow can complete correctly.

### Step 8: Configure roles if you use admin access

The backend syncs user roles from Clerk public metadata. If you use role-based access, store them in a structure like:

```json
{
  "roles": ["REGISTERED_CLIENT"]
}
```

Common role values used by the app include:

- `SUPER_ADMIN`
- `CONTENT_MANAGER`
- `OPS_MANAGER`
- `REGISTERED_CLIENT`
- `TOUR_OPERATOR`
- `TALENT_APPLICANT`

## 2b. Google login through Clerk

The Google button on the sign-in page now uses Clerk SSO. You do not need separate `GOOGLE_OAUTH_*` environment variables for that button.

If the Clerk dashboard has Google enabled under SSO connections and the app has the Clerk publishable key, secret key, issuer, and webhook secret configured, the Google flow should start from the login and register pages.

## 3. Set up Resend

### Step 1: Create or open your Resend account

Open the Resend dashboard and create an account if needed.

### Step 2: Verify your sending domain

Verify the domain you will send from. This is required for stable production sending.

### Step 3: Create an API key

Create a Resend API key and save it as:

```env
RESEND_API_KEY=re_...
```

### Step 4: Choose the sender address

Use a sender address from the verified domain and set it in:

```env
RESEND_FROM_EMAIL=noreply@your-verified-domain.com
```

You can also set:

```env
MAIL_FROM=noreply@your-verified-domain.com
```

`RESEND_FROM_EMAIL` takes priority when both are present.

### Step 5: Enable email notifications

Keep this on unless you explicitly want email sending disabled:

```env
EMAIL_NOTIFICATIONS_ENABLED=true
```

## 4. Restart the application

After setting the variables, restart the backend so Spring Boot reads the new environment.

If you are running locally, make sure the process that launches the app can actually see those variables.
When the local profile starts, it also logs a masked integration diagnostics line with yes/no presence checks.

## 5. Verify the flows

Test these actions after startup:

- Open the Clerk login page
- Sign in with Clerk
- Create a new account
- Reset a password
- Trigger an order email
- Trigger a booking email
- Trigger a donation email
- Trigger a contact inquiry email
- Trigger a talent application email

## 6. If something still fails

Check these first:

- `CLERK_PUBLISHABLE_KEY` is not empty
- `CLERK_SECRET_KEY` is not empty
- `CLERK_WEBHOOK_SECRET` matches the Clerk webhook
- `CLERK_ISSUER` matches the real Clerk issuer URL
- `RESEND_API_KEY` is present
- `RESEND_FROM_EMAIL` uses a verified Resend domain
- the app was restarted after the env vars were added

## 7. Code paths that depend on these values

- Clerk login and session exchange: `src/main/java/com/volcanoartscenter/platform/web/external/api/ClerkAuthController.java`
- Clerk user creation/sync: `src/main/java/com/volcanoartscenter/platform/security/clerk/ClerkBackendClient.java`
- Resend email sending: `src/main/java/com/volcanoartscenter/platform/shared/service/integration/impl/EmailMessagingService.java`
- Email notifications: `src/main/java/com/volcanoartscenter/platform/shared/email/TransactionalEmailService.java`
- Environment wiring: `src/main/resources/application.yml`

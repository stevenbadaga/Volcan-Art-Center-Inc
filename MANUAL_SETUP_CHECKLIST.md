# Manual Setup Checklist

Use this checklist after deploying or running the Volcano Arts Center system locally. These are the steps that must be done outside the codebase.

## 1. Environment Variables

Add these variables to your local `.env`, Railway/service environment, or production host:

```env
APP_BASE_URL=

CLERK_PUBLISHABLE_KEY=
CLERK_SECRET_KEY=
CLERK_WEBHOOK_SECRET=
CLERK_ISSUER=

STRIPE_SECRET_KEY=
STRIPE_PUBLISHABLE_KEY=
STRIPE_WEBHOOK_SECRET=

RESEND_API_KEY=
RESEND_FROM_EMAIL=
```

Recommended values:

- `APP_BASE_URL`: your public app URL, for example `https://volcanoartscenter.rw`
- `CLERK_ISSUER`: your Clerk issuer URL, for example `https://your-app.clerk.accounts.dev`
- `RESEND_FROM_EMAIL`: a verified sender email from Resend

Do not commit real secret values to Git.

## 2. Clerk Dashboard

In Clerk:

- Enable email/password authentication.
- Enable password reset by email.
- Enable MFA if you want MFA available for users.
- Add your app URL to allowed origins/redirects.
- Create these roles in user public metadata as needed:
  - `SUPER_ADMIN`
  - `CONTENT_MANAGER`
  - `OPS_MANAGER`
  - `REGISTERED_CLIENT`
  - `TOUR_OPERATOR`
  - `TALENT_APPLICANT`

Role metadata format:

```json
{
  "roles": ["REGISTERED_CLIENT"]
}
```

For admin users, manually assign the correct role in Clerk public metadata.

## 3. Clerk Webhook

Create a Clerk webhook endpoint:

```text
${APP_BASE_URL}/api/v1/auth/clerk/webhook
```

Subscribe to:

- `user.created`
- `user.updated`
- `user.deleted`

Copy the Clerk webhook signing secret into:

```env
CLERK_WEBHOOK_SECRET=
```

## 4. Stripe Dashboard

In Stripe:

- Use live keys only for production.
- Use test keys for local/staging testing.
- Enable payment methods you want to accept.
- Confirm your default currency/payment methods are correct.

Add Stripe keys:

```env
STRIPE_SECRET_KEY=
STRIPE_PUBLISHABLE_KEY=
```

## 5. Stripe Webhook

Create a Stripe webhook endpoint:

```text
${APP_BASE_URL}/api/v1/webhooks/stripe
```

Subscribe to:

- `payment_intent.succeeded`
- `payment_intent.payment_failed`
- `checkout.session.completed`
- `invoice.paid`
- `invoice.payment_succeeded`

Copy the Stripe webhook signing secret into:

```env
STRIPE_WEBHOOK_SECRET=
```

## 6. Resend Dashboard

In Resend:

- Verify your sending domain.
- Create an API key.
- Choose a verified sender email.

Add:

```env
RESEND_API_KEY=
RESEND_FROM_EMAIL=
```

## 7. Database Migration

Before production use, make sure Flyway runs the new migration:

```text
V010__clerk_stripe_resend_fields.sql
```

This adds Clerk IDs, Stripe payment fields, paid timestamps, and email tracking fields.

## 8. Local Testing

Run:

```powershell
.\mvnw.cmd -q test
```

Then test these flows manually:

- Login with Clerk.
- Signup with Clerk.
- Password reset with Clerk.
- Admin dashboard access with Clerk role metadata.
- Art checkout payment.
- Experience booking payment.
- Conservation donation payment.
- Stripe webhook payment success.
- Stripe webhook payment failure.
- Resend order confirmation email.
- Resend booking confirmation email.
- Resend donation confirmation email.
- Contact inquiry admin notification.
- Talent application confirmation/notification.

## 9. Production Checks

Before going live:

- Confirm no real secrets are committed.
- Confirm webhook URLs use the production domain.
- Confirm Clerk user roles are assigned correctly.
- Confirm Stripe is in live mode.
- Confirm Resend sender/domain is verified.
- Confirm admin users can access `/admin/**`.
- Confirm normal users cannot access admin routes.
- Confirm payment success updates orders/bookings/donations correctly.
- Confirm duplicate Stripe webhook retries do not duplicate confirmation emails.


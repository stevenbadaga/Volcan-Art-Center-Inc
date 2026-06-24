# Volcano Arts Center

This repository contains the Volcano Arts Center / Musanze platform.

## Clerk and Resend local environment setup

Use the project root `.env` file as the local copy/paste target for your keys and test values.

Important:

- The local Spring profile now imports `.env` automatically through `spring.config.import: optional:file:.env[.properties]`.
- If you run a non-local profile or a custom launcher, you still need to provide the same variables through the process environment or your IDE run configuration.
- `.env.example` is only a template. Do not paste real keys into it.
- Do not commit real secrets.

Exact variables to paste locally:

- `CLERK_PUBLISHABLE_KEY`
- `CLERK_SECRET_KEY`
- `CLERK_ISSUER`
- `CLERK_WEBHOOK_SECRET`
- `RESEND_API_KEY`
- `RESEND_FROM_EMAIL`
- `EMAIL_NOTIFICATIONS_ENABLED`
- `ADMIN_NOTIFICATION_EMAIL`

Temporary testing value:

- `ADMIN_NOTIFICATION_EMAIL=badagaclass@gmail.com`

Notes:

- `CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY` come from the Clerk API keys page.
- `CLERK_ISSUER` should be the Clerk instance / frontend API URL, not `https://api.clerk.com`.
- `CLERK_WEBHOOK_SECRET` comes from the Clerk webhook endpoint signing secret.
- `RESEND_API_KEY` comes from the Resend API keys page.
- `RESEND_FROM_EMAIL` must be a verified Resend sender or verified domain email.
- `badagaclass@gmail.com` is only for temporary admin testing and should be restored after testing.
- Google login is handled through Clerk SSO in this codebase, so you do not need separate `GOOGLE_OAUTH_*` variables for the login button.

If you want to launch locally from PowerShell, you can still override values in the current session, for example:

```powershell
$env:CLERK_PUBLISHABLE_KEY="..."
$env:CLERK_SECRET_KEY="..."
$env:CLERK_ISSUER="https://your-app.clerk.accounts.dev"
$env:CLERK_WEBHOOK_SECRET="..."
$env:RESEND_API_KEY="..."
$env:RESEND_FROM_EMAIL="noreply@your-verified-domain.com"
$env:EMAIL_NOTIFICATIONS_ENABLED="true"
$env:ADMIN_NOTIFICATION_EMAIL="badagaclass@gmail.com"
```

After testing, restore the real admin notification address and keep the Resend sender on a verified domain.

When the app starts in the local profile, it also logs a masked integration summary so you can confirm which values are present without exposing secrets.

## Temporary Testing Email Overrides

Use this section when you want to test admin notifications and related email flows with a temporary inbox.

Step by step:

1. Set `ADMIN_NOTIFICATION_EMAIL` to `badagaclass@gmail.com` in your local runtime environment.
2. Restart the app.
3. Trigger a contact, booking, order, donation, or talent submission.
4. Confirm the admin notification lands in the temporary inbox.
5. Restore the original value after testing.

| Feature / flow | Original email value | Temporary testing email | Config/file | Restore note |
|---|---|---|---|---|
| Admin notifications (contact, booking, order, donation, talent) | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` in `.env` and runtime env | Restore the real admin inbox after testing |
| Contact inquiry admin recipient | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` / `SITE_CONTACT_EMAIL` | Restore the original recipient before production deploy |
| Booking admin recipient | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` | Restore the original recipient before production deploy |
| Shop / order admin recipient | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` | Restore the original recipient before production deploy |
| Donation admin recipient | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` | Restore the original recipient before production deploy |
| Talent application admin recipient | Original value comes from env and was not exposed | badagaclass@gmail.com | `ADMIN_NOTIFICATION_EMAIL` | Restore the original recipient before production deploy |
| Resend sender/from address | `noreply@volcanoartsandhospes.com` | Leave unchanged | `RESEND_FROM_EMAIL` / `MAIL_FROM` | Keep the sender on the verified Resend domain |
| Public contact email shown in the footer | `hello@volcanoartsandhospes.com` | Leave unchanged | `SITE_CONTACT_EMAIL` and `platform.site.contact-email` fallback | Restore only if you intentionally change the public contact address |
| Clerk / Google login / password reset | Not a recipient override in this repo | Not applicable | Clerk dashboard + `CLERK_*` env vars | Restore Clerk settings after testing if changed |
| Newsletter subscribers | No newsletter capture flow is currently wired | Not applicable | No newsletter feature found in code | Implement the feature first, then add a test recipient if needed |
| Local admin account testing | `admin1@volcanoartscenter.rw`, `admin2@volcanoartscenter.rw`, `admin3@volcanoartscenter.rw` | Add `badagaclass@gmail.com` as a test admin if you need dashboard access with that inbox | Local DB seed / Clerk metadata / role assignment | Remove the temporary test admin account after testing |

### After testing, restore these values

- Replace `badagaclass@gmail.com` with the original admin email values listed above.
- Restore deployment environment variables in your hosting platform.
- Restore local environment variables in `.env`.
- Retest contact, booking, order, newsletter, donation, and talent notification flows.
- Confirm no testing email remains in production configuration.
- Keep `RESEND_FROM_EMAIL` on the verified Resend sender domain.
- If you added a temporary Clerk admin/test account, remove it or revert its role assignment after testing.

## Setup references

- Clerk + Resend setup guide: `CLERK_RESEND_SETUP_GUIDE.md`
- Manual setup checklist: `MANUAL_SETUP_CHECKLIST.md`
- Deployment notes: `DEPLOYMENT.md`

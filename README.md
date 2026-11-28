# Cadence — backend

Spring Boot 4 · Java · PostgreSQL. Multi-tenant academic-operations platform.

## Running locally

```bash
docker start cadence_db          # PostgreSQL (see docker/)
./mvnw spring-boot:run           # API on http://localhost:8080
./mvnw test                      # integration tests on in-memory H2
```

Configuration comes from environment variables (a local `.env` is fine). Schema changes that
`ddl-auto=update` can't make on its own (CHECK constraints) are in `docker/migrations/` — apply
them to every existing database.

### Seeded accounts

On startup, two accounts are created if they don't already exist. Once an account exists its password
isn't touched, so a password changed in the app stays changed.

| Variables | Account |
|---|---|
| `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD` | Owner (SUPER_ADMIN) of the seed institution, Semicolon Africa |
| `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD` | The Cadence platform admin: the platform console and every institution. Belongs to no institution. Skipped if the email is blank. |

---

## CI/CD

| Branch | What runs |
|---|---|
| `feature/*`, `fix/*`, `ci/*`, `develop`, PRs into `develop`/`main` | **CI** (`.github/workflows/ci.yml`): build, all tests, Docker image build |
| `main` | **Deploy** (`.github/workflows/deploy.yml`): tests again, then `railway up` |

`develop` is the integration branch; `main` only moves by a `develop → main` pull request, and every
push to it is a production deploy. The web app (`capstone_frontend`) follows the same rule and deploys
to Vercel.

### Railway setup (one-off)

1. Create a project with a **PostgreSQL** database and an empty service (e.g. `cadence-api`). Turn off
   the service's own GitHub auto-deploy — the workflow deploys it, only after tests pass.
2. In the service's variables set:

   | Variable | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<PGHOST>:<PGPORT>/<PGDATABASE>` from the database's variables |
   | `DB_USERNAME`, `DB_PASSWORD` | the database's `PGUSER` / `PGPASSWORD` |
   | `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM` | the PEM text of a fresh RSA key pair (not your local one) |
   | `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` | the Vercel URL, e.g. `https://cadence.vercel.app` |
   | `WEBAUTHN_RP_ID`, `WEBAUTHN_ORIGIN` | the web app's domain (`cadence.vercel.app`) and origin (`https://cadence.vercel.app`) |
   | `SUPER_ADMIN_*`, `PLATFORM_ADMIN_*`, `EMAIL_*`, `CLOUDINARY_*` | as in `.env` |

   Railway sets `PORT`; the app listens on it.
3. Apply `docker/migrations/*.sql` to the new database once, in date order.
4. Create a **project token** for the production environment. In GitHub → Settings → Secrets and
   variables → Actions add secret `RAILWAY_TOKEN` and variable `RAILWAY_SERVICE` (the service name).

Until those are set, the deploy job passes with a warning and deploys nothing.

---

## Notifications

PRD Section 12. A producing service publishes a **domain event** once; independent listeners fan it
out. Producers never know about notifications — adding a channel means adding a listener.

```
service ── DomainEvents.publish(event) ──► (after commit, async) NotificationListener
                                                      └─► NotificationService.deliver
                                                             ├─ in-app rows (batch insert, deduped)
                                                             ├─ email, whitelisted types only
                                                             └─ Web Push to the recipient's browsers (if configured)
```

- **Events** live in `common.event` and extend `DomainEvent` (event id, time, institution, optional actor).
  The actor is optional on purpose — anonymous feedback must never carry one (PRD 18.6).
- **Listeners** use `@TransactionalEventListener(AFTER_COMMIT)` on the bounded `eventExecutor`, so a
  rolled-back change emits nothing and a listener can't slow or roll back the request. Failures are
  logged at ERROR.
- **Recipients**: only people with an active membership in that institution and an undeleted account;
  never the person who caused the event. Removal from one institution doesn't affect another.
- **Idempotent**: one row per (event, recipient) via a unique `dedupe_key`.
- **Batch fan-out**: rows go in with one JDBC batch insert (`ON CONFLICT DO NOTHING`) — the path an
  institution-wide broadcast (PRD 18.9) will use.
- **Tenant-scoped**: each notification belongs to one institution; a user sees an institution's
  notifications (and unread count) only while working in it.
- **API** (`/api/notifications`): list (paged, newest first), `/unread-count`, `PATCH /{publicId}/read`,
  `POST /read-all`. Someone else's notification is "not found". The web app polls; no WebSockets (PRD 4.3).
- **Retention**: `NotificationCleanupJob` deletes *read* notifications 180 days after reading, daily at
  03:30. Unread ones are kept. Notifications are not audit data; `AuditLog` is separate.

### Event catalogue

| Event | Notifies | Status |
|---|---|---|
| `AttendanceOverriddenEvent` | the student | **wired** (class + resumption overrides) — also emailed by default |
| `StudentCohortChangedEvent` | the student | **wired** (admin enrols / moves a student) |
| `InviteAcceptedEvent` | the inviter | **wired** (staff, co-owner and student invites) |
| Assignment published / due soon / closed | cohort students | later stage |
| `SubmissionSentBackEvent` | the student | **wired** (work sent back to redo; shown at once) — also emailed by default |
| `GradesReleasedEvent` | each reviewed student | **wired** (facilitator releases an assignment's grades; or one student accepted after release) |
| `CoursePublishedEvent` | the course's audience (its cohorts, or every student) | **wired** (a course is published, now or on schedule) |
| Extension approved / denied | the student | later stage |
| Extension requested | the facilitator | later stage |
| Check-in awaiting biometric (still pending after N min) | the student | later stage |
| Attendance pattern flag raised | facilitator / admins with the permission | later stage (needs flags) |
| Feedback received | facilitator / admins with `VIEW_FEEDBACK` — **no actor** | later stage (needs feedback) |
| Dispute opened / resolved | the parties | later stage (needs disputes) |
| Institution-wide broadcast | every active member | later stage (uses the batch fan-out) |

Adding one: create the event in `common.event`, publish it from the service after the change, add a
`NotificationType` and a handler in `NotificationListener`.


### Push notifications (installable app)

Every newly written in-app notification is also sent as a **Web Push** message to each browser the
recipient turned push on for (the "Push notifications" switch on the web app's Notifications page;
on iPhone/iPad only from the Home Screen app, iOS 16.4+). It's extra, like email: asynchronous on the
`eventExecutor`, best-effort, and never affects in-app delivery.

- **Protocol**: RFC 8291 message encryption (`aes128gcm`) + RFC 8292 VAPID, implemented on the JDK
  alone in `WebPushCrypto` (ECDH P-256, HKDF-SHA256, AES-128-GCM, ES256) — no BouncyCastle or
  web-push library. It's checked against the RFC 8291 Appendix A test vector.
- **Payload**: `{ title, body, url, type }`, where `url` is `FRONTEND_URL` + the notification's link.
- **API** (`/api/push`, signed in): `GET /public-key` → `{ enabled, publicKey }`;
  `POST /subscriptions` with the browser's `PushSubscription.toJSON()`; `DELETE /subscriptions`
  with `{ endpoint }` (only your own; idempotent). A subscription belongs to the user, not an
  institution; the same browser subscribing as another account moves to that account. Up to 10
  devices per user (the oldest is dropped). Signing out of the web app removes that browser's subscription.
- **Cleanup**: a subscription the push service answers 404/410 for is deleted; successes stamp `last_success_at`.
- **Endpoints** must be `https` on a known push service (`PUSH_ALLOWED_HOSTS`), so the server can't
  be pointed at arbitrary URLs.
- **Off by default**: without all three VAPID variables the app starts normally, `GET /public-key`
  says `enabled: false`, subscribing is refused (409) and nothing is sent. A malformed or mismatched
  key pair logs a WARN at startup and also leaves push off.

| Variable | Notes |
|---|---|
| `VAPID_PUBLIC_KEY` | base64url, 65-byte uncompressed P-256 point (starts with `B`, 87 chars) |
| `VAPID_PRIVATE_KEY` | base64url, 32-byte private scalar (43 chars). **Secret** — Railway only, never in the repo or the frontend |
| `VAPID_SUBJECT` | a contact for push services: `mailto:you@example.com` or an `https://` URL |
| `PUSH_ALLOWED_HOSTS` | optional; default `fcm.googleapis.com,android.googleapis.com,push.services.mozilla.com,notify.windows.com,push.apple.com` (suffix match) |
| `PUSH_TTL_SECONDS` | optional; how long a push service holds an undelivered message, default `86400` |
| `FRONTEND_URL` | already set; the base of each notification's link |

Set these on the **Railway** API service. Nothing new is needed on **Vercel**: the web app fetches
the public key from the API.

**Generating a VAPID key pair** (once per environment; keep the same pair afterwards — changing it
invalidates every existing subscription, and users must turn push on again) with OpenSSL:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out vapid.pem
# VAPID_PRIVATE_KEY
openssl ec -in vapid.pem -outform DER 2>/dev/null | tail -c +8 | head -c 32 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
# VAPID_PUBLIC_KEY
openssl ec -in vapid.pem -pubout -outform DER 2>/dev/null | tail -c 65 | base64 | tr '/+' '_-' | tr -d '=\n'; echo
rm vapid.pem
```

Any standard Web Push tool's key generator gives the same format (e.g. the `web-push` npm package's
`generate-vapid-keys`). If the pair doesn't match, the startup log says so and push stays off.

---

## Email

`EmailService.send(...)` is the single entry point (verification, password reset, invites, invite
codes, notification email). The transport is chosen by **`EMAIL_PROVIDER`**; swapping provider is a
config change only.

| `EMAIL_PROVIDER` | Sends via | Use for |
|---|---|---|
| `log` *(default)* | nothing — logs the message and its links | local development |
| `smtp` | `JavaMailSender` | local/demo, hosts that allow outbound SMTP |
| `brevo-api` | Brevo HTTPS API | deployment |
| `resend-api` | Resend HTTPS API | deployment |
| `mailjet-api` | Mailjet Send API v3.1 (HTTPS) | deployment |

Sending is asynchronous with 5-second connect/read timeouts and up to 3 attempts for errors that may
pass (timeouts, 5xx, 429); a bad key or rejected address isn't retried. Failures are logged with the
provider name, never credentials, and never reach the user. Only the `log` provider writes email bodies
to the logs — real emails contain sign-in and reset links.

A provider selected without what it needs **falls back to `log` with a loud startup WARN** ("NO EMAIL
WILL BE SENT") rather than dropping mail silently.

### Environment variables

| Variable | Needed by | Notes |
|---|---|---|
| `EMAIL_PROVIDER` | all | `log`, `smtp`, `brevo-api`, `resend-api`, `mailjet-api` |
| `EMAIL_FROM_ADDRESS` | brevo-api, resend-api, mailjet-api (smtp: optional) | a sender on a domain you've verified with the provider |
| `EMAIL_FROM_NAME` | optional | default `Cadence` |
| `EMAIL_API_KEY` | brevo-api, resend-api, mailjet-api | from the provider dashboard (Mailjet: the API key) |
| `EMAIL_API_SECRET` | mailjet-api | Mailjet's secret key (its API uses a key pair) |
| `EMAIL_API_BASE_URL` | optional | override the API host (tests use a local fake) |
| `EMAIL_TIMEOUT_MS` | optional | default `5000` |
| `EMAIL_DAILY_CAP` | optional | default `280` — see below |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | smtp | default host `smtp.gmail.com:587` (STARTTLS) |
| `NOTIFICATION_EMAIL_TYPES` | optional | notification types also emailed; default `ATTENDANCE_OVERRIDDEN,SUBMISSION_SENT_BACK` |

**Daily cap.** Free tiers cap sends per day, and account emails already use that allowance. Near
`EMAIL_DAILY_CAP` the app logs a WARN; at the cap, notification emails are skipped (in-app delivery
continues) while account emails are still attempted. Set the cap a little under your plan's limit.

### Choosing a provider

Free tiers as checked in October 2026 — **re-verify before relying on them**:

- **Brevo** — 300/day, SMTP and API, no card; free-plan emails carry Brevo branding.
- **Mailjet** — 6,000/month, 200/day cap.
- **Resend** — 3,000/month, 100/day cap, one domain on the free plan.
- **Mailgun** — 100/day, card required.
- SendGrid's free plan is gone (60-day trial); Amazon SES no longer gives new accounts a free allowance.
- **Gmail SMTP** — about 500 recipients/day on a personal account (~2,000 on Workspace), counted per
  recipient, needs an app password, may throttle sudden spikes, and every email comes from that Gmail
  address. Use a dedicated account for dev/demo, **never a personal one**.

### Gmail SMTP (dev / demo)

1. Create a dedicated Gmail account and turn on 2-Step Verification.
2. Google Account → Security → App passwords → create one for "Mail".
3. Set `EMAIL_PROVIDER=smtp`, `MAIL_USERNAME=<the address>`, `MAIL_PASSWORD=<the app password>`.

### Brevo or Resend (deployment)

1. Create an account and an API key (Brevo: SMTP & API → API keys; Resend: API Keys).
2. Add and verify your sending domain in the provider (see SPF/DKIM below).
3. Set `EMAIL_PROVIDER=brevo-api` (or `resend-api`), `EMAIL_API_KEY`, `EMAIL_FROM_ADDRESS=no-reply@yourdomain`.

### Mailjet (deployment)

1. Create an account; under **Account settings → API key management** copy the **API key** and the
   **secret key**.
2. Under **Senders & domains**, add your domain and set up SPF/DKIM (below), or at least validate the
   single sender address you'll send from. Mail from an unvalidated sender is refused — Mailjet reports
   it inside a 200 response, which Cadence treats as a failed send and logs.
3. Set `EMAIL_PROVIDER=mailjet-api`, `EMAIL_API_KEY=<API key>`, `EMAIL_API_SECRET=<secret key>`,
   `EMAIL_FROM_ADDRESS=no-reply@yourdomain`, and `EMAIL_DAILY_CAP=190` on the free plan (200/day).

Mailjet also offers an SMTP relay: on a host that allows outbound SMTP you can use `EMAIL_PROVIDER=smtp`
instead, with the relay host and port from Mailjet's dashboard, the API key as `MAIL_USERNAME` and the
secret key as `MAIL_PASSWORD`. Free-plan emails carry the Mailjet logo.

### SPF and DKIM (sending from your own domain)

Mail from your own domain is only trusted when DNS says the provider may send for it.

1. In the provider's dashboard, add the domain (e.g. `yourdomain.com` or a subdomain such as
   `mail.yourdomain.com`). It shows the exact DNS records to create — **copy those values; don't guess
   them**, they're specific to your account.
2. At your DNS host, add the **DKIM** record(s) — usually a TXT or CNAME at a `…._domainkey` name.
3. Add or update the **SPF** TXT record on the sending domain to include the provider (the dashboard
   gives the `include:` value). A domain has **one** SPF record: merge includes into it
   (`v=spf1 include:<provider> include:<other> ~all`) rather than adding a second record.
4. Recommended: a **DMARC** TXT record at `_dmarc.yourdomain.com`, starting with `v=DMARC1; p=none;`
   and tightening once mail is flowing.
5. Wait for DNS to propagate, then click Verify in the provider. Send a test and check the headers show
   `spf=pass` and `dkim=pass`.

### Hosting

- The web app is expected on **Vercel**. This backend — long-running, with scheduled jobs and
  PostgreSQL — can't run on Vercel's serverless model and needs a separate host.
- **Many hosts block outbound SMTP (ports 465/587) on free plans.** Railway does on Free, Trial and Hobby
  and recommends an HTTPS email API instead. On such hosts use `brevo-api` or `resend-api`; with SMTP a
  blocked port shows up as connection timeouts in the logs (it fails within 5 seconds and the user is
  never kept waiting). Check your host's SMTP policy before choosing `smtp` in production.

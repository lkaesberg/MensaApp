# Automated Play Store releases

`.github/workflows/release-play-store.yml` builds a signed AAB and uploads it to the
Play **production** track every time a GitHub Release is published.

- `versionName` comes from the release tag (`v2.8` → `2.8`)
- `versionCode` is `git rev-list --count HEAD` (the total commit count), so it always increases
- Release notes come from the GitHub release body (truncated to Play's 500-character limit)

Local builds are unaffected: `composeApp/build.gradle.kts` falls back to the hardcoded
`versionCode`/`versionName` and skips the signing config when `MENSA_KEYSTORE_FILE` is unset.

## One-time setup

### 1. Google Cloud service account

1. Open <https://console.cloud.google.com/> and create a project (or reuse one).
2. Enable the **Google Play Android Developer API**:
   APIs & Services → Library → search for it → Enable.
3. IAM & Admin → Service Accounts → **Create service account**
   (name e.g. `github-play-publisher`, no roles needed at the GCP level).
4. Open the new account → **Keys** → Add key → Create new key → **JSON**. A file downloads.
   Keep it safe; it cannot be re-downloaded.

### 2. Grant it access in Play Console

1. Open <https://play.google.com/console/> → **Users and permissions** → **Invite new users**.
2. Enter the service account's email (`…@….iam.gserviceaccount.com`).
3. Under **App permissions**, add the Mensa App.
4. Under **Account permissions**, grant at least:
   - View app information and download bulk reports
   - Create, edit, and delete draft apps
   - Release apps to testing tracks
   - Release to production, exclude devices, and use app signing
5. Invite the user. Permission propagation can take a few minutes to a few hours.

### 3. Base64-encode the upload keystore

This is the same keystore you already use to sign the AAB you upload by hand:

```bash
base64 -i /path/to/upload-keystore.jks | pbcopy
```

### 4. Add the GitHub secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
| --- | --- |
| `PLAY_KEYSTORE_BASE64` | output of the `base64` command above |
| `PLAY_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_KEY_ALIAS` | key alias inside the keystore |
| `PLAY_KEY_PASSWORD` | password for that key |
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the downloaded JSON key file |

## Releasing

```bash
git tag v2.8 && git push origin v2.8
```

Then publish a GitHub Release for that tag (or use `gh release create v2.8 --notes "…"`).
The workflow runs, builds the AAB, and pushes it to production.

`workflow_dispatch` is also wired up if you need to re-run a publish manually.

## Notes

- The very first upload for an app must be done manually in Play Console; the API cannot
  create the initial release. That is already done for this app.
- Play rejects a `versionCode` that is not higher than the last published one. The commit
  count is currently ahead of the old hardcoded value (15), so the first automated release
  jumps to ~47. That is fine — it only has to increase.

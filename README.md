This is a Kotlin Multiplatform project targeting Android, iOS, Web.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - `commonMain` is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).

You can open the web application by running the `:composeApp:wasmJsBrowserDevelopmentRun` Gradle task.

## Backend (Supabase)

The Supabase backend (DB schema, RLS, storage, project config, edge functions) is version-controlled under `supabase/`.

### Deploy changes

```bash
./scripts/supabase-deploy.sh
```

This pushes migrations (`supabase db push`) and deploys all edge functions (`supabase functions deploy`). It will prompt for the DB password unless you put `SUPABASE_DB_PASSWORD=...` in a local `.env` file (gitignored).

Project config (`supabase/config.toml`) is **not** pushed by default — running `supabase config push` against a default `config.toml` would overwrite your dashboard auth/storage settings. Once you've hand-edited `config.toml` to match your dashboard, opt in with `SUPABASE_PUSH_CONFIG=1 ./scripts/supabase-deploy.sh`.

### Add a new migration

```bash
supabase migration new <name>      # creates supabase/migrations/<timestamp>_<name>.sql
# edit the generated SQL file
./scripts/supabase-deploy.sh
```

### Add or edit an edge function

Edit files under `supabase/functions/<name>/`, then run the deploy script. To scaffold a new function: `supabase functions new <name>`.

### One-time bootstrap (already done; documented for reference)

```bash
brew install supabase/tap/supabase
supabase login
supabase init
supabase link --project-ref odmhuzmqkvryyommkzfs
supabase db pull              # snapshot remote schema as initial migration
supabase functions list       # then `supabase functions download <name>` for each
# Note: there is no `supabase config pull`. Edit supabase/config.toml manually
# to match your dashboard settings before ever running `supabase config push`.
```
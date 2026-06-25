# DiffGuard

English | [简体中文](README.zh-CN.md)

DiffGuard is an IntelliJ IDEA plugin that reviews selected Git changes before commit with a user-configured OpenAI-compatible API.

## Features

- Adds a `Review with DiffGuard` action to Commit/VCS entry points.
- Reviews selected Git changes and generates unified diff content.
- Calls an OpenAI-compatible Chat Completions API configured by the user.
- Displays findings in the `DiffGuard` tool window.
- Parses plain JSON, fenced JSON in Markdown, and JSON arrays embedded in text.
- Shows raw fallback content as a LOW-level finding when the AI response cannot be parsed.
- Opens the related file and line when a finding card is clicked.
- Supports workspace guidance and ignore patterns for project-specific review focus.

## Configuration

Open `Settings / Tools / DiffGuard` and configure:

- `Base URL`: OpenAI-compatible API endpoint. Default: `https://api.openai.com/v1`.
- `API Key`: stored in IntelliJ PasswordSafe. The settings page masks saved keys.
- `Model`: model name. Default: `gpt-4o-mini`.

## Privacy And Data

DiffGuard does not operate a hosted review service and does not bundle API keys into the plugin package.

- API keys are stored in IntelliJ PasswordSafe.
- Non-secret settings are stored in IDE application-level settings.
- During review, the plugin sends selected unified diff content, available code context, and workspace guidance to the configured `Base URL`.
- The plugin does not send data to servers controlled by the DiffGuard author unless the user explicitly configures such an endpoint.
- Users should configure only providers they trust and confirm that sending code snippets to that provider is allowed by their organization.

## Usage

1. Select or stage the Git changes you want to review.
2. Click `Review with DiffGuard` from the Commit/VCS entry points.
3. The plugin opens the `DiffGuard` tool window and shows review progress.
4. Review results are displayed by severity, file, line number, and message.

## Local Development

Run the plugin in a development IDE:

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew runIde
```

Run tests:

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew test
```

Build the plugin:

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew buildPlugin
```

The plugin archive is generated in:

```text
build/distributions/
```

## Publishing To JetBrains Marketplace

For the first release, upload `build/distributions/*.zip` manually in JetBrains Marketplace. Later releases can use the Gradle `publishPlugin` task.

Recommended checks before publishing:

```bash
./gradlew test
./gradlew verifyPluginStructure
./gradlew buildPlugin
```

Plugin signing uses environment variables. Do not commit certificates, private keys, or tokens:

```bash
export JETBRAINS_CERTIFICATE_CHAIN="-----BEGIN CERTIFICATE-----..."
export JETBRAINS_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----..."
export JETBRAINS_PRIVATE_KEY_PASSWORD="..."
./gradlew signPlugin
```

Publishing requires a JetBrains Marketplace token:

```bash
export JETBRAINS_MARKETPLACE_TOKEN="perm:..."
./gradlew publishPlugin
```

Release configuration is defined in `gradle.properties` and `build.gradle.kts`:

- `pluginId=dev.diffguard`
- `pluginVersion=0.1.0`
- `pluginSinceBuild=241`
- `pluginUntilBuild=253.*`
- `pluginPublishChannel=default`

Increment `pluginVersion` before uploading each new version.

Fill real homepage, source code, and support links on the JetBrains Marketplace plugin page. Do not commit placeholder links.

### Marketplace Copy

Short description:

```text
AI code review for selected Git changes before commit, powered by your OpenAI-compatible provider.
```

Privacy summary:

```text
DiffGuard sends selected Git diffs and optional local context only to the OpenAI-compatible API endpoint configured by the user. API keys are stored in IntelliJ PasswordSafe and are not bundled with the plugin.
```

## Not Included

- Automatic code fixes
- Agent workflows
- Vector databases
- Multi-turn chat
- Local models
- User accounts
- Hosted configuration service

## License

DiffGuard is licensed under the [MIT License](LICENSE).

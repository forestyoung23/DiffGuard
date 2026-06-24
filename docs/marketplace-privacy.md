# DiffGuard Privacy Notice

DiffGuard is an IntelliJ IDEA plugin that reviews selected Git changes before commit using an OpenAI-compatible API endpoint configured by the user.

## Data Sent to the Provider

When the user starts a review, DiffGuard may send the following data to the configured `Base URL`:

- selected unified Git diff content;
- relevant local code context extracted by the plugin;
- workspace guidance files and ignore patterns, when configured;
- model and request parameters needed to call the provider.

DiffGuard sends this data only to the endpoint configured by the user in `Settings / Tools / DiffGuard`.

## API Key Storage

API keys are stored through IntelliJ PasswordSafe. They are not stored in the plugin source code, not written into the plugin ZIP, and not bundled during plugin packaging.

Uninstalling the plugin may not remove IntelliJ PasswordSafe entries or application-level settings. Users can remove saved credentials from the IDE or the operating system credential store.

## No DiffGuard Hosted Service

DiffGuard does not operate a hosted review service. The plugin author does not receive code, prompts, API keys, responses, telemetry, or usage data from the plugin unless the user explicitly configures a provider endpoint controlled by the author.

## User Responsibility

Users should configure only providers they trust and ensure that sending code snippets, diffs, and project context to that provider complies with their organization policies.

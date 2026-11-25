# Security Policy

The Dolil Onusondhan project takes the security and privacy of users' information seriously. Please use the guidance below when reporting a vulnerability or handling project data.

## Supported versions

Security fixes are generally evaluated for the latest published application release and the current development branch.

| Version | Security support |
|---|---|
| Latest published release | Preferred target for security reports |
| Current `main` branch | Reviewed for applicable fixes |
| Older releases | May require upgrading before a fix is available |

This table describes the project's current handling approach; it is not a guarantee of a fixed maintenance period for any release.

## Reporting a vulnerability

**Please do not disclose an unpatched vulnerability in a public GitHub issue, pull request, discussion, commit, or screenshot.**

### Preferred reporting channel

1. Open the repository's [Security tab](https://github.com/jubayerhossaindev/dolil-onushondan/security).
2. Use **Report a vulnerability** to submit a private vulnerability report when GitHub makes that feature available for this repository.
3. Include enough technical information for the maintainers to understand and reproduce the issue safely.

### If private vulnerability reporting is unavailable

Open a public [GitHub issue](https://github.com/jubayerhossaindev/dolil-onushondan/issues) containing only a request for a private reporting channel. Do not include exploit details, sensitive logs, credentials, personal information, or proof-of-concept payloads in that public issue.

You may also check the [repository owner's GitHub profile](https://github.com/jubayerhossaindev) for an available contact method.

## What to include

A useful report should contain, where applicable:

- A concise description of the vulnerability and its potential impact.
- The affected version, release tag, commit, or component.
- Android version and device / emulator details, if relevant.
- Clear reproduction steps and the expected versus actual behavior.
- A minimal proof of concept or redacted evidence.
- Any known prerequisites, permissions, or account configuration required.

Do not attach real deed records, grantor/grantee details, NID information, private office data, OAuth tokens, Firebase credentials, Drive access tokens, signing keys, or passwords. Redact logs and screenshots before sharing them.

## Scope and safe testing

Please limit security testing to systems, accounts, devices, and data that you own or are explicitly authorized to test.

Do not:

- Access, modify, or disclose another person's land records or personal information.
- Attempt to obtain credentials, tokens, or private backup contents belonging to another user.
- Bypass Google, Android, Firebase, or source-provider authentication and access controls.
- Perform denial-of-service, destructive, or disruptive testing against services.
- Use social engineering or physical attacks against project contributors or service providers.
- Publicly disclose a vulnerability before a fix or coordinated disclosure has been agreed.

The companion land-geographic pipeline must also follow its documented access-policy guardrails. It must not bypass robots directives, CAPTCHA, WAF restrictions, or collect private owner information.

## Response and disclosure

Maintainers will review reports as circumstances permit, assess their impact, and coordinate remediation and disclosure with the reporter when appropriate.

There is currently no published guaranteed response or remediation time. Please include a safe way to continue the conversation through the private reporting channel.

Once a fix is available, the project may publish a security advisory or release note describing the impact and remediation. Sensitive exploit details and user data should not be published.

## If a secret is exposed

If you accidentally commit a password, private key, signing credential, OAuth token, Firebase configuration that contains sensitive credentials, or Google Drive access token:

1. Revoke or rotate the exposed credential immediately using the provider's official console.
2. Remove the secret from active configuration and replace it with a secure local or CI secret.
3. Check whether the credential was used unexpectedly.
4. Report any resulting security concern privately.

Deleting a secret from the latest commit does not guarantee that it has been removed from Git history, forks, caches, or downloaded copies. Treat an exposed credential as compromised.

## Privacy reminder

Dolil Onusondhan is designed to organize deed information supplied by users. Treat all real deed records and party details as private data, including in development, test fixtures, crash reports, screenshots, backups, and support requests.

Thank you for helping protect the project and its users.

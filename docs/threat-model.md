# Initial threat model

## Current prototype

The current implementation scope is an Android launcher shell and local geometry preview. It has no network listener, pairing, stored device credential, remote command, clipboard, file-transfer, or notification-sync endpoint. Those capabilities must remain absent until the corresponding review and tests exist.

## Assets and boundaries for the planned connection

| Asset or boundary | Threat | Required control before implementation |
| --- | --- | --- |
| Pairing identity and device keys | Theft or impersonation | Android Keystore-backed storage, private Linux key files, explicit key revocation |
| Local network pairing | Spoofing, MITM, brute force | Authenticated encryption, short-lived one-time pairing secret, rate limits, user-verifiable identity |
| Desktop command execution | Unauthorized or destructive commands | Deny by default, typed allowlist, per-device permissions, confirmation, audit record |
| File transfer | Path traversal, overwrite, resource exhaustion | Destination allowlist, canonical path validation, size caps, temporary files, atomic replace |
| Clipboard and notifications | Private data disclosure | Per-feature opt-in, per-device scope, clear disclosure, revocation |
| Omarchy QML plugin | Malicious or compromised plugin code | Review source before installation; avoid shell interpolation; constrain process arguments and input size |

## Abuse cases

- A nearby device attempts to guess or reuse an expired pairing secret.
- A hostile access point substitutes a desktop identity during first pairing.
- A paired phone uses a stale or revoked token.
- A crafted filename escapes the approved share directory.
- A plugin receives malformed network JSON and evaluates it as a shell command.
- Repeated discovery or status requests drain phone or desktop battery.

The first transport implementation must write protocol tests for these cases before enabling any privileged operation.

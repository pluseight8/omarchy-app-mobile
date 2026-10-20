# Security policy

## Current scope

This prototype does not implement pairing, a network listener, remote commands, file transfer, clipboard sync, notification sync, or stored desktop credentials. Do not treat the current Omarchy widget as a secure connection.

## Reporting

Please report security issues through GitHub's private vulnerability reporting for this repository:

https://github.com/pluseight8/omarchy-app-mobile/security/advisories/new

Do not include credentials, signing keys, pairing secrets, or private user data in a public issue.

Remote-control capabilities will not be enabled until the protocol has per-device authorization, authenticated encryption, revocation, input validation, and tests for the abuse cases in docs/threat-model.md.

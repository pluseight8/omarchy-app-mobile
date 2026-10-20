# Pairing status

Device pairing is not implemented yet. The Devices page and Omarchy bar widget are status placeholders; neither discovers devices, listens on the network, stores keys, nor transfers data.

The initial threat model requires authenticated encryption, user-verifiable first pairing, rate limits, per-device capabilities, key revocation, and abuse-case tests before a network endpoint is enabled. Until that work is complete, do not expose remote commands, clipboard, notifications, or file transfer.

See [the threat model](threat-model.md) for the planned trust boundaries and abuse cases.

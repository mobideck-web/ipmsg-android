# Security and responsible reporting

## Release status

This is a beta release candidate, not an audited or physically accepted stable release. v0.7.3 has compilation/signature evidence. Latest camera/browser behavior and current Windows interoperability remain pending runtime acceptance.

## Boundaries

- Use trusted networks and approve intended peers. Compare public-key fingerprints through a trusted channel. Legacy plaintext peers remain unverified and require consent.
- Classic IPMsg encrypted files do not authenticate contents. Compatibility is not authenticated file-integrity protection.
- Camera HTTPS uses a local certificate and short-lived authenticated session. Compare the entire certificate fingerprint. Never disable certificate validation globally or install a general phone-controlled CA.
- The owner starts camera sharing. Built-in capture controls trigger alerts; independent OS/third-party recordings cannot be detected.
- History is local SQLite, not separately encrypted. Downloads follow Android/provider access. Keep device security controls enabled.

## Reporting

Do not put an exploit, camera token, private key, chat history or personal file in a public issue. If Security → Report a vulnerability is available, use that private channel. Otherwise open a minimal issue requesting a private reporting channel, without sensitive technical details. No response SLA is promised.

For ordinary bugs, use the bug template with versions and sanitized reproduction. Repository issues are public. Never include signing keys or credentials in contributions; private release signing remains outside this tree.

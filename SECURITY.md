# Security notes

This repository is a portfolio engineering snapshot. It is not a production service or a supported binary distribution.

## Threat model

Jarvis | AuthorAI combines model inference, Android Accessibility, and optional privileged Android operations. The design focuses on reducing risks from UI prompt injection, excessive privileges, credential exposure, unsafe privileged actions, and incorrect model decisions.

## Security boundaries

- The model does not receive Binder, Android Context, Shizuku, or a raw shell primitive directly.
- Executable actions are exposed through typed application tools.
- Tool execution passes through `PolicyEngine`.
- Privileged operations pass through `CommandPolicy`.
- Unknown privileged operations are classified as `CRITICAL` and denied.
- Password Accessibility nodes are masked before model use.
- API credentials are encrypted with AES-256-GCM using key material stored in Android Keystore.
- Secret-bearing preferences and local models are excluded from Android backup/device transfer.
- Cloud provider endpoints must use HTTPS.
- Medium- and high-risk operations require explicit user confirmation.
- Command runtime and returned output size are bounded.

## Portfolio scope

The snapshot intentionally keeps several areas small and auditable:

- Agent history is process-local rather than a durable task database.
- Local GGUF inference can run without a network request.
- There is no AuthorAI account backend, OAuth session, JWT layer, or remote control service.
- The policy engine is deterministic and intentionally easy to review.

## Security review focus

Key files for review:

- `app/src/main/java/top/authorche/agent/security/SecretStore.kt`
- `app/src/main/java/top/authorche/agent/agent/PolicyEngine.kt`
- `app/src/main/java/top/authorche/agent/shizuku/CommandPolicy.kt`
- `app/src/main/java/top/authorche/agent/shizuku/ShizukuBridge.kt`
- `app/src/main/java/top/authorche/agent/provider/AgentSettingsStore.kt`

See [docs/CASE_STUDY.md](docs/CASE_STUDY.md) for the architecture and trade-offs.

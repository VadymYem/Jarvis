# Engineering case study — Jarvis | AuthorAI

## Objective

The project explores a specific engineering question:

**How can a language-model agent interact with Android while keeping model output separated from operating-system privileges?**

A naive implementation can give a model raw shell access or let it execute arbitrary UI actions. This portfolio instead uses layered boundaries: model provider abstraction, typed tools, policy evaluation, explicit user confirmation, command allowlisting, and Android permission systems.

## System design

The application is divided into five major concerns:

1. **Presentation** — Jetpack Compose UI, task entry, status, confirmations.
2. **Orchestration** — bounded agent loop and step-by-step execution.
3. **Inference** — local GGUF and cloud-provider adapters.
4. **Policy** — risk classification and human approval.
5. **Execution** — normal Android APIs, Accessibility, WorkManager, and Shizuku.

The model does not directly control Android framework objects. It produces a structured decision that is resolved to an application-owned tool.

## Request lifecycle

```text
Natural-language task
        ↓
AgentOrchestrator
        ↓
AgentProviderRouter
        ↓
Local model or HTTPS model provider
        ↓
AgentDecision
        ↓
ToolRegistry
        ↓
Risk classification
        ↓
ALLOW / ASK / DENY
        ↓
Tool execution
        ↓
Observation appended to agent context
        ↓
Next step or final answer
```

The loop is deliberately capped at 16 steps to prevent unbounded execution.

## Model-provider abstraction

The provider layer supports two fundamentally different execution modes.

### Local

A GGUF model is imported through Android storage APIs and executed on-device through a JNI integration with llama.cpp.

Advantages demonstrated:

- offline inference;
- no cloud API credential;
- local model ownership;
- provider-independent orchestration.

### Cloud

Gemini and OpenAI-compatible clients use OkHttp and HTTPS. OpenAI, Groq, and compatible endpoints share one client shape while Gemini uses its own REST contract.

The orchestration layer does not depend on those protocol details.

## Credential design

Cloud API keys are treated as secrets, not ordinary preferences.

```text
API key
  ↓
AES-GCM encryption
  ↓
ciphertext + random IV
  ↓
private SharedPreferences

AES key material
  ↓
Android Keystore
```

Cloud endpoints must use HTTPS. Application preferences, encrypted secrets, and local models are excluded from Android backup/device transfer.

The project intentionally has no AuthorAI account database, OAuth session, JWT, or remote credential service.

## Privilege separation

Android interaction is separated by privilege level.

| Level | Mechanism | Example |
|---|---|---|
| Normal | Android SDK | launch activity, reminders |
| Accessibility | AccessibilityService | inspect UI, click, type, scroll |
| Shell | Shizuku UserService | selected am/pm/settings/input operations |
| Root-capable environment | Shizuku/Sui environment | same application-defined operations with broader OS authority |

Receiving elevated OS authority does not bypass the application's policy layer.

## Safety boundary

The core design choice is that the model cannot pass arbitrary shell text to a command runner.

Instead:

```text
model decision
    ↓
typed tool
    ↓
risk policy
    ↓
structured arguments
    ↓
CommandPolicy allowlist
    ↓
privileged service
```

Unknown privileged commands are classified as critical and denied.

This reduces command-injection risk and keeps the execution surface reviewable.

## Human confirmation

The policy engine exposes a small deterministic mapping:

- low risk: execute;
- medium risk: ask;
- high risk: ask;
- critical risk: deny.

The design is intentionally conservative. It demonstrates human-in-the-loop control rather than attempting to hide risk decisions inside model reasoning.

## Privacy decisions

- Local GGUF mode can operate without cloud inference.
- Accessibility password nodes are masked.
- Accessibility snapshots are not persisted by default.
- No cleartext HTTP traffic is allowed.
- API credentials are not embedded in source code.
- Android backup is disabled for secret-bearing application data.
- No hidden audio recording or background screen capture is implemented.

## Trade-offs

### Why Accessibility instead of continuous screenshots?

A semantic tree is cheaper, easier to reason about, and exposes less visual data. Vision can be a fallback, but it should not be the default observation channel.

### Why Shizuku instead of unrestricted root shell?

Shizuku gives the application a controlled privileged bridge while preserving an explicit permission flow. The application can still enforce its own command policy.

### Why a simple policy engine?

For a portfolio implementation, a deterministic and auditable policy is preferable to a complex rules engine whose behavior is difficult to demonstrate.

### Why keep the project buildable?

The repository is meant to show real engineering. Reviewers can inspect and compile actual Kotlin implementations instead of reading a design-only mockup.

## Skills demonstrated

- Kotlin
- Jetpack Compose
- Android application architecture
- Coroutines and StateFlow
- Android Accessibility APIs
- WorkManager
- Shizuku / Binder integration
- Android Keystore
- AES-GCM secret handling
- OkHttp / REST integrations
- JNI / native model integration
- LLM provider abstraction
- AI-agent orchestration
- tool-policy design
- privilege separation
- threat modeling
- unit testing
- GitHub Actions CI

## Portfolio status

This repository is a frozen engineering showcase, not a production roadmap. Future-feature promises and release artifacts are intentionally excluded so the repository reflects implemented work rather than planned product scope.

#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path.cwd()

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# 1) Android version
path = "android/app/build.gradle"
text = read(path)
text, n1 = re.subn(r'(\bversionCode\s+)11\b', r'\g<1>12', text, count=1)
text, n2 = re.subn(r'(\bversionName\s+)"0\.11\.0"', r'\g<1>"0.12.0"', text, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit(f"{path}: version replacement failed ({n1=}, {n2=})")
write(path, text)

# 2) Runtime tool enforcement in useChatSession
path = "src/hooks/useChatSession.ts"
text = read(path)

if "deriveToolSchemas," not in text:
    text = replace_once(
        text,
        "  collectSystemPromptFragments,\n  seedReadUrlAllowlist,",
        "  collectSystemPromptFragments,\n  deriveToolSchemas,\n  seedReadUrlAllowlist,",
        f"{path}: deriveToolSchemas import",
    )

constants = r'''
const JARVIS_RUNTIME_TALENTS = [
  'web_search',
  'read_url',
  'workspace',
  'python_exec',
  'android_document',
] as const;

const JARVIS_ARTIFACT_TALENTS = new Set<string>([
  'workspace',
  'python_exec',
  'android_document',
]);

const JARVIS_RUNTIME_PROMPT =
  'You are Jarvis. The Android host provides real tools. When the user asks to create or edit a file, image, PDF, DOC/DOCX, HTML page, source-code project, script, chart or other artifact, use workspace/python_exec/android_document and create the real artifact. Never answer with a fake placeholder such as [image], [file] or a prose description of an artifact. For drawn images and graphics use Python/Pillow; do not claim photorealistic generation unless an actual image-generation model exists.';

const wantsArtifactExecution = (text: string): boolean => {
  const value = text.toLocaleLowerCase();
  const action =
    /(створ|зроб|згенер|намал|побуд|збереж|експорт|покращ|відредаг|перероб|напиш|create|generate|draw|build|save|export|improve|edit|rewrite|erstell|generier|zeichn|speicher|verbesser|bearbeit)/i.test(
      value,
    );
  const artifact =
    /(картин|зображ|фото|файл|документ|pdf|docx?|html|сторінк|сайт|код|скрипт|програм|графік|діаграм|image|picture|file|document|page|website|code|script|program|chart|diagram|bild|datei|dokument|seite|webseite)/i.test(
      value,
    );
  return action && artifact;
};

const addJarvisRuntimePrompt = (
  params: ApiCompletionParams,
  forceArtifact: boolean,
): void => {
  const suffix = forceArtifact
    ? JARVIS_RUNTIME_PROMPT +
      ' This request explicitly requires an artifact: call a tool now; do not finish with prose only.'
    : JARVIS_RUNTIME_PROMPT;
  const messages = [...(params.messages ?? [])] as ChatMessage[];
  if (messages[0]?.role === 'system') {
    messages[0] = {
      ...messages[0],
      content: String(messages[0].content ?? '') + '\n\n' + suffix,
    };
  } else {
    messages.unshift({role: 'system', content: suffix});
  }
  params.messages = messages as ApiCompletionParams['messages'];
};

'''

if "const JARVIS_RUNTIME_TALENTS =" not in text:
    marker = "} from '../services/agent';\n"
    if marker not in text:
        raise SystemExit(f"{path}: services/agent import marker not found")
    text = text.replace(marker, marker + constants, 1)

pal_anchor = """    const pal = activeSession?.activePalId
      ? palStore.pals.find(p => p.id === activeSession.activePalId)
      : null;

    const systemMessages = resolveSystemMessages({
"""
pal_insert = """    const pal = activeSession?.activePalId
      ? palStore.pals.find(p => p.id === activeSession.activePalId)
      : null;

    // Jarvis is a dedicated agent build. Core execution tools must remain
    // available even when the chat has no explicitly selected Pal.
    const configuredPalTalents = (pal?.pact?.talents ?? []).map(t => t.name);
    const effectiveTalentNames = Array.from(
      new Set<string>([...configuredPalTalents, ...JARVIS_RUNTIME_TALENTS]),
    );
    const artifactExecution = wantsArtifactExecution(message.text);

    const systemMessages = resolveSystemMessages({
"""
if "const effectiveTalentNames = Array.from(" not in text:
    text = replace_once(
        text,
        pal_anchor,
        pal_insert,
        f"{path}: effective runtime talents",
    )

completion_anchor = """    currentMessageInfo.current = messageInfo;

    // Allowed talent names for this Pal. The runner rejects any
    // tool call whose function.name isn't in this list.
    const palTalents = (pal?.pact?.talents ?? []).map(t => t.name);

    abortRef.current = new AbortController();
"""
completion_insert = """    const turnTalentNames = artifactExecution
      ? effectiveTalentNames.filter(name => JARVIS_ARTIFACT_TALENTS.has(name))
      : effectiveTalentNames;
    const runtimeTools = deriveToolSchemas(turnTalentNames);
    if (runtimeTools.length > 0) {
      cleanCompletionParams.tools = runtimeTools;
      cleanCompletionParams.tool_choice = artifactExecution
        ? 'required'
        : cleanCompletionParams.tool_choice ?? 'auto';
    }
    addJarvisRuntimePrompt(cleanCompletionParams, artifactExecution);

    currentMessageInfo.current = messageInfo;

    abortRef.current = new AbortController();
"""
if "const turnTalentNames = artifactExecution" not in text:
    text = replace_once(
        text,
        completion_anchor,
        completion_insert,
        f"{path}: turn runtime tools",
    )

text = text.replace(
    "        allowedTalentNames: palTalents,",
    "        allowedTalentNames: effectiveTalentNames,",
    1,
)
if "allowedTalentNames: palTalents" in text:
    raise SystemExit(f"{path}: stale palTalents runner argument remains")

write(path, text)

# 3) If a required artifact turn is ignored by a local model, retry once.
path = "src/services/agent/AgentRunner.ts"
text = read(path)

if "REQUIRED_TOOL_RETRY_NUDGE" not in text:
    anchor = """const BUDGET_EXHAUSTED_NUDGE =
  '(Tool budget exhausted. Answer now using only the information gathered above; if it is insufficient, say what is missing.)';
"""
    insert = anchor + """const REQUIRED_TOOL_RETRY_NUDGE =
  'The user explicitly requested an executable artifact, but the previous response did not call a tool. Call one of the provided tools now and create the real artifact. Do not answer with prose, placeholders, or simulated files.';
"""
    text = replace_once(text, anchor, insert, f"{path}: retry nudge")

if "let requiredToolRetryUsed = false;" not in text:
    text = replace_once(
        text,
        "  let forceFinal = false;\n",
        "  let forceFinal = false;\n  let requiredToolRetryUsed = false;\n",
        f"{path}: retry state",
    )

old_no_calls = """      if (!calls || calls.length === 0) {
        // No tools requested — final answer landed.
        break;
      }
"""
new_no_calls = """      if (!calls || calls.length === 0) {
        // Some local GGUF templates/models ignore tool_choice='required'.
        // Give an explicit artifact turn one strict retry before accepting
        // a prose-only answer.
        if (
          !requiredToolRetryUsed &&
          initialParams.tool_choice === 'required' &&
          (initialParams.tools?.length ?? 0) > 0
        ) {
          requiredToolRetryUsed = true;
          messages = [
            ...(messages ?? []),
            {
              role: 'assistant',
              content: finishedResult.content ?? finishedResult.text ?? '',
            } as unknown as NonNullable<ApiCompletionParams['messages']>[number],
            {
              role: 'user',
              content: REQUIRED_TOOL_RETRY_NUDGE,
            } as unknown as NonNullable<ApiCompletionParams['messages']>[number],
          ];
          if (localModel) {
            messages = localChatMessages(
              messages as ChatMessage[],
            ) as ApiCompletionParams['messages'];
          }
          turn += 1;
          continue;
        }
        // No tools requested — final answer landed.
        break;
      }
"""
if "Some local GGUF templates/models ignore tool_choice='required'." not in text:
    text = replace_once(
        text,
        old_no_calls,
        new_no_calls,
        f"{path}: required tool retry",
    )

write(path, text)

print("Jarvis v0.12 runtime artifact enforcement applied.")

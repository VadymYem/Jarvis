#!/usr/bin/env python3
from pathlib import Path
import re
import shutil

ROOT = Path.cwd()
OVERLAY = (ROOT / ".." / "pocketpal" / "v013").resolve()

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def write(path, text):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

def overlay(source, target):
    src = OVERLAY / source
    dst = ROOT / target
    if not src.is_file():
        raise SystemExit(f"missing overlay: {src}")
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

# Version.
path = "android/app/build.gradle"
text = read(path)
text, n1 = re.subn(r'(\\bversionCode\\s+)12\\b', r'\\g<1>13', text, count=1)
text, n2 = re.subn(r'(\\bversionName\\s+)"0\\.12\\.0"', r'\\g<1>"0.13.0"', text, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit(f"{path}: version replacement failed ({n1=}, {n2=})")
write(path, text)

# Drop in the deterministic v0.13 runtime components.
overlay("ManagedOfflineZimModule.kt", "android/app/src/main/java/top/authorche/agent/ManagedOfflineZimModule.kt")
overlay("BackgroundWebSearchModule.kt", "android/app/src/main/java/top/authorche/agent/BackgroundWebSearchModule.kt")
overlay("offlineZim.ts", "src/knowledge/offlineZim.ts")
overlay("retrieve.ts", "src/knowledge/retrieve.ts")
overlay("toolMentions.ts", "src/agent/toolMentions.ts")
overlay("deviceWeb.ts", "src/services/search/providers/deviceWeb.ts")
overlay("ArticleLibraryScreen.tsx", "src/screens/ArticleLibraryScreen.tsx")
overlay("toolMentions.test.ts", "src/agent/__tests__/toolMentions.test.ts")
overlay("retrieve.test.ts", "src/knowledge/__tests__/retrieve.test.ts")

# Python compatibility. A local model used create_image although older builds only
# exported create_card; keep both APIs permanently.
path = "android/app/src/main/python/jarvis_artifacts.py"
text = read(path)
if "\ndef create_image(" not in text:
    anchor = "\ndef create_pdf("
    if anchor not in text:
        raise SystemExit(f"{path}: create_pdf anchor not found")
    helper = r'''
def create_image(path, prompt="", width=1024, height=1024, title="", body=""):
    """Create a real raster artifact with Pillow.

    This is a compatibility helper for model-generated scripts. It is a
    deterministic graphic renderer, not a diffusion model.
    """
    heading = str(title or "").strip()
    content = str(body or prompt or "").strip()
    if not heading and content:
        first, *rest = content.splitlines()
        if len(first) <= 80:
            heading = first
            content = "\n".join(rest).strip() or first
    return create_card(
        path,
        heading or "Jarvis",
        content,
        width=max(320, min(4096, int(width))),
        height=max(320, min(4096, int(height))),
    )

'''
    text = text.replace(anchor, "\n" + helper + "def create_pdf(", 1)
write(path, text)

# @ tool picker in the normal ChatInput, therefore available regardless of PAL.
path = "src/components/ChatInput/ChatInput.tsx"
text = read(path)
if "TOOL_MENTION_OPTIONS" not in text:
    text = replace_once(
        text,
        "import {t} from '../../locales';\n",
        "import {t} from '../../locales';\nimport {TOOL_MENTION_OPTIONS} from '../../agent/toolMentions';\n",
        f"{path}: tool mention import",
    )

if "const mentionSuggestions =" not in text:
    logic = r'''
    const mentionMatch =
      typeof value === 'string' ? /(?:^|\s)@([^\s@]*)$/u.exec(value) : null;
    const mentionQuery = (mentionMatch?.[1] ?? '').toLocaleLowerCase();
    const mentionSuggestions = mentionMatch
      ? TOOL_MENTION_OPTIONS.filter(option => {
          const values = [
            option.token.slice(1),
            option.title,
            ...option.aliases,
          ].map(item => item.toLocaleLowerCase());
          return !mentionQuery || values.some(item => item.includes(mentionQuery));
        }).slice(0, 5)
      : [];

    const handleToolMention = (token: string) => {
      const current = String(value ?? '');
      const next = current.replace(/@([^\s@]*)$/u, token + ' ');
      handleChangeText(next);
      requestAnimationFrame(() => inputRef.current?.focus());
    };

'''
    text = replace_once(
        text,
        "    const handleSend = () => {\n",
        logic + "    const handleSend = () => {\n",
        f"{path}: tool mention logic",
    )

if 'testID="tool-mention-menu"' not in text:
    ui = r'''
          {mentionSuggestions.length > 0 && !isStreaming && (
            <View
              testID="tool-mention-menu"
              style={{
                marginHorizontal: 12,
                marginBottom: 6,
                overflow: 'hidden',
                borderRadius: 16,
                borderWidth: 1,
                borderColor: theme.colors.outlineVariant,
                backgroundColor: theme.colors.elevation.level2,
              }}>
              {mentionSuggestions.map((option, index) => (
                <TouchableOpacity
                  key={option.id}
                  testID={'tool-mention-' + option.id}
                  onPress={() => handleToolMention(option.token)}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 10,
                    paddingHorizontal: 14,
                    paddingVertical: 10,
                    borderBottomWidth:
                      index === mentionSuggestions.length - 1 ? 0 : 1,
                    borderBottomColor: theme.colors.outlineVariant,
                  }}>
                  <IconButton
                    icon={option.icon}
                    size={20}
                    style={{margin: 0}}
                    iconColor={theme.colors.primary}
                  />
                  <View style={{flex: 1}}>
                    <Text variant="labelLarge">
                      {option.token} · {option.title}
                    </Text>
                    <Text
                      variant="bodySmall"
                      style={{color: theme.colors.onSurfaceVariant}}>
                      {option.description}
                    </Text>
                  </View>
                </TouchableOpacity>
              ))}
            </View>
          )}

'''
    text = replace_once(
        text,
        "          {/* Text Input Area (Top Row) */}\n",
        ui + "          {/* Text Input Area (Top Row) */}\n",
        f"{path}: tool mention UI",
    )
write(path, text)

# Host routing in every chat. PAL talents are additive, never required.
path = "src/hooks/useChatSession.ts"
text = read(path)
if "extractToolMentions" not in text:
    text = replace_once(
        text,
        "import {localChatMessages} from '../utils/localChatMessages';\n",
        "import {localChatMessages} from '../utils/localChatMessages';\nimport {extractToolMentions} from '../agent/toolMentions';\n",
        f"{path}: mention import",
    )

if "const explicitTools = extractToolMentions(message.text);" not in text:
    text = replace_once(
        text,
        "    let knowledgeEvidence = '';\n",
        "    const explicitTools = extractToolMentions(message.text);\n\n    let knowledgeEvidence = '';\n",
        f"{path}: explicit tool parse",
    )

text = text.replace(
    "knowledgeEvidence = await retrieveKnowledge(message.text);",
    "knowledgeEvidence = await retrieveKnowledge(message.text, explicitTools);",
)
text = text.replace(
    "const searchingWeb = wantsWebSearch(message.text);",
    "const searchingWeb = wantsWebSearch(message.text, explicitTools);",
)

old_turn = """    const turnTalentNames = artifactExecution
      ? effectiveTalentNames.filter(name => JARVIS_ARTIFACT_TALENTS.has(name))
      : effectiveTalentNames;
    const runtimeTools = deriveToolSchemas(turnTalentNames);
    if (runtimeTools.length > 0) {
      cleanCompletionParams.tools = runtimeTools;
      cleanCompletionParams.tool_choice = artifactExecution
        ? 'required'
        : cleanCompletionParams.tool_choice ?? 'auto';
    }
    if (artifactExecution) {
      addJarvisRuntimePrompt(cleanCompletionParams, true);
    }
"""
new_turn = """    const forcedTalentNames = new Set<string>();
    if (explicitTools.includes('image') || explicitTools.includes('python')) {
      forcedTalentNames.add('python_exec');
      forcedTalentNames.add('workspace');
    }
    if (explicitTools.includes('files')) {
      forcedTalentNames.add('android_document');
      forcedTalentNames.add('workspace');
    }

    const forceArtifact =
      artifactExecution || explicitTools.includes('image');
    const turnTalentNames =
      forcedTalentNames.size > 0
        ? effectiveTalentNames.filter(name => forcedTalentNames.has(name))
        : forceArtifact
          ? effectiveTalentNames.filter(name => JARVIS_ARTIFACT_TALENTS.has(name))
          : effectiveTalentNames;
    const runtimeTools = deriveToolSchemas(turnTalentNames);
    if (runtimeTools.length > 0) {
      cleanCompletionParams.tools = runtimeTools;
      cleanCompletionParams.tool_choice =
        forcedTalentNames.size > 0 || forceArtifact
          ? 'required'
          : cleanCompletionParams.tool_choice ?? 'auto';
    }
    if (forceArtifact) {
      addJarvisRuntimePrompt(cleanCompletionParams, true);
    }
"""
if old_turn in text:
    text = text.replace(old_turn, new_turn, 1)
elif "const forcedTalentNames = new Set<string>();" not in text:
    raise SystemExit(f"{path}: v0.12 runtime tool block not found")

text = text.replace(
    "allowedTalentNames: effectiveTalentNames,",
    "allowedTalentNames: turnTalentNames,",
)
write(path, text)

# Drawer route and fast non-AI article reader.
path = "src/screens/index.ts"
text = read(path)
if "ArticleLibraryScreen" not in text:
    text = text.rstrip() + "\nexport * from './ArticleLibraryScreen';\n"
write(path, text)

path = "src/utils/navigationConstants.ts"
text = read(path)
if "ARTICLES:" not in text:
    text = replace_once(
        text,
        "  CHAT: 'Chat',\n",
        "  CHAT: 'Chat',\n  ARTICLES: 'Articles',\n",
        f"{path}: articles route",
    )
write(path, text)

path = "App.tsx"
text = read(path)
if "ArticleLibraryScreen" not in text:
    text = replace_once(
        text,
        "  ChatScreen,\n",
        "  ChatScreen,\n  ArticleLibraryScreen,\n",
        f"{path}: articles import",
    )
if "name={ROUTES.ARTICLES}" not in text:
    screen = """                          <Drawer.Screen
                            name={ROUTES.ARTICLES}
                            component={gestureHandlerRootHOC(ArticleLibraryScreen)}
                            options={{
                              headerStyle: styles.headerWithoutDivider,
                              title: 'Завантаження статей',
                            }}
                          />
"""
    text = replace_once(
        text,
        "                          <Drawer.Screen\n                            name={ROUTES.PALS}\n",
        screen + "                          <Drawer.Screen\n                            name={ROUTES.PALS}\n",
        f"{path}: articles drawer screen",
    )
write(path, text)

path = "src/components/SidebarContent/SidebarContent.tsx"
text = read(path)
if "drawer-item-articles" not in text:
    item = """            <Drawer.Item
              label="Завантаження статей"
              icon={() => <AppInfoIcon stroke={theme.colors.primary} />}
              onPress={() => props.navigation.navigate(ROUTES.ARTICLES)}
              style={styles.menuDrawerItem}
              testID="drawer-item-articles"
            />
"""
    text = replace_once(
        text,
        "            <Drawer.Item\n              label={l10n.components.sidebarContent.menuItems.pals}\n",
        item + "            <Drawer.Item\n              label={l10n.components.sidebarContent.menuItems.pals}\n",
        f"{path}: articles sidebar item",
    )
write(path, text)

# Make the Settings copy truthful: ZIM is opt-in per turn via @Вікі.
path = "src/screens/SettingsScreen/OfflineKnowledgeCard.tsx"
text = read(path)
text = text.replace(
    "Після цього Jarvis шукатиме в ньому автоматично без інтернету.",
    "У чаті викликайте архів явно через @Вікі; без цього локальна бібліотека не підмішується до відповіді.",
)
write(path, text)

# CI regression checks: the reported import must never regress again.
path = "android/app/src/main/python/jarvis_artifacts.py"
text = read(path)
if "def create_image(" not in text:
    raise SystemExit("jarvis_artifacts.create_image was not installed")

print("Jarvis v0.13 overlays and runtime routing applied.")

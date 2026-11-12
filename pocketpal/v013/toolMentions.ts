export type JarvisToolMention =
  | 'wiki'
  | 'web'
  | 'image'
  | 'python'
  | 'files';

export type JarvisToolMentionOption = {
  id: JarvisToolMention;
  token: string;
  aliases: string[];
  title: string;
  description: string;
  icon: string;
};

export const TOOL_MENTION_OPTIONS: JarvisToolMentionOption[] = [
  {
    id: 'wiki',
    token: '@Вікі',
    aliases: ['вікі', 'wiki', 'wikipedia', 'zim', 'статті', 'articles'],
    title: 'Вікі / ZIM',
    description: 'Шукати лише в імпортованих офлайн-архівах',
    icon: 'book-open-page-variant',
  },
  {
    id: 'web',
    token: '@Веб',
    aliases: ['веб', 'web', 'internet', 'інтернет', 'search', 'пошук'],
    title: 'Веб-пошук',
    description: 'Примусово виконати актуальний пошук в інтернеті',
    icon: 'web',
  },
  {
    id: 'image',
    token: '@Зображення',
    aliases: ['зображення', 'image', 'picture', 'картинка', 'graphic'],
    title: 'Створення зображення',
    description: 'Створити реальний PNG/JPEG через локальний Python/Pillow',
    icon: 'image-outline',
  },
  {
    id: 'python',
    token: '@Python',
    aliases: ['python', 'py', 'код', 'code'],
    title: 'Python',
    description: 'Примусово запустити локальний Python для цього запиту',
    icon: 'language-python',
  },
  {
    id: 'files',
    token: '@Файли',
    aliases: ['файли', 'files', 'file', 'документ', 'document'],
    title: 'Файли',
    description: 'Відкрити або опрацювати файл через системний вибір Android',
    icon: 'file-document-outline',
  },
];

const normalize = (value: string): string => value.trim().toLocaleLowerCase();
const mentionPattern = /@([^\s@.,!?;:()[\]{}]+)/g;

export const extractToolMentions = (text: string): JarvisToolMention[] => {
  const found = new Set<JarvisToolMention>();
  const matches = text.matchAll(mentionPattern);
  for (const match of matches) {
    const value = normalize(match[1] ?? '');
    const option = TOOL_MENTION_OPTIONS.find(
      item =>
        normalize(item.token.slice(1)) === value ||
        item.aliases.some(alias => normalize(alias) === value),
    );
    if (option) found.add(option.id);
  }
  return [...found];
};

export const stripToolMentions = (text: string): string => {
  const known = new Set(
    TOOL_MENTION_OPTIONS.flatMap(option => [
      normalize(option.token.slice(1)),
      ...option.aliases.map(normalize),
    ]),
  );
  return text
    .replace(mentionPattern, (whole, name: string) =>
      known.has(normalize(name)) ? ' ' : whole,
    )
    .replace(/\s+/g, ' ')
    .trim();
};

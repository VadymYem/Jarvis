import {createSearchProvider} from '../services/search';
import type {SearchProviderId} from '../services/search/types';
import {offlineZim, formatOfflineEvidence} from './offlineZim';
import {searchProviderStore} from '../store/SearchProviderStore';
import {
  extractToolMentions,
  stripToolMentions,
  type JarvisToolMention,
} from '../agent/toolMentions';

const WEB_REQUEST =
  /(?:знайди|пошукай|пошук|в інтернеті|в мережі|актуальн|сьогодні|latest|current|search (?:the )?web|look up|find online|im internet suchen)/i;

export const wantsWebSearch = (
  text: string,
  mentions: JarvisToolMention[] = extractToolMentions(text),
): boolean => mentions.includes('web') || WEB_REQUEST.test(stripToolMentions(text));

export const conciseQuery = (text: string): string =>
  stripToolMentions(text)
    .replace(
      /^(?:розкажи|поясни|що таке|хто такий|хто така|знайди|пошукай|search for|look up)\s+/i,
      '',
    )
    .replace(/[?!]+$/g, '')
    .trim()
    .split(/\s+/)
    .slice(0, 14)
    .join(' ');

const webEvidence = async (query: string): Promise<string> => {
  const selected = searchProviderStore.activeProviderId;
  const providerIds: SearchProviderId[] =
    selected === 'device_web' ? ['device_web'] : [selected, 'device_web'];
  const failures: string[] = [];

  for (const id of providerIds) {
    try {
      const hits = await createSearchProvider(
        id,
        () => searchProviderStore.getKey(id),
      ).search(query, {maxResults: 6});
      if (!hits.length) throw new Error('пошук не повернув результатів');
      return (
        'Результати веб-пошуку, отримані зараз. Використовуй лише наведені факти й URL; якщо відповіді немає, скажи про це.\n' +
        hits
          .slice(0, 6)
          .map(
            hit =>
              `${hit.title}\n${hit.url}\n${hit.snippet.slice(0, 500)}`,
          )
          .join('\n\n')
      );
    } catch (error) {
      failures.push(`${id}: ${String(error).slice(0, 220)}`);
    }
  }

  throw new Error(
    'Веб-пошук не спрацював через жоден доступний канал. ' +
      failures.join('; '),
  );
};

export const retrieveKnowledge = async (
  text: string,
  mentions: JarvisToolMention[] = extractToolMentions(text),
): Promise<string> => {
  const query = conciseQuery(text);
  if (!query || query.length < 2 || query.length > 180) return '';

  const blocks: string[] = [];

  if (mentions.includes('wiki')) {
    const status = await offlineZim.status();
    if (!status.ready) {
      throw new Error(
        'Для @Вікі немає імпортованих ZIM. Відкрийте «Завантаження статей» і додайте .zim.',
      );
    }
    const articles = await offlineZim.search(query, 5);
    if (!articles.length) {
      blocks.push(
        'У підключених ZIM-архівах за цим запитом нічого не знайдено.',
      );
    } else {
      blocks.push(
        'Фрагменти з явно вибраного користувачем плагіна @Вікі/ZIM. Це локальні дані й вони можуть бути неактуальними.\n' +
          formatOfflineEvidence(articles),
      );
    }
  }

  if (wantsWebSearch(text, mentions)) {
    blocks.push(await webEvidence(query));
  }

  return blocks.join('\n\n');
};

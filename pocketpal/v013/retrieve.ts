import {createSearchProvider} from '../services/search';
import type {SearchProviderId} from '../services/search/types';
import {offlineZim, formatOfflineEvidence, type OfflineArticle} from './offlineZim';
import {searchProviderStore} from '../store/SearchProviderStore';
import {
  extractToolMentions,
  stripToolMentions,
  type JarvisToolMention,
} from '../agent/toolMentions';

const WEB_REQUEST =
  /(?:знайди|пошукай|пошук|в інтернеті|в мережі|актуальн|сьогодні|latest|current|search (?:the )?web|look up|find online|im internet suchen)/i;

const SEARCH_STOP_WORDS = new Set([
  'про',
  'для',
  'та',
  'і',
  'й',
  'або',
  'the',
  'and',
  'for',
  'with',
  'von',
  'und',
  'der',
  'die',
  'das',
  'людину',
  'людина',
  'інфо',
  'інформацію',
  'інформація',
  'дані',
]);

type EvidenceHit = {
  title: string;
  url: string;
  snippet: string;
};

export const wantsWebSearch = (
  text: string,
  mentions: JarvisToolMention[] = extractToolMentions(text),
): boolean => {
  if (mentions.includes('web')) return true;
  // @Вікі is an explicit request for the local ZIM library. Words such as
  // "знайди" inside that request must never silently activate the Internet.
  if (mentions.includes('wiki')) return false;
  return WEB_REQUEST.test(stripToolMentions(text));
};

export const conciseQuery = (text: string): string => {
  let value = stripToolMentions(text)
    .replace(/^[\s,.:;!?-]+|[?!]+$/g, '')
    .trim();

  // Remove conversational command/filler prefixes but keep the subject itself.
  // Example: "@Вікі знайди інфо про Тараса Шевченка" -> "Тараса Шевченка".
  for (let pass = 0; pass < 3; pass += 1) {
    const before = value;
    value = value
      .replace(
        /^(?:будь ласка[,\s]+)?(?:знайди|пошукай|покажи|розкажи|поясни|дай|search for|look up|find(?: online)?|suche(?: im internet)? nach)\s+/i,
        '',
      )
      .replace(
        /^(?:інфо(?:рмацію)?|інформацію|відомості|дані)\s+(?:про|щодо)\s+/i,
        '',
      )
      .replace(/^(?:хто такий|хто така|хто такі|що таке)\s+/i, '')
      .trim();
    if (value === before) break;
  }

  return value
    .split(/\s+/)
    .slice(0, 16)
    .join(' ')
    .slice(0, 180);
};

const tokenize = (value: string): string[] =>
  (value.toLocaleLowerCase().match(/[\p{L}\p{N}_-]{3,}/gu) ?? [])
    .map(token => token.replace(/^[-_]+|[-_]+$/g, ''))
    .filter(token => token.length >= 3 && !SEARCH_STOP_WORDS.has(token));

const relevantHits = (query: string, hits: EvidenceHit[]): EvidenceHit[] => {
  const queryTokens = Array.from(new Set(tokenize(query)));
  if (!queryTokens.length) return hits;

  return hits.filter(hit => {
    const haystack = `${hit.title} ${hit.snippet} ${hit.url}`.toLocaleLowerCase();
    const matched = queryTokens.filter(token => haystack.includes(token));
    if (!matched.length) return false;

    // A distinctive long token (AuthorChe, Шевченка, Android, etc.) is strong
    // enough by itself. Otherwise require two overlapping terms for longer
    // natural-language queries.
    if (matched.some(token => token.length >= 6)) return true;
    return queryTokens.length <= 2 || matched.length >= 2;
  });
};

const queryCandidates = (query: string): string[] => {
  const values = [query];
  const distinctive = query
    .split(/\s+/)
    .map(word => word.replace(/^[^\p{L}\p{N}_-]+|[^\p{L}\p{N}_-]+$/gu, ''))
    .filter(
      word =>
        word.length >= 5 &&
        (/^[A-ZА-ЯІЇЄҐ].*[a-zа-яіїєґ]/u.test(word) ||
          /[A-Z].*[A-Z]|[a-z].*[A-Z]/.test(word) ||
          /\d/.test(word)),
    );

  // Rare proper names/usernames are often a better second attempt than a
  // conversational phrase such as "людину з псевдонімом AuthorChe".
  if (distinctive.length) values.push(distinctive.slice(-3).join(' '));

  const deduped = Array.from(new Set(values.map(value => value.trim()).filter(Boolean)));
  return deduped.slice(0, 2);
};

const webEvidence = async (query: string): Promise<string> => {
  const selected = searchProviderStore.activeProviderId;
  const providerIds: SearchProviderId[] =
    selected === 'device_web' ? ['device_web'] : [selected, 'device_web'];
  const failures: string[] = [];

  for (const candidate of queryCandidates(query)) {
    for (const id of providerIds) {
      try {
        const rawHits = (await createSearchProvider(
          id,
          () => searchProviderStore.getKey(id),
        ).search(candidate, {maxResults: 8})) as EvidenceHit[];
        const hits = relevantHits(candidate, rawHits).slice(0, 6);
        if (!hits.length) {
          throw new Error('пошук повернув лише нерелевантні результати');
        }
        return (
          `Результати веб-пошуку для запиту "${candidate}", отримані зараз. ` +
          'Використовуй лише наведені факти й URL; якщо відповіді немає, прямо скажи про це.\n' +
          hits
            .map(
              hit =>
                `${hit.title}\n${hit.url}\n${hit.snippet.slice(0, 600)}`,
            )
            .join('\n\n')
        );
      } catch (error) {
        failures.push(
          `${id} [${candidate}]: ${String(error).slice(0, 180)}`,
        );
      }
    }
  }

  throw new Error(
    'Веб-пошук не знайшов перевірених релевантних результатів через доступні канали. ' +
      failures.slice(-5).join('; '),
  );
};

const offlineQueries = (query: string): string[] => {
  const values = [query];
  const words = query.split(/\s+/).filter(Boolean);
  if (words.length > 2) values.push(words.slice(-3).join(' '));
  return Array.from(new Set(values)).slice(0, 2);
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

    let articles: OfflineArticle[] = [];
    let usedQuery = query;
    for (const candidate of offlineQueries(query)) {
      const found = await offlineZim.search(candidate, 5);
      if (found.length) {
        articles = found;
        usedQuery = candidate;
        break;
      }
    }

    if (!articles.length) {
      blocks.push(
        `У підключених ZIM-архівах за запитом "${query}" нічого не знайдено. Не використовуй веб або власні знання як заміну, якщо користувач явно вибрав @Вікі.`,
      );
    } else {
      blocks.push(
        `Фрагменти з явно вибраного користувачем @Вікі/ZIM для запиту "${usedQuery}". Це локальні дані й вони можуть бути неактуальними. Відповідай лише на їх основі; не підмінюй їх веб-пошуком.\n` +
          formatOfflineEvidence(articles),
      );
    }
  }

  if (wantsWebSearch(text, mentions)) {
    blocks.push(await webEvidence(query));
  }

  return blocks.join('\n\n');
};

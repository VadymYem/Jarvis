import {NativeModules, Platform} from 'react-native';
import type {SearchProvider, SearchHit, SearchOptions} from '../types';
import {fetchText} from './http';

const WEB_HEADERS = {
  Accept:
    'text/html,application/xhtml+xml,application/rss+xml,application/xml;q=0.9,*/*;q=0.8',
  'Accept-Language': 'uk-UA,uk;q=0.9,en;q=0.8,de;q=0.6',
  'Cache-Control': 'no-cache',
};

const decodeEntities = (value: string): string =>
  value
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&#x2F;/gi, '/')
    .replace(/&#(\d+);/g, (_match, code) =>
      String.fromCharCode(Number(code)),
    );

const stripMarkup = (value: string): string =>
  decodeEntities(
    value
      .replace(/<script[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style[\s\S]*?<\/style>/gi, ' ')
      .replace(/<[^>]*>/g, ' '),
  )
    .replace(/\s+/g, ' ')
    .trim();

const normalizeHref = (rawHref: string): string | null => {
  const href = decodeEntities(rawHref).trim();
  try {
    const candidate = href.startsWith('//') ? 'https:' + href : href;
    const url = new URL(candidate, 'https://duckduckgo.com');
    if (
      url.hostname.endsWith('duckduckgo.com') &&
      (url.pathname.startsWith('/l/') || url.pathname.startsWith('/lite/'))
    ) {
      const target = url.searchParams.get('uddg');
      if (target && /^https?:\/\//i.test(target)) {
        return decodeURIComponent(target);
      }
    }
    const result = url.toString();
    return /^https?:\/\//i.test(result) ? result : null;
  } catch {
    return null;
  }
};

const dedupe = (hits: SearchHit[], max: number): SearchHit[] => {
  const seen = new Set<string>();
  const out: SearchHit[] = [];
  for (const hit of hits) {
    const key = hit.url.replace(/#.*$/, '');
    if (!hit.title || !hit.url || seen.has(key)) continue;
    seen.add(key);
    out.push(hit);
    if (out.length >= max) break;
  }
  return out;
};

const readAttribute = (attrs: string, name: string): string => {
  const expression = new RegExp(
    name + '\\s*=\\s*(?:"([^"]*)"|\\'([^\\']*)\\')',
    'i',
  );
  const match = expression.exec(attrs);
  return match?.[1] ?? match?.[2] ?? '';
};

const parseDuckDuckGo = (html: string, max: number): SearchHit[] => {
  const hits: SearchHit[] = [];
  const anchor = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  let match: RegExpExecArray | null;
  while ((match = anchor.exec(html)) && hits.length < max * 3) {
    const attrs = match[1];
    const className = readAttribute(attrs, 'class');
    if (!/(?:^|\s)result__a(?:\s|$)/i.test(className)) continue;
    const url = normalizeHref(readAttribute(attrs, 'href'));
    const title = stripMarkup(match[2]);
    if (!url || !title) continue;

    const window = html.slice(anchor.lastIndex, anchor.lastIndex + 3200);
    const snippetMatch =
      /class=["'][^"']*result__snippet[^"']*["'][^>]*>([\s\S]*?)<\/(?:a|div)>/i.exec(
        window,
      );
    hits.push({
      title,
      url,
      snippet: snippetMatch ? stripMarkup(snippetMatch[1]).slice(0, 700) : '',
    });
  }
  return dedupe(hits, max);
};

const parseBing = (html: string, max: number): SearchHit[] => {
  const hits: SearchHit[] = [];
  const block =
    /<li[^>]+class=["'][^"']*\bb_algo\b[^"']*["'][^>]*>([\s\S]*?)<\/li>/gi;
  let match: RegExpExecArray | null;
  while ((match = block.exec(html)) && hits.length < max * 2) {
    const body = match[1];
    const link =
      /<h2[^>]*>\s*<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i.exec(
        body,
      );
    if (!link) continue;
    const url = normalizeHref(link[1]);
    const title = stripMarkup(link[2]);
    if (!url || !title) continue;
    const snippet =
      /<p[^>]*>([\s\S]*?)<\/p>/i.exec(body)?.[1] ??
      /class=["'][^"']*b_caption[^"']*["'][^>]*>([\s\S]*?)<\/div>/i.exec(
        body,
      )?.[1] ??
      '';
    hits.push({title, url, snippet: stripMarkup(snippet).slice(0, 700)});
  }
  return dedupe(hits, max);
};

const xmlValue = (item: string, name: string): string => {
  const value =
    new RegExp('<' + name + '>([\\s\\S]*?)<\\/' + name + '>', 'i').exec(
      item,
    )?.[1] ?? '';
  return stripMarkup(value.replace(/^<!\[CDATA\[|\]\]>$/g, ''));
};

export const parseRss = (body: string, maxResults: number): SearchHit[] => {
  if (!/<rss\b/i.test(body)) {
    throw new Error('Сервіс повернув сторінку замість RSS');
  }
  const items = body.match(/<item\b[^>]*>[\s\S]*?<\/item>/gi) ?? [];
  return items
    .slice(0, Math.min(8, Math.max(1, maxResults)))
    .flatMap(item => {
      const title = xmlValue(item, 'title');
      const url = xmlValue(item, 'link');
      if (!title || !/^https?:\/\//i.test(url)) return [];
      return [
        {
          title,
          url,
          snippet: xmlValue(item, 'description').slice(0, 700),
        },
      ];
    });
};

export class DeviceWebProvider implements SearchProvider {
  readonly id = 'device_web';

  async search(query: string, opts: SearchOptions): Promise<SearchHit[]> {
    const max = Math.min(8, Math.max(1, opts.maxResults));
    const failures: string[] = [];

    try {
      const feed = await fetchText(
        'https://www.bing.com/search?format=rss&q=' + encodeURIComponent(query),
        {
          method: 'GET',
          headers: {Accept: 'application/rss+xml,application/xml;q=0.9'},
        },
        10000,
      );
      const hits = parseRss(feed, max);
      if (hits.length) return hits;
      failures.push('Bing RSS: немає результатів');
    } catch (error) {
      failures.push('Bing RSS: ' + String(error));
    }

    try {
      const html = await fetchText(
        'https://html.duckduckgo.com/html/?q=' + encodeURIComponent(query),
        {method: 'GET', headers: WEB_HEADERS},
        10000,
      );
      const hits = parseDuckDuckGo(html, max);
      if (hits.length) return hits;
      failures.push('DuckDuckGo HTML: немає результатів');
    } catch (error) {
      failures.push('DuckDuckGo HTML: ' + String(error));
    }

    try {
      const html = await fetchText(
        'https://www.bing.com/search?setlang=uk-UA&count=10&q=' +
          encodeURIComponent(query),
        {method: 'GET', headers: WEB_HEADERS},
        10000,
      );
      const hits = parseBing(html, max);
      if (hits.length) return hits;
      failures.push('Bing HTML: немає результатів');
    } catch (error) {
      failures.push('Bing HTML: ' + String(error));
    }

    if (Platform.OS === 'android' && NativeModules.BackgroundWebSearchModule) {
      try {
        const hits = (await NativeModules.BackgroundWebSearchModule.search(
          query,
        )) as SearchHit[];
        const clean = dedupe(hits, max);
        if (clean.length) return clean;
        failures.push('Android browser: немає результатів');
      } catch (error) {
        failures.push('Android browser: ' + String(error));
      }
    }

    throw new Error(
      'Веб-пошук недоступний з цього підключення. ' + failures.join('; '),
    );
  }
}

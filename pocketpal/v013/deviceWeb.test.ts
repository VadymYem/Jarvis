import {filterRelevantHits, parseRss} from '../deviceWeb';

describe('DeviceWebProvider relevance safeguards', () => {
  it('rejects unrelated result sets instead of feeding them to the model', () => {
    const hits = filterRelevantHits(
      'Тарас Шевченко',
      [
        {
          title: 'ForgeGUI - AI Roblox Scripting Tool',
          url: 'https://devforum.roblox.com/example',
          snippet: 'Roblox Studio plugin for scripting.',
        },
      ],
      6,
    );
    expect(hits).toEqual([]);
  });

  it('rejects unrelated pages for distinctive usernames', () => {
    const hits = filterRelevantHits(
      'людину з псевдонімом AuthorChe',
      [
        {
          title: 'Hotel Bahía Calpe',
          url: 'https://bahiacalpehotel.com/',
          snippet: 'Hotel on the Costa Blanca in Spain.',
        },
      ],
      6,
    );
    expect(hits).toEqual([]);
  });

  it('keeps genuinely relevant results', () => {
    const hits = filterRelevantHits(
      'Тарас Шевченко',
      [
        {
          title: 'Тарас Шевченко — біографія',
          url: 'https://example.org/shevchenko',
          snippet: 'Український поет Тарас Григорович Шевченко.',
        },
      ],
      6,
    );
    expect(hits).toHaveLength(1);
  });

  it('parses a valid Bing-style RSS feed', () => {
    const hits = parseRss(
      '<rss><channel><item><title>AuthorChe profile</title><link>https://example.org/authorche</link><description>AuthorChe information</description></item></channel></rss>',
      6,
    );
    expect(hits).toEqual([
      {
        title: 'AuthorChe profile',
        url: 'https://example.org/authorche',
        snippet: 'AuthorChe information',
      },
    ]);
  });
});

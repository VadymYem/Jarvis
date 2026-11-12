import {
  conciseQuery,
  wantsWebSearch,
  retrieveKnowledge,
} from '../retrieve';
import {offlineZim} from '../offlineZim';

jest.mock('../offlineZim', () => ({
  offlineZim: {
    status: jest.fn(),
    search: jest.fn(),
  },
  formatOfflineEvidence: jest.fn((items: any[]) =>
    items.map(item => item.title).join('\n'),
  ),
}));

jest.mock('../../store/SearchProviderStore', () => ({
  searchProviderStore: {
    activeProviderId: 'device_web',
    getKey: jest.fn(() => ''),
  },
}));

jest.mock('../../services/search', () => ({
  createSearchProvider: jest.fn(() => ({
    search: jest.fn(async () => [
      {
        title: 'Актуальний результат',
        url: 'https://example.org/current',
        snippet: 'Поточні дані',
      },
    ]),
  })),
}));

describe('Pocket knowledge retrieval', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (offlineZim.status as jest.Mock).mockResolvedValue({
      ready: true,
      name: 'ukwiki.zim',
    });
    (offlineZim.search as jest.Mock).mockResolvedValue([
      {
        title: 'Фотосинтез',
        path: 'A/Photosynthesis',
        excerpt: 'Локальна стаття',
        archive: 'ukwiki.zim',
        archiveId: 'ukwiki',
      },
    ]);
  });

  it('does not use ZIM automatically for an ordinary chat turn', async () => {
    expect(wantsWebSearch('Що таке фотосинтез?')).toBe(false);
    expect(conciseQuery('Що таке фотосинтез?')).toBe('фотосинтез');
    await expect(retrieveKnowledge('Що таке фотосинтез?')).resolves.toBe('');
    expect(offlineZim.status).not.toHaveBeenCalled();
    expect(offlineZim.search).not.toHaveBeenCalled();
  });

  it('uses the offline archive only when @Вікі is explicitly selected', async () => {
    const evidence = await retrieveKnowledge('@Вікі Що таке фотосинтез?');
    expect(offlineZim.status).toHaveBeenCalled();
    expect(offlineZim.search).toHaveBeenCalledWith('фотосинтез', 5);
    expect(evidence).toContain('Фотосинтез');
  });

  it('keeps @Вікі strictly offline even when the prompt says знайди', async () => {
    expect(wantsWebSearch('@Вікі знайди інфо про Тараса Шевченка')).toBe(false);
    expect(conciseQuery('@Вікі знайди інфо про Тараса Шевченка')).toBe(
      'Тараса Шевченка',
    );
    const evidence = await retrieveKnowledge(
      '@Вікі знайди інфо про Тараса Шевченка',
    );
    expect(offlineZim.search).toHaveBeenCalledWith('Тараса Шевченка', 5);
    expect(evidence).toContain('Фотосинтез');
  });

  it('recognizes explicit and natural-language web requests', () => {
    expect(wantsWebSearch('@Веб погода Бонн')).toBe(true);
    expect(wantsWebSearch('Знайди новини про космос')).toBe(true);
    expect(wantsWebSearch('Яка актуальна версія Android?')).toBe(true);
  });
});

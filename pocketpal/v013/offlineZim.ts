import {NativeModules, Platform} from 'react-native';

export interface ArchiveInfo {
  id: string;
  name: string;
  articles: number;
  bytes: number;
}

export interface OfflineArticle {
  title: string;
  path: string;
  excerpt: string;
  archive: string;
  archiveId: string;
}

export interface OfflineArticleContent {
  title: string;
  archive: string;
  archiveId: string;
  path: string;
  text: string;
  truncated: boolean;
}

export interface ArchiveStatus {
  ready: boolean;
  name: string | null;
  articles?: number;
  archives?: ArchiveInfo[];
}

interface OfflineZimNative {
  chooseArchive(): Promise<(ArchiveStatus & {id?: string; bytes?: number}) | null>;
  status(): Promise<ArchiveStatus>;
  search(query: string, limit: number): Promise<OfflineArticle[]>;
  readArticle(archiveId: string, path: string, title: string): Promise<OfflineArticleContent>;
  deleteArchive(id: string): Promise<void>;
  removeArchive(key: string): Promise<void>;
  forgetArchive(): Promise<void>;
}

const native =
  Platform.OS === 'android'
    ? (NativeModules.OfflineZimModule as OfflineZimNative | undefined)
    : undefined;

export const offlineZim: OfflineZimNative = {
  chooseArchive: () => native?.chooseArchive() ?? Promise.resolve(null),
  status: () =>
    native?.status() ??
    Promise.resolve({ready: false, name: null, articles: 0, archives: []}),
  search: (query, limit) => native?.search(query, limit) ?? Promise.resolve([]),
  readArticle: (archiveId, path, title) =>
    native?.readArticle(archiveId, path, title) ??
    Promise.reject(new Error('Offline article reader unavailable.')),
  deleteArchive: id =>
    native?.deleteArchive(id) ?? Promise.resolve(),
  removeArchive: key =>
    native?.removeArchive(key) ?? native?.deleteArchive(key) ?? Promise.resolve(),
  forgetArchive: () => native?.forgetArchive() ?? Promise.resolve(),
};

export const formatOfflineEvidence = (articles: OfflineArticle[]): string =>
  articles
    .slice(0, 5)
    .map(
      (article, index) =>
        `[${index + 1}] ${article.title} · ${article.archive} · ${article.path}\n${article.excerpt.slice(0, 1400)}`,
    )
    .join('\n\n');

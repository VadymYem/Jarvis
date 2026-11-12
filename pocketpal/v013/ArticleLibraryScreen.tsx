import React from 'react';
import {Alert, ScrollView, TouchableOpacity, View} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {
  ActivityIndicator,
  Button,
  Card,
  Divider,
  Searchbar,
  Text,
} from 'react-native-paper';

import {
  offlineZim,
  type ArchiveInfo,
  type ArchiveStatus,
  type OfflineArticle,
  type OfflineArticleContent,
} from '../knowledge/offlineZim';
import {useTheme} from '../hooks';

const formatBytes = (bytes: number): string => {
  if (!Number.isFinite(bytes) || bytes <= 0) return 'невідомо';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value >= 10 || unit === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unit]}`;
};

export const ArticleLibraryScreen: React.FC = () => {
  const theme = useTheme();
  const [status, setStatus] = React.useState<ArchiveStatus>({
    ready: false,
    name: null,
    articles: 0,
    archives: [],
  });
  const [query, setQuery] = React.useState('');
  const [results, setResults] = React.useState<OfflineArticle[]>([]);
  const [article, setArticle] = React.useState<OfflineArticleContent | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState('');

  const refresh = React.useCallback(async () => {
    try {
      setStatus(await offlineZim.status());
    } catch (error) {
      setMessage(String(error));
    }
  }, []);

  useFocusEffect(
    React.useCallback(() => {
      refresh();
    }, [refresh]),
  );

  const importArchive = async () => {
    setBusy(true);
    setMessage('');
    try {
      const imported = await offlineZim.chooseArchive();
      if (imported) {
        setMessage(
          `Імпортовано ${imported.name ?? 'ZIM'}: ${imported.articles ?? 0} статей.`,
        );
        await refresh();
      }
    } catch (error) {
      setMessage(`Помилка імпорту: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  };

  const removeArchive = (archive: ArchiveInfo) => {
    Alert.alert(
      'Видалити архів?',
      `${archive.name}\n\nІмпортована копія буде видалена зі сховища Jarvis. Оригінальний файл поза застосунком не змінюється.`,
      [
        {text: 'Скасувати', style: 'cancel'},
        {
          text: 'Видалити',
          style: 'destructive',
          onPress: async () => {
            setBusy(true);
            try {
              await offlineZim.deleteArchive(archive.id);
              setResults([]);
              setArticle(null);
              await refresh();
            } catch (error) {
              setMessage(`Не вдалося видалити: ${String(error)}`);
            } finally {
              setBusy(false);
            }
          },
        },
      ],
    );
  };

  const search = async () => {
    const term = query.trim();
    if (term.length < 2) return;
    setBusy(true);
    setArticle(null);
    setMessage('');
    try {
      const hits = await offlineZim.search(term, 30);
      setResults(hits);
      if (!hits.length) setMessage('Нічого не знайдено у завантажених архівах.');
    } catch (error) {
      setMessage(`Помилка пошуку: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  };

  const openArticle = async (item: OfflineArticle) => {
    setBusy(true);
    setMessage('');
    try {
      setArticle(
        await offlineZim.readArticle(item.archiveId, item.path, item.title),
      );
    } catch (error) {
      setMessage(`Не вдалося відкрити статтю: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  };

  if (article) {
    return (
      <ScrollView
        style={{flex: 1, backgroundColor: theme.colors.background}}
        contentContainerStyle={{padding: 18, gap: 12}}>
        <Button icon="arrow-left" onPress={() => setArticle(null)}>
          До результатів
        </Button>
        <Text variant="headlineSmall" selectable>
          {article.title}
        </Text>
        <Text
          variant="labelMedium"
          style={{color: theme.colors.onSurfaceVariant}}>
          {article.archive}
        </Text>
        <Divider />
        <Text variant="bodyLarge" selectable style={{lineHeight: 26}}>
          {article.text || 'У цій статті немає текстового вмісту.'}
        </Text>
        {article.truncated && (
          <Text
            variant="bodySmall"
            style={{color: theme.colors.onSurfaceVariant}}>
            Дуже довгу статтю скорочено для швидкої роботи вбудованого читача.
          </Text>
        )}
      </ScrollView>
    );
  }

  const archives = status.archives ?? [];
  return (
    <ScrollView
      style={{flex: 1, backgroundColor: theme.colors.background}}
      contentContainerStyle={{padding: 16, gap: 14}}>
      <Text variant="headlineSmall">Завантаження статей</Text>
      <Text
        variant="bodyMedium"
        style={{color: theme.colors.onSurfaceVariant}}>
        ZIM-архіви копіюються у сховище Jarvis і читаються напряму, без моделі ШІ.
        У чаті використовуйте @Вікі, коли хочете передати ці статті моделі.
      </Text>

      <Button
        mode="contained-tonal"
        icon="archive-plus"
        loading={busy}
        disabled={busy}
        onPress={importArchive}>
        Додати ZIM
      </Button>

      {archives.map(archive => (
        <Card key={archive.id} mode="outlined">
          <Card.Content style={{gap: 5}}>
            <Text variant="titleMedium" selectable>
              {archive.name}
            </Text>
            <Text
              variant="bodySmall"
              style={{color: theme.colors.onSurfaceVariant}}>
              {archive.articles.toLocaleString()} статей · {formatBytes(archive.bytes)}
            </Text>
          </Card.Content>
          <Card.Actions>
            <Button
              textColor={theme.colors.error}
              onPress={() => removeArchive(archive)}>
              Видалити
            </Button>
          </Card.Actions>
        </Card>
      ))}

      {status.ready ? (
        <>
          <Searchbar
            placeholder="Знайти статтю"
            value={query}
            onChangeText={setQuery}
            onSubmitEditing={search}
            returnKeyType="search"
          />
          <Button
            mode="contained"
            onPress={search}
            loading={busy}
            disabled={busy || query.trim().length < 2}>
            Пошук
          </Button>
        </>
      ) : (
        <Text variant="bodyMedium">
          Додайте хоча б один .zim архів, щоб читати статті офлайн.
        </Text>
      )}

      {busy && <ActivityIndicator />}

      {results.map((item, index) => (
        <TouchableOpacity
          key={`${item.archiveId}-${item.path}-${index}`}
          onPress={() => openArticle(item)}
          accessibilityRole="button">
          <Card mode="outlined">
            <Card.Content style={{gap: 6}}>
              <Text variant="titleMedium">{item.title}</Text>
              <Text
                variant="labelSmall"
                style={{color: theme.colors.primary}}>
                {item.archive}
              </Text>
              <Text numberOfLines={5} variant="bodySmall">
                {item.excerpt}
              </Text>
            </Card.Content>
          </Card>
        </TouchableOpacity>
      ))}

      {!!message && (
        <View style={{paddingVertical: 6}}>
          <Text selectable>{message}</Text>
        </View>
      )}
    </ScrollView>
  );
};

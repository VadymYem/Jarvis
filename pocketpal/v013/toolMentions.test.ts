import {
  extractToolMentions,
  stripToolMentions,
} from '../toolMentions';

describe('Jarvis @ tool mentions', () => {
  it('recognizes localized and English aliases', () => {
    expect(extractToolMentions('@Вікі Кобзар')).toContain('wiki');
    expect(extractToolMentions('@web latest Android')).toContain('web');
    expect(extractToolMentions('@Python створи файл')).toContain('python');
    expect(extractToolMentions('@Зображення листівка')).toContain('image');
    expect(extractToolMentions('@Файли прочитай PDF')).toContain('files');
  });

  it('strips only known tool mentions from retrieval queries', () => {
    expect(stripToolMentions('@Вікі Леся Українка')).toBe('Леся Українка');
    expect(stripToolMentions('mail me at @someone')).toBe('mail me at @someone');
  });
});

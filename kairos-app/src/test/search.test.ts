import { describe, expect, it } from 'vitest';
import {
  buildSearchIndex,
  normalizeFa,
  scoreMatch,
  searchEverything,
  sectionItems,
  type SearchableItem,
} from '@/lib/search';
import type { Goal, Habit, Task } from '@/types/domain';

/**
 * Persian search has to survive what Persian keyboards actually produce: two
 * forms of yeh and kaf, an invisible ZWNJ, and diacritics that some layouts add.
 */

const item = (overrides: Partial<SearchableItem> = {}): SearchableItem => ({
  id: 'x',
  kind: 'task',
  title: 'خرید کتاب',
  tab: 'tasks',
  ...overrides,
});

describe('Global search — normalisation and ranking', () => {
  it('treats Arabic yeh/kaf and the ZWNJ as the same characters', () => {
    expect(normalizeFa('مي‌شود')).toBe('می شود');
    expect(normalizeFa('كتاب')).toBe('کتاب');
    expect(normalizeFa('  خرید   کتاب ')).toBe('خرید کتاب');
  });

  it('ranks a title match above a description match', () => {
    const title = item({ id: 'a', title: 'کتاب' });
    const body = item({ id: 'b', title: 'یادداشت', subtitle: 'کتاب' });
    expect(scoreMatch('کتاب', title)).toBeGreaterThan(scoreMatch('کتاب', body));
    expect(searchEverything('کتاب', [body, title])[0].id).toBe('a');
  });

  it('ranks a word start above a mid-word hit', () => {
    const wordStart = item({ id: 'a', title: 'خرید هفتگی' });
    const midWord = item({ id: 'b', title: 'آخر ماه' });
    expect(searchEverything('خر', [midWord, wordStart])[0].id).toBe('a');
  });

  it('requires every token, so two words cannot match two different things', () => {
    const both = item({ id: 'a', title: 'خرید کتاب' });
    const onlyOne = item({ id: 'b', title: 'خرید نان' });
    const results = searchEverything('خرید کتاب', [both, onlyOne]);
    expect(results.map((r) => r.id)).toEqual(['a']);
  });

  it('drops finished work below open work of the same relevance', () => {
    const open = item({ id: 'a', title: 'مرور هفتگی' });
    const done = item({ id: 'b', title: 'مرور هفتگی', done: true });
    expect(searchEverything('مرور هفتگی', [done, open])[0].id).toBe('a');
  });

  it('returns nothing for an empty or unmatchable query', () => {
    expect(searchEverything('', sectionItems())).toEqual([]);
    expect(searchEverything('   ', sectionItems())).toEqual([]);
    expect(searchEverything('قققق', sectionItems())).toEqual([]);
  });

  it('makes the seven sections searchable by name and by keyword', () => {
    const results = searchEverything('تقویم', sectionItems());
    expect(results[0].tab).toBe('calendar');

    // A keyword, not the title: "گزارش" must reach analytics.
    expect(searchEverything('گزارش', sectionItems())[0].tab).toBe('analytics');
  });

  it('indexes live rows and skips archived or foreign ones', () => {
    const tasks = [
      { id: 't1', title: 'تمرین پیانو', description: '', archived: false, status: 'todo', dueDate: null, category: 'personal', quadrant: 'q2' },
      { id: 't2', title: 'کار بایگانی', description: '', archived: true, status: 'todo', dueDate: null, category: 'personal', quadrant: 'q2' },
    ] as unknown as Task[];
    const habits = [
      { id: 'h1', name: 'مطالعهٔ شبانه', description: '۲۰ صفحه', archived: false },
      { id: 'h2', name: 'عادت بایگانی', description: '', archived: true },
    ] as unknown as Habit[];
    const goals = [{ id: 'g1', title: 'کتاب نوشتن', description: '', completed: false, category: 'personal' }] as unknown as Goal[];

    const index = buildSearchIndex({ tasks, habits, goals });
    const titles = index.map((entry) => entry.title);

    expect(titles).toContain('تمرین پیانو');
    expect(titles).not.toContain('کار بایگانی');
    expect(titles).toContain('مطالعهٔ شبانه');
    expect(titles).not.toContain('عادتبایگانی');
    expect(titles).toContain('کتاب نوشتن');

    // A habit name is found even when the query drops the ZWNJ.
    expect(searchEverything('مطالعه شبانه', index)[0].id).toBe('habit:h1');
    // Descriptions are searchable too.
    expect(searchEverything('۲۰ صفحه', index)[0].id).toBe('habit:h1');
  });

  it('caps the result list', () => {
    const many = Array.from({ length: 40 }, (_, i) => item({ id: `t${i}`, title: `کار ${i}` }));
    expect(searchEverything('کار', many)).toHaveLength(12);
    expect(searchEverything('کار', many, 3)).toHaveLength(3);
  });
});

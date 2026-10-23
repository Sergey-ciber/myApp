/**
 * Общие константы и помощники «Игры в ассоциации».
 *
 * Пирамида: 16 + 8 + 4 + 2 + 1 = 31 слово.
 * Подсказка для слова i в колонке N+1 — пара слов (2i-1, 2i) из колонки N (нумерация с 1).
 */

export const COLUMN_SIZES = [16, 8, 4, 2, 1]

export const COLUMN_HEADERS = [
  { title: 'Колонка 1', subtitle: '16 стартовых слов' },
  { title: 'Колонка 2', subtitle: '8 ответов' },
  { title: 'Колонка 3', subtitle: '4 ответа' },
  { title: 'Колонка 4', subtitle: '2 ответа' },
  { title: 'Колонка 5', subtitle: '1 ответ' },
]

/**
 * Сколько строк сетки занимает ячейка колонки.
 * Колонка 1 — одна строка-пара, дальше 2, 4, 8: карточка ответа центрируется
 * по двум своим подсказкам из предыдущей колонки.
 */
export const COLUMN_SPANS = [1, 1, 2, 4, 8]

/** Сравнение ответов: без учёта регистра и пробелов по краям. */
export const normalizeWord = (value) => (value || '').trim().toLowerCase()

/** Пустая форма: 5 колонок нужных размеров. */
export const emptyColumns = () => COLUMN_SIZES.map((size) => Array(size).fill(''))

/** Копия колонок из ответа API — чтобы редактирование не меняло исходный объект. */
export const cloneColumns = (columns) =>
  COLUMN_SIZES.map((size, index) => {
    const source = columns?.[index] || []
    return Array.from({ length: size }, (_, position) => source[position] || '')
  })

/**
 * Подпись-подсказка для карточки: какая пара слов предыдущей колонки её открывает.
 * columnIndex — индекс колонки карточки (1..4), position — индекс карточки (0-based).
 */
export function hintLabel(columnIndex, position) {
  const first = position * 2 + 1
  const second = position * 2 + 2
  const noun = columnIndex === 1 ? 'Слово' : 'Ответ'
  return `Подсказка: ${noun} ${first} + ${noun} ${second}`
}

/** Пары слов колонки 1: [[w1, w2], [w3, w4], ...] — 8 пар. */
export function toPairs(words) {
  const pairs = []
  for (let index = 0; index < words.length; index += 2) {
    pairs.push([words[index], words[index + 1]])
  }
  return pairs
}

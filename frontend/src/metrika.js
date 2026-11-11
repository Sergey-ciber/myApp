/**
 * Яндекс.Метрика для SPA.
 *
 * Счётчик (tag.js) подключается в index.html как можно раньше, но его
 * инициализация отложена до момента, когда приложение уже определило
 * целевой маршрут. Иначе счётчик успевает отправить хит на промежуточный
 * URL редиректа ("/" → "/associations" → "/login") и в отчёт попадают
 * лишние просмотры.
 */

export const COUNTER_ID = 114185830

// Настройки из стандартного сниппета Метрики.
const INIT_OPTIONS = {
  ssr: true,
  webvisor: true,
  clickmap: true,
  ecommerce: 'dataLayer',
  accurateTrackBounce: true,
  trackLinks: true,
}

// Небольшая пауза перед первым хитом: за это время отрабатывают
// синхронные редиректы react-router, и хит уходит уже на целевой URL.
const FIRST_HIT_DELAY_MS = 150

let initialized = false
let lastUrl = null
let firstHitTimer = null

function ym(...args) {
  try {
    // ym — заглушка из сниппета, поэтому вызов безопасен, даже если
    // tag.js не загрузился (блокировщик рекламы, офлайн).
    if (typeof window.ym === 'function') window.ym(COUNTER_ID, ...args)
  } catch {
    // Аналитика не должна ломать приложение.
  }
}

/** Первый просмотр: инициализируем счётчик сразу на целевом URL. */
function initCounter() {
  firstHitTimer = null
  if (initialized) return
  initialized = true
  lastUrl = window.location.href
  ym('init', { ...INIT_OPTIONS, referrer: document.referrer, url: lastUrl })
}

/** Если посетитель уходит раньше паузы — отправляем хит немедленно. */
function flushFirstHit() {
  if (!initialized && firstHitTimer !== null) {
    clearTimeout(firstHitTimer)
    initCounter()
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', flushFirstHit)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushFirstHit()
  })
}

/** Учесть просмотр текущей страницы (вызывается на каждом маршруте). */
export function trackPageview(url = window.location.href) {
  if (typeof window === 'undefined') return

  if (initialized) {
    if (lastUrl === url) return
    lastUrl = url
    ym('hit', url)
    return
  }

  // Первый просмотр: ждём, пока маршрут устоится после редиректов.
  clearTimeout(firstHitTimer)
  firstHitTimer = setTimeout(initCounter, FIRST_HIT_DELAY_MS)
}

const PREFIX = 'assoc:'

/**
 * Состояние раздела «Игра в ассоциации» привязано к пользователю и хранится
 * на сервере. При входе/выходе чистим локальные ключи, чтобы следующая сессия
 * ни при каких условиях не увидела состояние предыдущей.
 */
export function clearAssocStorage() {
  try {
    Object.keys(localStorage)
      .filter((key) => key.startsWith(PREFIX))
      .forEach((key) => localStorage.removeItem(key))
  } catch {
    // localStorage может быть недоступен — не критично
  }
}

/** Ключ настройки раздела для конкретного пользователя: assoc:mode:5. */
export function userScopedKey(name, userId) {
  return userId ? `${PREFIX}${name}:${userId}` : null
}

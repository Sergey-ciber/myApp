import { useCallback, useEffect, useRef, useState } from 'react'

/** Сколько держится красная/зелёная рамка после ответа. */
export const ANSWER_FEEDBACK_DURATION = 1500

/**
 * Кратковременная подсветка полей ответа.
 *
 * Ключ поля — произвольная строка: для обычного уровня это id вопроса,
 * для пирамиды — "колонка:позиция". Хук хранит карту { [key]: 'correct' | 'wrong' }
 * и сам снимает подсветку через заданное время, не оставляя таймеров
 * после размонтирования компонента.
 */
export default function useAnswerFeedback(duration = ANSWER_FEEDBACK_DURATION) {
  const [feedback, setFeedback] = useState({})
  const timers = useRef({})

  const clear = useCallback((key) => {
    if (timers.current[key]) {
      clearTimeout(timers.current[key])
      delete timers.current[key]
    }
    setFeedback((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }, [])

  const clearAll = useCallback(() => {
    Object.values(timers.current).forEach(clearTimeout)
    timers.current = {}
    setFeedback({})
  }, [])

  const show = useCallback((key, type) => {
    if (key == null) return
    if (timers.current[key]) clearTimeout(timers.current[key])
    setFeedback((prev) => (prev[key] === type ? prev : { ...prev, [key]: type }))
    timers.current[key] = setTimeout(() => {
      delete timers.current[key]
      setFeedback((prev) => {
        if (!prev[key]) return prev
        const next = { ...prev }
        delete next[key]
        return next
      })
    }, duration)
  }, [duration])

  useEffect(() => () => {
    Object.values(timers.current).forEach(clearTimeout)
    timers.current = {}
  }, [])

  return { feedback, show, clear, clearAll }
}

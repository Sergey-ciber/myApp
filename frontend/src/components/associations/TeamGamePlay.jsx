import { useCallback, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import {
  disconnectTeamGame, endTeamGame, getTeamGameState, getTeamGameStatistics, heartbeatTeamGame, joinTeamGame, leaveTeamGame,
  revealTeamHint, revealTeamHintById, submitPyramidTeamAnswer, submitTeamAnswer,
} from '../../api/teamGames'
import { apiMessage } from '../../api/client'
import { formatTime } from './constants'
import GameBoard from './GameBoard'
import GameStatisticsTable from './GameStatisticsTable'

const WS_URL = import.meta.env.VITE_WS_URL || '/ws'

function mediaKind(url) {
  const lower = (url || '').toLowerCase()
  if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) return 'video'
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) return 'audio'
  if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) return 'image'
  return 'other'
}

function renderMedia(urls, onImageClick) {
  if (!Array.isArray(urls) || urls.length === 0) return null
  return (
    <div className="assoc-normal-media" style={{ flexDirection: 'column' }}>
      {urls.map((url, idx) => {
        const kind = mediaKind(url)
        if (kind === 'image') return <img key={idx} src={url} alt="Медиа" style={{ cursor: 'zoom-in' }} onClick={() => onImageClick?.(url)} />
        if (kind === 'audio') return <audio key={idx} src={url} controls />
        if (kind === 'video') return <video key={idx} src={url} controls />
        return null
      })}
    </div>
  )
}

function finishReasonText(reason) {
  switch ((reason || '').toLowerCase()) {
    case 'host_ended':
      return 'Игра завершена ведущим'
    case 'time_expired':
      return 'Время игры истекло'
    case 'completed':
      return 'Команда прошла игру!'
    default:
      return 'Игра завершена'
  }
}

function shortQuestionText(text, max = 42) {
  const clean = String(text || '').replace(/\s+/g, ' ').trim()
  if (clean.length <= max) return clean
  return `${clean.slice(0, max - 1)}…`
}

function questionStatusText(questionState) {
  if (!questionState) return 'не решён'
  if (questionState.isSolved) return 'решён'
  if (Array.isArray(questionState.revealedHintIds) && questionState.revealedHintIds.length > 0) {
    return 'есть подсказки'
  }
  return 'не решён'
}

function questionStorageKey(sessionId) {
  return `teamGameQuestion:${sessionId}`
}

/**
 * Ответы вопроса из состояния партии — строго по question_id.
 *
 * Возвращает пустой список, если вопрос не найден или не решён.
 */
function answersOfQuestion(state, questionId) {
  if (!state || questionId == null) return []
  const qState = state.questionStates?.[String(questionId)]
  return Array.isArray(qState?.answers) ? qState.answers : []
}

/** Относится ли событие к вопросу, который сейчас открыт у игрока. */
function isSelectedQuestion(selectedQuestionId, questionId) {
  if (selectedQuestionId == null || questionId == null) return false
  return String(selectedQuestionId) === String(questionId)
}

/**
 * Текст последнего угаданного ответа вопроса — только для выбранного вопроса.
 *
 * Если событие относится к другому вопросу, ответ не показывается:
 * игрок увидит его, когда переключится на этот вопрос.
 */
function answerOfQuestion(state, questionId) {
  if (!state || questionId == null) return ''
  const answers = answersOfQuestion(state, questionId)
  return answers.length > 0 ? answers[answers.length - 1] : ''
}

/**
 * Игровой экран командной партии (co-op).
 *
 * Все действия уходят на сервер, а сервер рассылает события комнате
 * /topic/team_session_{sessionId}: sync_correct_answer, question_solved,
 * hint_revealed, sync_level_up, PRESENCE, end_team_game.
 *
 * Вопросы уровня независимы: каждый игрок может выбрать свой вопрос
 * (выбор хранится локально), а открытые подсказки и решения общие для команды.
 *
 * Права:
 *  - хост (тот, кто запустил игру) видит «Завершить игру»;
 *  - остальные участники видят «Покинуть игру» — партия при этом продолжается.
 */
export default function TeamGamePlay({
  sessionId, initialState, onExit, currentUserId, onMissing, onEnded,
}) {
  const [state, setState] = useState(initialState)
  const [loading, setLoading] = useState(!initialState)
  const [error, setError] = useState('')
  const [answer, setAnswer] = useState('')
  const [checking, setChecking] = useState(false)
  const [hintLoading, setHintLoading] = useState(false)
  const [wrong, setWrong] = useState(false)
  const [flash, setFlash] = useState('')
  const [hintNotice, setHintNotice] = useState(null)
  const [ending, setEnding] = useState(false)
  const [showEndConfirm, setShowEndConfirm] = useState(false)
  const [finishedFromServer, setFinishedFromServer] = useState(false)
  const [fullscreenImage, setFullscreenImage] = useState(null)
  const [solvedPyramid, setSolvedPyramid] = useState({})
  const [statistics, setStatistics] = useState(null)
  const [statisticsLoading, setStatisticsLoading] = useState(false)
  const [statisticsError, setStatisticsError] = useState('')
  const [selectedQuestionId, setSelectedQuestionId] = useState(() => {
    try {
      return localStorage.getItem(questionStorageKey(sessionId))
    } catch {
      return null
    }
  })
  const expiryFetchRef = useRef(null)
  const wrongTimer = useRef(null)
  const flashTimer = useRef(null)
  const hintTimer = useRef(null)
  // Текущий выбранный вопрос и состояние — нужны обработчику WebSocket,
  // чтобы не показывать ответ чужого вопроса.
  const selectedQuestionIdRef = useRef(selectedQuestionId)
  const stateRef = useRef(state)
  // Авторитетные флаги «я — host», посчитанные сервером: { userId: true/false }.
  // Приходят и в REST-ответах (currentUserHost), и в broadcast-событиях.
  const [hostFlags, setHostFlags] = useState({})
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    selectedQuestionIdRef.current = selectedQuestionId
  }, [selectedQuestionId])

  /**
   * Применяет состояние партии.
   *
   * Флаг роли host берём только из персональных ответов сервера
   * (REST). В broadcast-событиях currentUserHost относится к автору
   * события, поэтому там роль приходит отдельной картой isHostByUser.
   */
  const applyState = useCallback((s, personal = false) => {
    if (!s) return
    stateRef.current = s
    setState(s)
    setLoading(false)
    if (personal && (s.currentUserHost === true || s.currentUserHost === false)) {
      setHostFlags((prev) => ({ ...prev, [String(currentUserId)]: s.currentUserHost }))
    }
  }, [currentUserId])

  const showFlash = useCallback((text, ms = 2500) => {
    setFlash(text)
    if (flashTimer.current) clearTimeout(flashTimer.current)
    flashTimer.current = setTimeout(() => setFlash(''), ms)
  }, [])

  const showHint = useCallback((payload) => {
    const text = payload.text || payload.firstLetter
    if (!text && !payload.answer) return
    setHintNotice({ text: text || payload.answer, imageUrl: payload.imageUrl, penaltySeconds: payload.penaltySeconds })
    if (hintTimer.current) clearTimeout(hintTimer.current)
    hintTimer.current = setTimeout(() => setHintNotice(null), 8000)
  }, [])

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  /**
   * Клиентский отсчёт — только подсказка. Когда ends_at прошёл, перезапрашиваем
   * состояние у сервера. Финальный статус выставляет только серверный таймер.
   */
  useEffect(() => {
    const status = String(state?.status || '').toLowerCase()
    const endsAtMs = state?.endsAt ? new Date(state.endsAt).getTime() : null
    if (status !== 'active' || !endsAtMs || now < endsAtMs) return
    const lastAttempt = expiryFetchRef.current || 0
    if (now - lastAttempt < 5000) return
    expiryFetchRef.current = now
    getTeamGameState(sessionId).then((s) => applyState(s, true)).catch(() => {})
  }, [now, state?.status, state?.endsAt, sessionId, applyState])

  /**
   * Статистику времени после финиша запрашиваем только у сервера.
   * Небольшие повторы нужны на случай, если финиш пришёл broadcasтом
   * до коммита транзакции завершения сессии.
   */
  const loadStatistics = useCallback(async () => {
    setStatisticsLoading(true)
    setStatisticsError('')
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        const data = await getTeamGameStatistics(sessionId)
        const empty = !data?.levels || data.levels.length === 0
        if (empty && attempt < 2) {
          await new Promise((resolve) => setTimeout(resolve, 600))
          continue
        }
        setStatistics(data)
        setStatisticsLoading(false)
        return
      } catch (err) {
        if (attempt === 2) {
          setStatisticsError(apiMessage(err))
          setStatisticsLoading(false)
          return
        }
        await new Promise((resolve) => setTimeout(resolve, 600))
      }
    }
  }, [sessionId])

  const finishedNow = String(state?.status || '').toLowerCase() === 'finished' || finishedFromServer
  useEffect(() => {
    if (finishedNow) loadStatistics()
  }, [finishedNow, loadStatistics])

  // Подключение к партии: получаем полный слепок состояния.
  useEffect(() => {
    if (initialState) {
      applyState(initialState, true)
      joinTeamGame(sessionId)
        .then((s) => applyState(s, true))
        .catch((err) => {
          // Сессия исчезла — возвращаем игрока к списку игр.
          // Завершённая сессия не ошибка: join вернёт её актуальное состояние.
          if (err?.response?.status === 404 || err?.response?.status === 409) {
            if (onMissing) onMissing()
          }
        })
    } else {
      getTeamGameState(sessionId)
        .then((s) => applyState(s, true))
        .catch((err) => {
          // Сессия исчезла — возвращаем игрока к экрану игры без ошибки.
          if (err?.response?.status === 404) {
            if (onMissing) onMissing()
            return
          }
          setError(apiMessage(err))
        })
        .finally(() => setLoading(false))
    }
    // Периодическое обновление времени присутствия, чтобы статус «онлайн» не устаревал.
    const heartbeat = setInterval(() => {
      heartbeatTeamGame(sessionId).catch(() => {})
    }, 30000)
    return () => {
      clearInterval(heartbeat)
      // ВАЖНО: не выходим из игры при размонтировании.
      // Размонтирование бывает и при перезагрузке страницы — это не выход.
      // Явный выход выполняется только по кнопке «Покинуть игру» (handleLeave).
      disconnectTeamGame(sessionId).catch(() => {})
    }
  }, [sessionId, initialState, applyState, onMissing])

  // Подписка на комнату партии: все изменения приходят сюда.
  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      connectHeaders: {
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      onConnect: () => {
        stompClient.subscribe('/topic/team_session_' + sessionId, (message) => {
          try {
            const payload = JSON.parse(message.body)
            // Флаги host от сервера: смотрим свой user_id.
            if (payload.isHostByUser && typeof payload.isHostByUser === 'object') {
              setHostFlags(payload.isHostByUser)
            }
            if (payload.state) applyState(payload.state)

            switch (payload.type) {
              case 'host_changed': {
                // Сменился владелец сессии: роль каждого участника
                // определяется сервером, клиент только применяет её.
                showFlash(payload.newHostUsername
                  ? `👑 Хостом теперь ${payload.newHostUsername}`
                  : '👑 Владелец партии изменился')
                break
              }
              case 'sync_correct_answer': {
                // Слово угадано. Текст ответа берём из своего состояния и
                // показываем только если открыт именно этот вопрос.
                setWrong(false)
                if (String(payload.userId) === String(currentUserId)) {
                  setAnswer('')
                }
                // Обновляем solvedPyramid для пирамиды (ASSOCIATIONS уровень).
                // Храним каноничный текст ответа с сервера, чтобы поле
                // оставалось заполненным и после перезагрузки страницы.
                if (payload.column != null && payload.position != null) {
                  const key = `${payload.column}:${payload.position}`
                  const wordText = typeof payload.answer === 'string' && payload.answer.trim() !== ''
                    ? payload.answer
                    : (payload.state?.solved?.[key] || '')
                  setSolvedPyramid((prev) => ({
                    ...prev,
                    [key]: wordText || (typeof prev[key] === 'string' ? prev[key] : ''),
                  }))
                }
                const shown = isSelectedQuestion(selectedQuestionIdRef.current, payload.questionId)
                  ? answerOfQuestion(payload.state || stateRef.current, payload.questionId)
                  : ''
                showFlash(shown
                  ? `✅ ${payload.username || 'Игрок'} разгадал: ${shown}`
                  : `✅ ${payload.username || 'Игрок'} разгадал ответ`)
                break
              }
              case 'question_solved': {
                if (String(payload.userId) === String(currentUserId)) {
                  setAnswer('')
                }
                const shown = isSelectedQuestion(selectedQuestionIdRef.current, payload.questionId)
                  ? answerOfQuestion(payload.state || stateRef.current, payload.questionId)
                  : ''
                showFlash(shown
                  ? `🎯 Вопрос решён: ${shown}`
                  : '🎯 Вопрос решён')
                break
              }
              case 'hint_revealed':
              case 'sync_hint_used':
                showHint(payload)
                showFlash(`💡 ${payload.username || 'Команда'} открыл(а) подсказку`
                  + (payload.penaltySeconds ? ` (+${payload.penaltySeconds} сек.)` : ''))
                break
              case 'sync_level_up':
                setAnswer('')
                setSolvedPyramid({})
                showFlash('🎉 Уровень пройден! Открыт следующий уровень')
                break
              case 'sync_state':
                if (payload.state) applyState(payload.state)
                break
              case 'ANSWER_WRONG':
                if (String(payload.userId) === String(currentUserId)) {
                  setWrong(true)
                  setAnswer('')
                  if (wrongTimer.current) clearTimeout(wrongTimer.current)
                  wrongTimer.current = setTimeout(() => setWrong(false), 2000)
                }
                break
              case 'end_team_game':
                setFinishedFromServer(true)
                if (!payload.state) {
                  // Событие без слепка (старый формат) — добираем актуальное состояние.
                  getTeamGameState(sessionId).then((s) => applyState(s, true)).catch(() => {})
                }
                showFlash(finishReasonText(payload.endReason || payload.reason))
                if (onEnded) onEnded(payload.state)
                break
              case 'DISCONNECTED':
                if (String(payload.userId) === String(currentUserId)) {
                  showFlash('Вы отключились от партии')
                }
                break
              default:
                break
            }
          } catch (e) {
            // некорректное сообщение игнорируем
          }
        })
        // Клиенты, присоединившиеся позже, запрашивают полный слепок состояния
        stompClient.publish({ destination: '/app/team-game/request-sync', body: JSON.stringify({ sessionId }) })
      },
    })
    stompClient.activate()
    return () => {
      if (wrongTimer.current) clearTimeout(wrongTimer.current)
      if (flashTimer.current) clearTimeout(flashTimer.current)
      if (hintTimer.current) clearTimeout(hintTimer.current)
      stompClient.deactivate()
    }
  }, [sessionId, currentUserId, applyState, showFlash, showHint, onEnded])

  const game = state?.game
  const levels = game?.levels || []
  const levelIndex = state?.currentLevelIndex || 0
  const level = levels[levelIndex]
  const penalty = state?.penaltyTime || 0
  const events = state?.events || []
  const memberNames = state?.memberNames || {}
  const onlineUserIds = state?.onlineUserIds || []
  /**
   * Роль host определяется сервером и приходит флагом по нашему user_id.
   * Никаких вычислений на клиенте: один broadcast может содержать разные
   * флаги для разных участников, и каждый берёт свой.
   */
  const isHost = hostFlags[String(currentUserId)] === true
  const hostUsername = state?.hostUsername
  const status = String(state?.status || '').toLowerCase()
  const isFinished = status === 'finished' || finishedFromServer
  const endReason = state?.endReason || ''
  const finishTitle = finishReasonText(endReason)

  // Таймеры (на сервере - независимы от подключений)
  const startTimeMs = state?.startTime
    ? new Date(state.startedAt || state.startTime).getTime()
    : Date.now()
  const gameElapsedSec = Math.max(0, Math.floor((now - startTimeMs) / 1000))
  const gameEndsAtMs = state?.endsAt ? new Date(state.endsAt).getTime() : null
  const gameRemainingSec = gameEndsAtMs ? Math.max(0, Math.floor((gameEndsAtMs - now) / 1000)) : null
  const levelEndTimeMs = state?.levelEndTime ? new Date(state.levelEndTime).getTime() : null
  const levelTimeLimitSec = level?.timeLimitMinutes ? Math.round(level.timeLimitMinutes * 60) : (levelEndTimeMs ? Math.max(0, Math.floor((levelEndTimeMs - startTimeMs) / 1000)) : null)
  const levelRemainingSec = levelEndTimeMs ? Math.max(0, Math.floor((levelEndTimeMs - now) / 1000)) : levelTimeLimitSec

  const questions = level?.questions || []
  const questionStates = state?.questionStates || {}

  // Выбранный вопрос хранится локально: у каждого игрока он свой.
  const currentQuestion = questions.find((q) => String(q.id) === String(selectedQuestionId)) || questions[0]
  const currentQuestionState = currentQuestion ? questionStates[String(currentQuestion.id)] : null
  const currentHints = currentQuestionState?.hints || []
  const currentSolved = currentQuestionState?.isSolved === true
  const allHintsRevealed = currentQuestionState?.allHintsRevealed === true

  /**
   * Подсказки вопроса с вычисленным состоянием.
   *
   * Доступность определяется только временем (availableAt от сервера),
   * поэтому открыть можно любую подсказку, время которой наступило,
   * не открывая предыдущие.
   */
  const hintRows = currentHints.map((hint, idx) => {
    const availableAtMs = hint.availableAt ? new Date(hint.availableAt).getTime() : null
    // Доступность определяем ТОЛЬКО по серверному availableAt и текущему client now.
    // Не доверяем кэшированному hint.isAvailable — оно могло быть вычислено
    // на сервере в прошлый раз, когда время ещё не пришло.
    const timeReached = availableAtMs == null || now >= availableAtMs
    const isAvailable = timeReached
    const remainingSec = availableAtMs == null
      ? 0
      : Math.max(0, Math.ceil((availableAtMs - now) / 1000))
    let status = 'unavailable'
    if (hint.isRevealed) status = 'revealed'
    else if (isAvailable) status = 'available'
    return {
      ...hint,
      index: idx,
      status,
      isAvailable,
      remainingSec,
    }
  })

  const availableHints = hintRows.filter((h) => h.status === 'available')
  // Ближайшая по времени недоступная подсказка — отсчёт показываем всегда,
  // независимо от того, открыты ли предыдущие.
  const upcomingHint = hintRows
    .filter((h) => h.status === 'unavailable')
    .sort((a, b) => a.remainingSec - b.remainingSec)[0] || null
  const nextHintRemainingSec = upcomingHint ? upcomingHint.remainingSec : 0

  // Количество угаданных ответов — из состояния вопроса (по question_id).
  const questionSolvedCount = currentQuestionState?.matchedCount ?? 0
  const passThreshold = currentQuestionState?.passThreshold || currentQuestion?.passThreshold || 0

  /**
   * Угаданные ответы — строго по выбранному вопросу.
   *
   * Сервер отдаёт их в questionStates[questionId].answers, поэтому ответы
   * других вопросов сюда не попадают и в DOM не попадают тоже.
   */
  const solvedWords = currentQuestionState?.answers || []

  // При смене уровня или загрузке состояния возвращаемся к сохранённому вопросу,
  // если он существует; иначе — к первому вопросу уровня.
  useEffect(() => {
    if (!questions.length) return
    const exists = questions.some((q) => String(q.id) === String(selectedQuestionId))
    if (!exists) {
      setSelectedQuestionId(String(questions[0].id))
    }
  }, [questions, selectedQuestionId])

  // Синхронизация solvedPyramid при смене уровня и после обновления состояния.
  // Формат ключей совпадает с GameBoard.jsx: "column:position" (1-based).
  // Сервер отдаёт текст уже разгаданного слова, поэтому поле можно
  // восстановить заполненным, подсвеченным и заблокированным после F5.
  useEffect(() => {
    if (level?.type !== 'ASSOCIATIONS') {
      setSolvedPyramid({})
      return
    }
    const levelSolved = state?.solved || {}
    setSolvedPyramid((prev) => {
      const pyramidSolved = {}
      for (const [key, wordText] of Object.entries(levelSolved)) {
        if (!/^\d+:\d+$/.test(key)) continue
        const text = typeof wordText === 'string' ? wordText : ''
        // Если сервер ещё отдаёт только ключи без текста, не теряем
        // уже показанное слово, полученное из WebSocket-события.
        pyramidSolved[key] = text || (typeof prev[key] === 'string' ? prev[key] : '')
      }
      return pyramidSolved
    })
  }, [levelIndex, level?.type, state?.solved])

  // Смена вопроса очищает введённый ответ и ошибку: блок вопроса
  // отображается целиком для выбранного вопроса.
  useEffect(() => {
    setAnswer('')
    setError('')
    setWrong(false)
  }, [selectedQuestionId, levelIndex])

  useEffect(() => {
    if (selectedQuestionId == null) return
    try {
      localStorage.setItem(questionStorageKey(sessionId), String(selectedQuestionId))
    } catch {
      // localStorage может быть недоступен — выбор просто не сохранится
    }
  }, [sessionId, selectedQuestionId])

  const handleSubmit = async () => {
    if (!currentQuestion || checking || currentSolved) return
    const trimmed = (answer || '').trim()
    if (!trimmed) return
    setChecking(true)
    setWrong(false)
    try {
      const res = await submitTeamAnswer(sessionId, currentQuestion.id, trimmed)
      if (res) applyState(res, true)
      setAnswer('')
    } catch (err) {
      setWrong(true)
      setError(apiMessage(err))
      setTimeout(() => setWrong(false), 2000)
    } finally {
      setChecking(false)
    }
  }

  /** Отправить ответ для пирамиды (ASSOCIATIONS уровень). */
  const handleSubmitPyramid = async (column, position, text) => {
    if (!text || checking) return
    const trimmed = text.trim()
    const key = `${column}:${position}`
    // Блокируем повторную отправку по самому факту разгаданности ячейки,
    // даже если сервер вернул пустой текст (например, старый слепок).
    if (Object.prototype.hasOwnProperty.call(solvedPyramid, key)) return
    setChecking(true)
    setWrong(false)
    try {
      const res = await submitPyramidTeamAnswer(sessionId, column, position, trimmed)
      if (res) applyState(res, true)
    } catch (err) {
      setWrong(true)
      setError(apiMessage(err))
      setTimeout(() => setWrong(false), 2000)
    } finally {
      setChecking(false)
    }
  }

  /**
   * Открыть подсказку вопроса.
   *
   * hintId не указан — сервер отдаст ближайшую по времени доступную.
   * Указан — откроется именно эта подсказка: доступность зависит только
   * от времени, предыдущие открывать не обязательно.
   */
  const handleHint = async (hintId = null) => {
    if (!currentQuestion || hintLoading || currentSolved) return
    setHintLoading(true)
    setError('')
    try {
      const res = hintId == null
        ? await revealTeamHint(sessionId, currentQuestion.id)
        : await revealTeamHintById(sessionId, currentQuestion.id, hintId)
      if (res) applyState(res, true)
    } catch (err) {
      // Локальное состояние не трогаем: показываем только сообщение об ошибке.
      setError(apiMessage(err))
    } finally {
      setHintLoading(false)
    }
  }

  /** Завершить партию — только хост. Локальный статус меняем лишь после ответа сервера. */
  const handleEnd = async () => {
    if (ending) return
    setShowEndConfirm(false)
    setEnding(true)
    setError('')
    try {
      const res = await endTeamGame(sessionId)
      if (res) {
        applyState(res, true)
        if (onEnded) onEnded(res)
      }
    } catch (err) {
      // Сервер не подтвердил завершение — остаёмся на активном экране.
      setError(apiMessage(err))
    } finally {
      setEnding(false)
    }
  }

  /** Покинуть партию — партия продолжается без нас. */
  const handleLeave = async () => {
    try {
      await leaveTeamGame(sessionId)
    } catch {
      // не критично
    }
    if (onExit) onExit()
  }

  if (loading) return <div className="page-loader">Загрузка командной игры…</div>
  if (error && !state) return <div className="alert alert-error">{error}</div>
  if (!state) return null
  if (!game) {
    return (
      <div className="assoc-player">
        {onExit && (
          <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
            ← К списку игр
          </button>
        )}
        <div className="alert alert-error">Не удалось загрузить данные игры. Попробуйте обновить или переподключиться.</div>
        <button type="button" className="btn btn-primary" onClick={() => {
          getTeamGameState(sessionId).then((s) => applyState(s, true)).catch((e) => setError(apiMessage(e)))
        }}>Переподключиться</button>
      </div>
    )
  }

  if (isFinished) {
    const completed = String(endReason).toLowerCase() === 'completed'
    return (
      <div className="assoc-start-screen">
        <div className="assoc-team-finish">
          <div className="assoc-info-card">
            <div className="assoc-win-emoji" aria-hidden="true">{completed ? '🏆' : '🏁'}</div>
            <h2 className="assoc-info-title">{finishTitle}</h2>
            <p className="assoc-info-description">
              Штрафное время команды: +{formatTime(Math.round(penalty * 60))}
            </p>
            <p className="assoc-info-description">
              Пройдено уровней: {Math.min(levelIndex + (completed ? 1 : 0), levels.length)} из {levels.length}
            </p>
            <div className="assoc-info-actions">
              {onExit && (
                <button type="button" className="btn btn-secondary" onClick={onExit}>
                  К списку игр
                </button>
              )}
            </div>
          </div>

          <div className="assoc-statistics-card assoc-team-statistics">
            <h3 className="assoc-statistics-title">Статистика времени по уровням</h3>
            {statisticsLoading && !statistics && (
              <p className="assoc-info-description">Загружаем статистику…</p>
            )}
            {statisticsError && (
              <>
                <p className="assoc-win-error">{statisticsError}</p>
                <button type="button" className="btn btn-secondary" onClick={loadStatistics}>
                  Повторить
                </button>
              </>
            )}
            {!statisticsError && statistics && (
              <GameStatisticsTable
                levels={statistics.levels}
                totalSeconds={statistics.totalSeconds}
                emptyText="Ни один уровень не был начат"
              />
            )}
          </div>
        </div>
      </div>
    )
  }

  // Текст статуса подсказки для выбранного вопроса.
  let hintStatusText = 'Для этого вопроса нет подсказок'
  if (currentSolved) {
    hintStatusText = 'Вопрос решён — новые подсказки недоступны'
  } else if (allHintsRevealed) {
    hintStatusText = 'Все подсказки по этому вопросу получены'
  } else if (upcomingHint) {
    hintStatusText = `Следующая подсказка по этому вопросу через ${formatTime(nextHintRemainingSec)}`
  } else if (availableHints.length > 0) {
    hintStatusText = `Доступно подсказок: ${availableHints.length}`
  }

  return (
    <div className="assoc-player">
      {onExit && (
        <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
          ← К списку игр
        </button>
      )}

      <div className="assoc-player-top">
        <div className="assoc-player-heading">
          <h2 className="assoc-player-title">{game.title} · Командный режим</h2>
          <span className="assoc-player-author">
            Хост: {hostUsername || '—'}{isHost ? ' (вы)' : ''}
          </span>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="badge" title="Время игры">⏱ Игра: {formatTime(gameElapsedSec)}</span>
          {gameRemainingSec != null && (
            <span
              className="badge"
              style={{ color: gameRemainingSec <= 30 ? '#ff4d4f' : undefined, fontWeight: 700 }}
              title="Осталось до автоматического завершения игры"
            >
              ⏳ До конца: {formatTime(gameRemainingSec)}
            </span>
          )}
          {levelEndTimeMs ? (
            <span className="badge" style={{ color: levelRemainingSec <= 10 ? '#ff4d4f' : '#fbfbfa', fontWeight: 700 }} title="Обратный отсчёт до окончания уровня">⏲ Уровень: {formatTime(levelRemainingSec)}</span>
          ) : levelTimeLimitSec ? (
            <span className="badge" title="Таймер уровня">⏲ Уровень: {formatTime(levelRemainingSec || levelTimeLimitSec)}</span>
          ) : null}
          <span className="badge" title="Подсказка по выбранному вопросу">
            💡 {currentSolved
              ? 'вопрос решён'
              : allHintsRevealed
                ? 'все получены'
                : nextHintRemainingSec > 0
                  ? `через ${formatTime(nextHintRemainingSec)}`
                  : availableHints.length > 0
                    ? `доступно ${availableHints.length}`
                    : 'нет подсказок'}
          </span>
        </div>
        {/* Завершить игру может только хост, остальные — покинуть. */}
        {isHost ? (
          <button
            type="button"
            className="btn btn-danger"
            onClick={() => setShowEndConfirm(true)}
            disabled={ending}
          >
            {ending ? 'Завершаем…' : 'Завершить игру'}
          </button>
        ) : (
          <button type="button" className="btn btn-secondary" onClick={handleLeave}>
            Покинуть игру
          </button>
        )}
      </div>

      {showEndConfirm && (
        <div className="modal-overlay" onClick={() => !ending && setShowEndConfirm(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '520px', width: '90%' }}>
            <h3 className="modal-title">Завершить игру для всех участников?</h3>
            <p className="modal-text">
              Партия будет остановлена для всей команды. Продолжить её после завершения нельзя.
            </p>
            <div className="modal-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setShowEndConfirm(false)}
                disabled={ending}
              >
                Отмена
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={handleEnd}
                disabled={ending}
              >
                {ending ? 'Завершаем…' : 'Да, завершить'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Индикаторы присутствия участников команды */}
      <div className="assoc-team-presence">
        {Object.entries(memberNames).map(([uid, name]) => {
          const online = onlineUserIds.includes(String(uid))
          const isSessionHost = String(uid) === String(state.hostId)
          return (
            <span key={uid} className={'assoc-presence-chip' + (online ? ' online' : '')}>
              {name}{online ? ' ●' : ''}{isSessionHost ? ' 👑' : ''}
            </span>
          )
        })}
      </div>

      <div className="assoc-level-progress">
        Уровень {levelIndex + 1} из {levels.length} · Штраф: +{formatTime(Math.round(penalty * 60))}
      </div>

      {flash && <div className="alert alert-success assoc-team-flash">{flash}</div>}
      {error && <div className="alert alert-error">{error}</div>}

      {/* Для ASSOCIATIONS уровней рендерим пирамиду как в одиночной игре */}
      {level?.type === 'ASSOCIATIONS' ? (
        <GameBoard
          key={`${sessionId}-${levelIndex}`}
          game={game}
          level={level}
          progress={{
            solved: solvedPyramid,
            elapsedSeconds: gameElapsedSec,
          }}
          onLevelComplete={(payload) => {
            showFlash('🎉 Уровень пройден! Открыт следующий уровень')
          }}
          onSave={(progressData) => {
            // Сохраняем solvedPyramid локально
            if (progressData?.solved) {
              setSolvedPyramid(progressData.solved)
            }
          }}
          onCheckAnswer={(column, position, text) => handleSubmitPyramid(column, position, text)}
        />
      ) : (
        <div className="assoc-normal-card">
        <h3 className="assoc-normal-question">{level?.title}</h3>
        {renderMedia(level?.mediaUrls, setFullscreenImage)}

        {/* Выпадающий список вопросов: у каждого игрока свой выбор. */}
        {questions.length > 1 && (
          <div className="assoc-normal-nav">
            <label className="field assoc-normal-select">
              <span>Вопрос</span>
              <select
                value={currentQuestion ? String(currentQuestion.id) : ''}
                onChange={(e) => {
                  setSelectedQuestionId(e.target.value)
                  setAnswer('')
                  setError('')
                }}
              >
                {questions.map((q, idx) => {
                  const qState = questionStates[String(q.id)]
                  return (
                    <option key={q.id} value={String(q.id)}>
                      {`${idx + 1}. ${shortQuestionText(q.questionText)} — ${questionStatusText(qState)}`}
                    </option>
                  )
                })}
              </select>
            </label>
          </div>
        )}

        {currentQuestion ? (
          <>
            <div className="assoc-team-question-head">
              <p className="assoc-normal-question">{currentQuestion.questionText}</p>
              <span className={'assoc-team-question-status' + (currentSolved ? ' solved' : '')}>
                {currentSolved ? '✅ Решён' : 'Не решён'}
              </span>
            </div>
            {renderMedia(currentQuestion.mediaUrls, setFullscreenImage)}
            <p className="assoc-normal-progress">
              Правильно: <strong>{questionSolvedCount}</strong> из <strong>{passThreshold}</strong>
            </p>
          </>
        ) : (
          <p className="assoc-normal-hints-empty">В этом уровне нет вопросов</p>
        )}

        {/* Подсказка, полученная командой (краткое уведомление) */}
        {hintNotice && (
          <div className="assoc-team-hint-notice">
            {hintNotice.imageUrl && (
              <img src={hintNotice.imageUrl} alt="Подсказка" onClick={() => setFullscreenImage(hintNotice.imageUrl)} />
            )}
            <span>{hintNotice.text}</span>
            {hintNotice.penaltySeconds ? (
              <span className="assoc-team-hint-penalty">штраф: +{hintNotice.penaltySeconds} сек.</span>
            ) : null}
          </div>
        )}

        {/*
          Подсказки выбранного вопроса — по одной на каждую.
          Доступность каждой определяется только временем, поэтому кнопка
          появляется у любой подсказки, время которой наступило.
        */}
        {currentQuestion && (
          <div className="assoc-team-hints">
            <div className="assoc-team-hints-title">Подсказки по вопросу</div>
            {hintRows.length === 0 ? (
              <p className="assoc-normal-hints-empty">Для этого вопроса нет подсказок</p>
            ) : (
              <ul className="assoc-team-hints-list">
                {hintRows.map((hint) => (
                  <li
                    key={hint.id ?? hint.index}
                    className={'assoc-team-hint-item assoc-team-hint-' + hint.status}
                  >
                    <div className="assoc-team-hint-head">
                      <span>Подсказка {hint.index + 1}</span>
                      {hint.status === 'revealed' ? (
                        <span className="assoc-team-hint-badge opened">Открыта</span>
                      ) : hint.status === 'available' ? (
                        <span className="assoc-team-hint-badge available">Доступна</span>
                      ) : (
                        <span className="assoc-team-hint-badge locked">Недоступна</span>
                      )}
                      {hint.status === 'revealed' && hint.penaltyTime ? (
                        <span className="assoc-team-hint-penalty">
                          штраф: +{Math.round(Number(hint.penaltyTime) * 60)} сек.
                        </span>
                      ) : null}
                    </div>

                    {hint.status === 'revealed' && (
                      <>
                        {hint.text && <span className="assoc-team-hint-text">{hint.text}</span>}
                        {hint.imageUrl && (
                          <img
                            src={hint.imageUrl}
                            alt="Подсказка"
                            className="assoc-team-hint-image"
                            onClick={() => setFullscreenImage(hint.imageUrl)}
                          />
                        )}
                        {renderMedia(hint.mediaUrls, setFullscreenImage)}
                      </>
                    )}

                    {hint.status === 'available' && (
                      <button
                        type="button"
                        className="btn btn-primary assoc-team-hint-button"
                        onClick={() => handleHint(hint.id)}
                        disabled={hintLoading || currentSolved}
                      >
                        {hintLoading ? 'Получаем подсказку…' : '💡 Получить подсказку'}
                      </button>
                    )}

                    {hint.status === 'unavailable' && (
                      <span className="assoc-team-hint-countdown">
                        Появится через {formatTime(hint.remainingSec)}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <div className="assoc-team-hint-action">
              <span className="assoc-team-hint-status">{hintStatusText}</span>
            </div>
          </div>
        )}

        {solvedWords.length > 0 && (
          <div className="assoc-normal-guessed">
            <div className="assoc-normal-guessed-title">Угаданные слова</div>
            <ul className="assoc-normal-guessed-list">
              {solvedWords.map((w, idx) => (
                <li key={idx} className="assoc-normal-guessed-item">{w}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="assoc-normal-answer" style={{ border: wrong ? '1px solid #ff4d4f' : undefined }}>
          <input
            className="assoc-input"
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSubmit() }}
            placeholder={currentSolved ? 'Вопрос уже решён' : 'Командный ответ'}
            disabled={checking || !currentQuestion || currentSolved}
          />
          <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={checking || !currentQuestion || currentSolved}>
            Отправить
          </button>
        </div>
      </div>
      )}

      {/* Лента событий команды */}
      <div className="assoc-info-block" style={{ marginTop: 16 }}>
        <h3 className="assoc-info-block-title">Лента событий</h3>
        <ul className="assoc-event-list">
          {(events || []).slice().reverse().map((ev, idx) => (
            <li key={idx} className="assoc-event-item">
              <span>{ev.text}</span>
            </li>
          ))}
        </ul>
      </div>

      {fullscreenImage && (
        <div
          onClick={() => setFullscreenImage(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.9)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            cursor: 'zoom-out',
            padding: '20px',
          }}
        >
          <img
            src={fullscreenImage}
            alt="Медиа"
            style={{ maxWidth: '90%', maxHeight: '90%' }}
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </div>
  )
}

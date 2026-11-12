import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiMessage } from '../../api/client'
import { fetchGame, fetchLeaderboard, startGame, submitResult, restartGame, saveGameProgress, loadGameProgress, clearGameProgress } from '../../api/games'
import { fetchTeams } from '../../api/teams'
import { getTeamGameState, joinTeamGame, leaveTeamGame, startTeamGameSession } from '../../api/teamGames'
import GameBoard from './GameBoard'
import NormalLevelBoard from './NormalLevelBoard'
import Leaderboard from './Leaderboard'
import TeamGamePlay from './TeamGamePlay'
import ActiveTeamGames from './ActiveTeamGames'
import useTeamChannel from './useTeamChannel'
import GameStatisticsTable from './GameStatisticsTable'
import GameStatisticsBlock from './GameStatisticsBlock'
import { formatMinutes } from './constants'

/** Определить тип медиафайла по URL (расширение сохраняется бэкендом). */
function mediaKind(url) {
  const lower = (url || '').toLowerCase()
  if (/\.(mp4|webm|m4v|mov)(\?|$)/.test(lower)) return 'video'
  if (/\.(mp3|wav|ogg|m4a)(\?|$)/.test(lower)) return 'audio'
  if (/\.(png|jpg|jpeg|gif|webp)(\?|$)/.test(lower)) return 'image'
  return 'other'
}

/** Дата игры из ISO-строки (LocalDate) в человекочитаемый формат. */
function formatGameDate(dateStr) {
  if (!dateStr) return ''
  const parts = String(dateStr).split('-')
  if (parts.length === 3 && parts[0].length === 4) return `${parts[2]}.${parts[1]}.${parts[0]}`
  return String(dateStr)
}

/** Сложность в виде звёздочек (1..5). */
function difficultyStars(value) {
  const n = Math.max(0, Math.min(5, Number(value) || 0))
  return n > 0 ? '★'.repeat(n) : ''
}

/**
 * Режим игрока: последовательное прохождение уровней игры.
 *
 * - «Начать игру» запускает первый уровень.
 * - После прохождения уровня (или истечения времени уровня) загружается следующий.
 * - После последнего уровня — экран итоговой статистики.
 */
function playerKey(id, suffix) {
  if (!id) return null
  return `assoc_player_${id}_${suffix}`
}
function readP(k) { if (!k) return null; try { return localStorage.getItem(k) } catch { return null } }
function writeP(k,v) { if (!k) return; try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k,String(v)) } catch {} }
function readPJson(k) { if (!k) return null; try { const s=localStorage.getItem(k); return s?JSON.parse(s):null } catch { return null } }
function writePJson(k,v) { if (!k) return; try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k,JSON.stringify(v)) } catch {} }

export default function PlayerPanel({ games, gameId, onSelectGame, onExit, currentUserId }) {
  const navigate = useNavigate()
  // Ключ хранилища должен быть стабильным между рендерами,
  // иначе useCallback/effect с ним в зависимостях перезапускаются постоянно.
  const pk = useCallback((s) => playerKey(gameId || currentUserId, s), [gameId, currentUserId])
  /**
   * Ключи командной сессии хранятся с привязкой к пользователю, а не к игре,
   * выбранной в списке: при перезагрузке страницы выбранная игра ещё не
   * определена, и состояние участия иначе потерялось бы.
   */
  const tpk = useCallback((s) => playerKey(currentUserId, s), [currentUserId])
  const [game, setGame] = useState(null)
  const [progress, setProgress] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [leaderboard, setLeaderboard] = useState([])
  const [leaderboardLoading, setLeaderboardLoading] = useState(false)
  const [resultError, setResultError] = useState('')

  const [gameStarted, setGameStarted] = useState(() => readP(pk('started')) === '1')
  const [starting, setStarting] = useState(false)
  const [currentLevelIndex, setCurrentLevelIndex] = useState(() => { const v=Number(readP(pk('level'))); return Number.isFinite(v)&&v>=0?v:0 })
  const [savedQuestionIndex, setSavedQuestionIndex] = useState(() => { const v=Number(readP(pk('question'))); return Number.isFinite(v)&&v>=0?v:0 })
  const [levelStats, setLevelStats] = useState(() => readPJson(pk('stats')) || [])
  const [finished, setFinished] = useState(() => readP(pk('finished')) === '1')
  const [totalTime, setTotalTime] = useState(() => { const v=Number(readP(pk('total'))); return Number.isFinite(v)?v:0 })
  const [teamMode, setTeamMode] = useState(() => readP(tpk('teamMode')) === '1')
  const [teams, setTeams] = useState([])
  const [selectedTeamId, setSelectedTeamId] = useState(() => { const v=Number(readP(tpk('teamId'))); return Number.isFinite(v)?v:null })
  const [teamSessionId, setTeamSessionId] = useState(() => readP(tpk('teamSession')) || null)
  const [teamState, setTeamState] = useState(null)
  const [teamError, setTeamError] = useState('')
  const [showQuitModal, setShowQuitModal] = useState(false)
  const [quitting, setQuitting] = useState(false)
  // Зелёное уведомление о пройденном уровне: показываем 2 секунды,
  // затем переходим к следующему уровню или к итогам.
  const [levelCompleteNotice, setLevelCompleteNotice] = useState(false)
  const levelNoticeTimer = useRef(null)
  // Проверен ли id командной сессии (защита от обращения к несуществующей сессии)
  const [teamSessionChecked, setTeamSessionChecked] = useState(false)
  // id уже проверенной сессии — чтобы не проверять одну и ту же сессию снова
  const checkedSessionRef = useRef(null)

  /** Скрыть уведомление о пройденном уровне и отменить отложенный переход. */
  const clearLevelCompleteNotice = useCallback(() => {
    if (levelNoticeTimer.current) {
      clearTimeout(levelNoticeTimer.current)
      levelNoticeTimer.current = null
    }
    setLevelCompleteNotice(false)
  }, [])

  // При размонтировании не оставляем висящий таймер перехода.
  useEffect(() => () => {
    if (levelNoticeTimer.current) clearTimeout(levelNoticeTimer.current)
  }, [])

  /**
   * Пользователь известен не сразу: при первом рендере currentUserId может быть
   * пустым (данные пользователя ещё загружаются), и ключи участия тогда
   * недоступны. Когда пользователь появился — читаем участие заново.
   */
  const restoredUserRef = useRef(null)
  useEffect(() => {
    if (!currentUserId) return
    if (restoredUserRef.current === currentUserId) return
    restoredUserRef.current = currentUserId
    setTeamMode(readP(tpk('teamMode')) === '1')
    setTeamSessionId(readP(tpk('teamSession')) || null)
    const teamIdValue = Number(readP(tpk('teamId')))
    setSelectedTeamId(Number.isFinite(teamIdValue) && teamIdValue > 0 ? teamIdValue : null)
  }, [currentUserId, tpk])

  /** Выйти из командного режима: сессия недоступна или партия завершена. */
  const resetTeamMode = useCallback(() => {
    setTeamMode(false)
    setTeamState(null)
    setTeamSessionId(null)
    setTeamError('')
    checkedSessionRef.current = null
    writeP(tpk('teamMode'), null)
    writeP(tpk('teamSession'), null)
  }, [tpk])

  /**
   * События команды: любой участник запустил партию (STARTED) —
   * автоматически переходим в командный режим и открываем ту же игру.
   * Партию завершили (end_team_game) — возвращаемся в список игр.
   */
  const handleTeamGameStarted = useCallback((state, teamId, type) => {
    if (type === 'end_team_game') {
      // Партия завершена на сервере. Оставляем игрока на экране результатов,
      // а не выбрасываем в список игр: иначе он не увидит причину завершения.
      const endedSessionId = state?.sessionId ?? teamSessionId
      if (endedSessionId && String(endedSessionId) === String(teamSessionId)) {
        if (state) setTeamState(state)
        setTeamMode(true)
        setTeamSessionChecked(true)
      }
      return
    }
    const sessionId = state?.sessionId
    if (!sessionId) return
    setTeamSessionId(sessionId)
    setTeamState(state)
    setTeamMode(true)
    setTeamSessionChecked(true)
    setTeamError('')
    if (teamId) writeP(tpk('teamId'), teamId)
    writeP(tpk('teamMode'), '1')
    writeP(tpk('teamSession'), sessionId)
  }, [tpk, resetTeamMode, teamSessionId])

  /** Партию завершили — показываем экран результатов с причиной завершения. */
  const handleTeamGameEnded = useCallback((finalState) => {
    if (finalState) setTeamState(finalState)
  }, [])

  /** Выход с экрана командной игры (в том числе после завершения). */
  const exitTeamMode = useCallback(() => {
    resetTeamMode()
    if (onExit) onExit()
  }, [resetTeamMode, onExit])

  /**
   * Присоединение к активной партии команды из карточки в списке игр.
   * Игрок попадает ровно на текущий момент партии.
   */
  const handleJoinActiveTeamGame = useCallback(async (session) => {
    setTeamError('')
    try {
      const state = await joinTeamGame(session.sessionId)
      setTeamSessionId(state.sessionId)
      setTeamState(state)
      setTeamMode(true)
      setTeamSessionChecked(true)
      writeP(tpk('teamMode'), '1')
      writeP(tpk('teamSession'), state.sessionId)
      writeP(tpk('teamId'), session.teamId)
      writeP(pk('started'), null)
    } catch (err) {
      setTeamError(apiMessage(err))
    }
  }, [tpk])
  // есть ли сохранённая незавершённая сессия (для кнопки «Продолжить игру»)
  const [resumable, setResumable] = useState(() => {
    const levelIndex = Number(readP(pk('level'))) || 0
    const levelStats = readPJson(pk('stats')) || []
    return {
      levelIndex,
      questionIndex: Number(readP(pk('question'))) || 0,
      levelStats,
      hasProgress: readP(pk('started')) === '1' || levelIndex > 0 || levelStats.length > 0,
    }
  })
  useEffect(() => {
    fetchTeams()
      .then((data) => {
        const list = Array.isArray(data) ? data : []
        // Играть командой можно только в командах, где пользователь состоит.
        setTeams(list.filter((t) => t && t.currentUserMember))
      })
      .catch(() => setTeams([]))
  }, [])

  /**
   * Сохранённый в браузере id сессии мог устареть: партию завершили,
   * команду удалили, сессию снесли. Тогда просто выходим из командного
   * режима, вместо того чтобы показывать ошибку «Сессия не найдена».
   *
   * Пока сессия не проверена, лобби не отрисовываем — иначе он успел бы
   * запросить несуществующую сессию и показать ошибку.
   */
  useEffect(() => {
    if (!teamMode || !teamSessionId) {
      checkedSessionRef.current = null
      setTeamSessionChecked(true)
      return undefined
    }
    if (checkedSessionRef.current === teamSessionId) return undefined
    let cancelled = false
    checkedSessionRef.current = teamSessionId
    setTeamSessionChecked(false)
    getTeamGameState(teamSessionId)
      .then((state) => {
        if (cancelled || !state) return
        const sessionStatus = String(state.status || '').toLowerCase()
        if (sessionStatus === 'active' || sessionStatus === 'finished') {
          // Активную партию показываем, а завершённую — экраном результатов.
          // После перезагрузки страницы игрок снова увидит актуальный статус.
          setTeamState(state)
        } else {
          resetTeamMode()
          return
        }
        if (!cancelled) setTeamSessionChecked(true)
      })
      .catch(() => {
        if (cancelled) return
        // Сессии больше нет — возвращаем игрока на экран игры.
        resetTeamMode()
      })
    return () => {
      cancelled = true
      // Освобождаем отметку: эффект перезапустится (например, когда
      // загрузится выбранная игра), и проверка сессии должна повториться.
      if (checkedSessionRef.current === teamSessionId) {
        checkedSessionRef.current = null
      }
    }
  }, [teamMode, teamSessionId, gameId, resetTeamMode])

  // Автоматически подключаемся к партии, которую запустил любой участник команды.
  // Канал один на команду, поэтому подписываемся на каждую свою команду.
  useTeamChannel(teams.map((t) => t.id), handleTeamGameStarted)

  // Загружаем информацию об игре (без запуска таймера): игрок видит экран описания.
  useEffect(() => {
    if (!gameId) {
      setGame(null)
      setProgress(null)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError('')
    fetchGame(gameId)
      .then((data) => {
        if (cancelled) return
        setGame(data.game)
        setProgress(data.progress)
        // если есть сохранённое состояние - не сбрасываем
        const savedStarted = readP(pk('started')) === '1'
        if (!savedStarted) {
          setGameStarted(false)
          setCurrentLevelIndex(0)
          setLevelStats([])
          setFinished(false)
          setTotalTime(0)
        }
      })
      .catch((err) => { if (!cancelled) setError(apiMessage(err)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    // подтягиваем сохранённый прогресс из user_game_progress (бэкенд)
    loadGameProgress(gameId)
      .then((saved) => {
        if (cancelled || !saved) return
        const state = saved.gameState || {}
        // индекс текущего уровня
        const savedLevel = Number(saved.currentLevelIndex)
        if (Number.isFinite(savedLevel) && savedLevel >= 0) {
          writeP(pk('level'), savedLevel)
          setCurrentLevelIndex((prev) => (prev === 0 ? savedLevel : prev))
        }
        // индекс текущего вопроса обычного уровня
        const savedQuestion = Number(saved.currentQuestionIndex)
        if (Number.isFinite(savedQuestion) && savedQuestion > 0) {
          writeP(pk('question'), savedQuestion)
          setSavedQuestionIndex(savedQuestion)
        }
        // статистика предыдущих уровней
        if (Array.isArray(state.levelStats)) {
          writePJson(pk('stats'), state.levelStats)
          setLevelStats((prev) => (prev && prev.length ? prev : state.levelStats))
        }
        // командный режим
        if (state.teamMode != null) {
          writeP(tpk('teamMode'), String(state.teamMode ? 1 : 0))
          setTeamMode((prev) => (prev ? prev : Boolean(state.teamMode)))
        }
        // игра была начата — возвращаем игрока сразу на нужный уровень
        const wasStarted = state.started === true
          || savedLevel > 0
          || (Array.isArray(state.levelStats) && state.levelStats.length > 0)
        if (wasStarted) {
          writeP(pk('started'), '1')
          setGameStarted((prev) => (prev ? prev : true))
        }
        // сохраняем сведения о незавершённой сессии для кнопки «Продолжить игру».
        // Наличие сохранённой сессии в базе уже означает, что игру можно продолжить:
        // разгаданные слова и таймер хранятся на сервере.
        const hasProgress = true
        setResumable({
          levelIndex: Number.isFinite(savedLevel) && savedLevel > 0 ? savedLevel : 0,
          questionIndex: Number.isFinite(savedQuestion) && savedQuestion > 0 ? savedQuestion : 0,
          levelStats: Array.isArray(state.levelStats) ? state.levelStats : [],
          hasProgress,
        })
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [gameId, currentUserId])

  /** Начать игру с нуля: сбрасываем серверный прогресс и начинаем первый уровень. */
  const startPlaying = async () => {
    if (!game || starting) return
    clearLevelCompleteNotice()
    setStarting(true)
    setError('')
    try {
      // Полный сброс: серверный прогресс и разгаданные слова тоже очищаются
      try { await restartGame(game.id) } catch {}
      try { await clearGameProgress(game.id) } catch {}
      const data = await startGame(game.id)
      setGame(data.game)
      setProgress(data.progress)
      setCurrentLevelIndex(0)
      setSavedQuestionIndex(0)
      setLevelStats([])
      setFinished(false)
      setTotalTime(0)
      setGameStarted(true)
      writeP(pk('started'),'1'); writeP(pk('level'),'0'); writeP(pk('question'),null); writePJson(pk('stats'),[]); writeP(pk('finished'),null); writeP(pk('total'),null)
      writeP(tpk('teamMode'),null); writeP(tpk('teamSession'),null); writeP(tpk('teamId'),null)
      setResumable({ levelIndex: 0, questionIndex: 0, levelStats: [], hasProgress: false })
      gameStateRef.current = {
        ...gameStateRef.current,
        started: true,
        levelIndex: 0,
        questionIndex: 0,
        levelStats: [],
        teamMode: false,
      }
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setStarting(false)
    }
  }

  /**
   * Продолжить сохранённую игру: подтягиваем состояние с сервера
   * (таймер продолжает отсчёт с учётом офлайн-времени) и открываем нужный уровень.
   */
  const resumePlaying = async () => {
    if (!game || starting) return
    clearLevelCompleteNotice()
    setStarting(true)
    setError('')
    try {
      const data = await startGame(game.id)
      setGame(data.game)
      setProgress(data.progress)
      const levelIdx = Math.max(0, Number(resumable.levelIndex) || 0)
      setCurrentLevelIndex(levelIdx)
      setSavedQuestionIndex(Math.max(0, Number(resumable.questionIndex) || 0))
      setLevelStats(Array.isArray(resumable.levelStats) ? resumable.levelStats : [])
      setFinished(false)
      setTotalTime(0)
      setGameStarted(true)
      writeP(pk('started'), '1')
      writeP(pk('level'), levelIdx)
      writeP(pk('question'), Number(resumable.questionIndex) || 0)
      writePJson(pk('stats'), Array.isArray(resumable.levelStats) ? resumable.levelStats : [])
      writeP(pk('finished'), null); writeP(pk('total'), null)
      writeP(tpk('teamMode'), null); writeP(tpk('teamSession'), null); writeP(tpk('teamId'), null)
      gameStateRef.current = {
        ...gameStateRef.current,
        started: true,
        levelIndex: levelIdx,
        questionIndex: Number(resumable.questionIndex) || 0,
        levelStats: Array.isArray(resumable.levelStats) ? resumable.levelStats : [],
        teamMode: false,
      }
    } catch (err) {
      setError(apiMessage(err))
    } finally {
      setStarting(false)
    }
  }

  /**
   * Начать командную игру: сервер создаёт сессию, текущий игрок становится хостом.
   * Если партия этой команды уже идёт — подключаемся к ней.
   */
  const startTeamPlay = async () => {
    if (!game) return
    if (!teams || teams.length === 0) {
      setTeamError('Вы не состоите ни в одной команде')
      return
    }
    if (!selectedTeamId) {
      setSelectedTeamId(teams[0].id)
    }
    setTeamError('')
    try {
      const tid = selectedTeamId || teams[0].id
      const state = await startTeamGameSession(tid, game.id)
      setTeamSessionId(state.sessionId)
      setTeamState(state)
      setTeamMode(true)
      setTeamSessionChecked(true)
      writeP(tpk('teamMode'),'1'); writeP(tpk('teamSession'), state.sessionId); writeP(tpk('teamId'), tid)
      writeP(pk('started'),null)
    } catch (err) {
      setTeamError(apiMessage(err))
    }
  }

  const loadLeaderboard = useCallback(async (id) => {
    if (!id) return
    setLeaderboardLoading(true)
    try {
      setLeaderboard(await fetchLeaderboard(id))
    } catch (err) {
      setResultError(apiMessage(err))
    } finally {
      setLeaderboardLoading(false)
    }
  }, [])

  useEffect(() => {
    setLeaderboard([])
    setResultError('')
    loadLeaderboard(game?.id)
  }, [game, loadLeaderboard])

  // дебаунс-автосохранение состояния игры в бэкенд
  const saveTimerRef = useRef(null)
  const gameStateRef = useRef({ started: false, levelIndex: 0, questionIndex: 0, levelStats: [], teamMode: false })

  useEffect(() => {
    gameStateRef.current = {
      ...gameStateRef.current,
      started: gameStarted && !finished,
      levelIndex: currentLevelIndex,
      levelStats,
      teamMode,
    }
  }, [gameStarted, finished, currentLevelIndex, levelStats, teamMode])

  const scheduleSave = useCallback((extra) => {
    if (extra) {
      const patch = {}
      if (extra.currentQuestionIndex != null) patch.questionIndex = Number(extra.currentQuestionIndex)
      if (Object.keys(patch).length) {
        gameStateRef.current = { ...gameStateRef.current, ...patch }
        if (patch.questionIndex != null) {
          writeP(pk('question'), patch.questionIndex)
          setSavedQuestionIndex(patch.questionIndex)
        }
      }
    }
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null
      const gs = gameStateRef.current
      if (!gameId) return
      const payload = {
        currentLevelIndex: gs.levelIndex,
        currentQuestionIndex: gs.questionIndex,
        gameState: { ...gs, ...(extra || {}) },
      }
      saveGameProgress(gameId, payload).catch(() => {})
    }, 1200)
    return () => { if (saveTimerRef.current) clearTimeout(saveTimerRef.current) }
  }, [gameId])

  useEffect(() => {
    if (!gameId) return
    if (!gameStarted || finished) return
    return scheduleSave()
  }, [gameId, gameStarted, finished, scheduleSave])

  /** Завершение уровня: фиксируем статистику и переходим дальше или к итогам. */
  const handleLevelComplete = (level, { netTime, penaltyTime = 0, timedOut = false }) => {
    const net = Number(netTime) || 0
    const penalty = Number(penaltyTime) || 0
    const stat = { levelId: level.id, name: level.title, netTime: net, penaltyTime: penalty, totalTime: net + penalty, timedOut: !!timedOut }
    const nextStats = [...levelStats, stat]
    setLevelStats(nextStats)
    writePJson(pk('stats'), nextStats)
    const levels = game?.levels || []
    const isLast = currentLevelIndex >= levels.length - 1

    /** Переход к следующему уровню или к экрану итогов. */
    const goNext = () => {
      if (isLast) {
        const grandTotal = nextStats.reduce((sum, s) => sum + s.totalTime, 0)
        setTotalTime(grandTotal)
        setFinished(true)
        writeP(pk('total'), grandTotal); writeP(pk('finished'),'1')
        // Уровень, закончившийся по таймауту, не считается пройденным:
        // такой результат сервер в рейтинг не примет, поэтому и не отправляем.
        if (nextStats.some((s) => s.timedOut)) {
          setResultError('Игра не засчитана: уровень закончился по времени. Результат не попал в рейтинг.')
          return
        }
        setResultError('')
        // Детализация по уровням уходит на сервер: он проверяет, что все
        // уровни пройдены, и сам считает общее время.
        const levelsStats = nextStats.map((s) => ({
          levelId: s.levelId,
          levelName: s.name,
          netSeconds: Math.max(0, Math.round(s.netTime)),
          penaltySeconds: Math.max(0, Math.round(s.penaltyTime)),
          totalSeconds: Math.max(0, Math.round(s.totalTime)),
          timedOut: !!s.timedOut,
        }))
        submitResult(game.id, Math.max(1, Math.round(grandTotal)), levelsStats)
          .then(() => {
            // Небольшая пауза перед обновлением рейтинга: результат должен
            // сначала закоммититься на сервере, иначе таблица останется пустой.
            return new Promise((resolve) => setTimeout(resolve, 300))
          })
          .then(() => loadLeaderboard(game.id))
          .catch((err) => setResultError(`Не удалось сохранить результат: ${apiMessage(err)}`))
        try { clearGameProgress(game.id).catch(() => {}) } catch {}
      } else {
        const nextIdx = currentLevelIndex + 1
        setCurrentLevelIndex(nextIdx)
        writeP(pk('level'), nextIdx)
        writeP(pk('question'), null)
        setSavedQuestionIndex(0)
        gameStateRef.current = { ...gameStateRef.current, levelIndex: nextIdx, questionIndex: 0 }
      }
    }

    // При истечении времени уровень не «пройден» — переключаемся без уведомления.
    if (timedOut) {
      clearLevelCompleteNotice()
      goNext()
      return
    }

    // Даём игроку 2 секунды увидеть зелёное «Уровень пройден!» перед переходом.
    setLevelCompleteNotice(true)
    if (levelNoticeTimer.current) clearTimeout(levelNoticeTimer.current)
    levelNoticeTimer.current = setTimeout(() => {
      levelNoticeTimer.current = null
      setLevelCompleteNotice(false)
      goNext()
    }, 2000)
  }

  /** Начать заново: сбрасываем прогресс и статистику. */
  const playAgain = async () => {
    clearLevelCompleteNotice()
    try {
      await restartGame(game.id)
      try { await clearGameProgress(game.id) } catch {}
    } catch {
      // не критично
    }
    setGameStarted(false)
    setCurrentLevelIndex(0)
    setSavedQuestionIndex(0)
    setLevelStats([])
    setFinished(false)
    setTotalTime(0)
    setResultError('')
    setResumable({ levelIndex: 0, questionIndex: 0, levelStats: [], hasProgress: false })
    writeP(pk('started'),null); writeP(pk('level'),null); writeP(pk('question'),null); writePJson(pk('stats'),null); writeP(pk('finished'),null); writeP(pk('total'),null)
  }

  const confirmQuit = async () => {
    if (quitting) return
    clearLevelCompleteNotice()
    setQuitting(true)
    try {
      if (teamMode && teamSessionId) {
        // в командной игре игрок только выходит из сессии —
        // остальные участники продолжают прохождение без него
        try { await leaveTeamGame(teamSessionId) } catch {}
      }
      // Сохранённый прогресс не удаляем: игрок сможет продолжить игру позже.
      // Полностью сбрасывается прогресс кнопкой «Начать заново».
      // Сохраняем сразу (без debounce), т.к. после выхода компонент размонтируется.
      const gs = gameStateRef.current
      try {
        await saveGameProgress(game.id, {
          currentLevelIndex: gs.levelIndex,
          currentQuestionIndex: gs.questionIndex,
          // started=false: после выхода показываем экопи игры, а не продолжаем её
          gameState: { ...gs, started: false },
        })
      } catch {}
    } catch {}
    setShowQuitModal(false)
    setQuitting(false)
    setGameStarted(false)
    writeP(pk('started'), null)
    writeP(tpk('teamMode'), null); writeP(tpk('teamSession'), null); writeP(tpk('teamId'), null)
    // прогресс сохранён — игрок сможет продолжить игру позже
    setResumable((prev) => ({ ...prev, hasProgress: true }))
    if (onExit) onExit()
    else navigate('/games')
  }

  if (games.length === 0) {
    return (
      <div className="empty-state">
        Игра ещё не создана. Обратитесь к администратору.
      </div>
    )
  }

  if (error) return <div className="alert alert-error">{error}</div>
  if (loading && !game) return <div className="page-loader">Загрузка игры…</div>
  if (!game) return null

  const levels = game.levels || []
  const currentLevel = levels[currentLevelIndex]

  return (
    <div className="assoc-player">
      {/*
        В командном режиме верхний блок не нужен: TeamGamePlay сам показывает
        кнопку «← К списку игр» и шапку партии с таймерами и хостом.
        В остальных режимах блок остаётся на месте.
      */}
      {!teamMode && onExit && (
        <button type="button" className="btn btn-secondary assoc-back-btn" onClick={onExit}>
          ← К списку игр
        </button>
      )}

      {/* Активные партии команд — баннер в самом верху экрана игры */}
      {!teamMode && (
        <ActiveTeamGames onJoin={handleJoinActiveTeamGame} />
      )}
      {teamMode && !teamState && (
        // Восстанавливаем участие после перезагрузки страницы:
        // пока тянем актуальное состояние сессии — показываем загрузку,
        // а не приглашение присоединиться заново.
        <div className="page-loader">Восстанавливаем участие в командной игре…</div>
      )}

      {/*{!teamMode && (*/}
      {/*  <div className="assoc-player-top">*/}
      {/*    <div className="assoc-player-heading">*/}
      {/*      <h2 className="assoc-player-title">{game.title}</h2>*/}
      {/*      <span className="assoc-player-author">*/}
      {/*        автор: {game.creatorUsername || 'неизвестен'} · лимит: {formatMinutes(game.timeLimitMinutes)} мин.*/}
      {/*      </span>*/}
      {/*    </div>*/}
      {/*    {gameStarted && !finished && (*/}
      {/*      <button type="button" className="btn btn-danger" onClick={() => setShowQuitModal(true)}>*/}
      {/*        Завершить игру*/}
      {/*      </button>*/}
      {/*    )}*/}
      {/*  </div>*/}
      {/*)}*/}

      {showQuitModal && (
        <div className="modal-overlay" onClick={() => setShowQuitModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth:'520px', width:'90%' }}>
            <h3 className="modal-title">Вы уверены, что хотите завершить игру?</h3>
            <p className="modal-text">
              {teamMode
                ? 'Вы выйдете из командной игры. Остальные участники смогут продолжить прохождение без вас. Вы уверены?'
                : 'Ваш текущий прогресс будет сохранён, и вы вернётесь в главное меню. Продолжить игру можно будет позже.'}
            </p>
            <div className="modal-actions">
              <button type="button" className="btn btn-secondary" onClick={() => setShowQuitModal(false)} disabled={quitting}>Отмена</button>
              <button type="button" className="btn btn-danger" onClick={confirmQuit} disabled={quitting}>{quitting ? 'Завершение...' : 'Да, завершить'}</button>
            </div>
          </div>
        </div>
      )}

      {teamMode ? (
        teamState ? (
          <TeamGamePlay
            sessionId={teamSessionId}
            initialState={teamState}
            onExit={exitTeamMode}
            currentUserId={currentUserId}
            onMissing={resetTeamMode}
            onEnded={handleTeamGameEnded}
          />
        ) : (
          <div className="page-loader">Подключение к командной игре…</div>
        )
      ) : !gameStarted ? (
        <GameInfoScreen
          game={game}
          levelsCount={levels.length}
          starting={starting}
          onStart={startPlaying}
          onResume={resumePlaying}
          canResume={resumable.hasProgress}
          teams={teams}
          selectedTeamId={selectedTeamId}
          onSelectTeam={setSelectedTeamId}
          onStartTeam={startTeamPlay}
          teamError={teamError}
          leaderboard={leaderboard}
          leaderboardLoading={leaderboardLoading}
        />
      ) : finished ? (
        <StatisticsScreen
          stats={levelStats}
          totalTime={totalTime}
          resultError={resultError}
          leaderboard={leaderboard}
          leaderboardLoading={leaderboardLoading}
          currentUserId={currentUserId}
          onPlayAgain={playAgain}
          onHome={() => navigate('/games')}
        />
      ) : (
        <>
          <div className="assoc-level-progress">
            Уровень {currentLevelIndex + 1} из {levels.length}
          </div>
          {currentLevel.type === 'NORMAL' ? (
            <NormalLevelBoard
              key={currentLevel.id}
              game={game}
              level={currentLevel}
              progress={progress}
              savedQuestionIndex={savedQuestionIndex}
              onLevelComplete={(payload) => handleLevelComplete(currentLevel, payload)}
              onSave={scheduleSave}
            />
          ) : (
            <GameBoard
              key={currentLevel.id}
              game={game}
              level={currentLevel}
              progress={progress}
              onLevelComplete={(payload) => handleLevelComplete(currentLevel, payload)}
              onSave={scheduleSave}
            />
          )}
        </>
      )}

      {levelCompleteNotice && (
        <div className="assoc-toast assoc-level-toast" role="status" aria-live="polite">
          Уровень пройден!
        </div>
      )}
    </div>
  )
}

/** Экран информации об игре перед стартом. */
function GameInfoScreen({ game, levelsCount, starting, onStart, onResume, canResume, teams, selectedTeamId, onSelectTeam, onStartTeam, teamError, leaderboard, leaderboardLoading }) {
  const media = Array.isArray(game.mediaUrls) ? game.mediaUrls : []
  const description = (game.description || '').trim()
  const date = formatGameDate(game.gameDate)
  const stars = difficultyStars(game.difficulty)

  return (
    <div className="assoc-start-screen">
      <div className="assoc-info-card">
        <h2 className="assoc-info-title">{game.title}</h2>

        <div className="assoc-info-meta">
          <span className="assoc-info-badge">
            <span aria-hidden="true">👤</span> {game.creatorUsername || 'неизвестен'}
          </span>
          {date && (
            <span className="assoc-info-badge">
              <span aria-hidden="true">📅</span> {date}
            </span>
          )}
          <span className="assoc-info-badge">
            <span aria-hidden="true">🔥</span>
            {stars ? <span className="assoc-info-stars">{stars}</span> : 'Сложность не указана'}
          </span>
          <span className="assoc-info-badge">
            <span aria-hidden="true">⏱️</span> {formatMinutes(game.timeLimitMinutes)} минут
          </span>
          <span className="assoc-info-badge">
            <span aria-hidden="true">🎯</span> {levelsCount} {levelsCount === 1 ? 'уровень' : 'уровней'}
          </span>
        </div>

        {media.length > 0 && (
          <div className="assoc-info-block">
            <h3 className="assoc-info-block-title">Материалы игры</h3>
            <div className="assoc-info-media">
              {media.map((url, index) => {
                const kind = mediaKind(url)
                if (kind === 'image') {
                  return <img key={index} src={url} alt="Медиа к игре" className="assoc-info-media-img" />
                }
                if (kind === 'audio') {
                  return <audio key={index} src={url} controls className="assoc-info-media-audio" />
                }
                if (kind === 'video') {
                  return <video key={index} src={url} controls className="assoc-info-media-video" />
                }
                return (
                  <a key={index} href={url} target="_blank" rel="noreferrer" className="assoc-info-media-link">
                    📎 {url}
                  </a>
                )
              })}
            </div>
          </div>
        )}

        {description && (
          <div className="assoc-info-block">
            <h3 className="assoc-info-block-title">Описание</h3>
            <p className="assoc-info-description">{description}</p>
          </div>
        )}

        {/* Статистика игры: рейтинг одиночных игроков и команд с детализацией по уровням */}
        <GameStatisticsBlock
          entries={leaderboard}
          loading={leaderboardLoading}
          error={null}
        />

        {teamError && <div className="alert alert-error">{teamError}</div>}
        <div className="assoc-info-actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          {canResume ? (
            <>
              <button type="button" className="btn btn-primary assoc-info-start" onClick={onResume} disabled={starting}>
                {starting ? 'Запускаем…' : 'Продолжить игру'}
              </button>
              <button type="button" className="btn btn-secondary" onClick={onStart} disabled={starting}>
                Начать заново
              </button>
            </>
          ) : (
            <button type="button" className="btn btn-primary assoc-info-start" onClick={onStart} disabled={starting}>
              {starting ? 'Запускаем…' : 'Играть одному'}
            </button>
          )}
          {teams && teams.length > 0 ? (
            <>
              <select
                className="assoc-input"
                style={{ width: 'auto' }}
                value={selectedTeamId || teams[0].id}
                onChange={(e) => onSelectTeam && onSelectTeam(Number(e.target.value))}
              >
                {teams.map((t) => (
                  <option key={t.id} value={t.id}>{t.name}</option>
                ))}
              </select>
              <button type="button" className="btn btn-secondary" onClick={onStartTeam}>
                Играть командой
              </button>
            </>
          ) : (
            <button type="button" className="btn btn-secondary" disabled title="Не состоите в команде">
              Играть командой
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/** Экран итоговой статистики после последнего уровня. */
function StatisticsScreen({ stats, totalTime, resultError, leaderboard, leaderboardLoading, currentUserId, onPlayAgain, onHome }) {
  const rows = (stats || []).map((stat, index) => ({
    levelId: stat.levelId,
    levelIndex: index,
    title: stat.name,
    pureSeconds: stat.netTime,
    penaltySeconds: stat.penaltyTime,
    totalSeconds: stat.totalTime,
  }))
  return (
    <div className="assoc-statistics">
      <div className="assoc-statistics-card">
        <div className="assoc-win-emoji" aria-hidden="true">🏆</div>
        <h3 className="assoc-statistics-title">Игра завершена!</h3>

        <GameStatisticsTable levels={rows} totalSeconds={totalTime} />

        {resultError && <p className="assoc-win-error">{resultError}</p>}

        <Leaderboard
          entries={leaderboard || []}
          loading={leaderboardLoading}
          currentUserId={currentUserId}
          limit={5}
          title="Лучшие результаты"
        />

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onHome}>
            Вернуться на главную
          </button>
          <button type="button" className="btn btn-primary" onClick={onPlayAgain}>
            Играть снова
          </button>
        </div>
      </div>
    </div>
  )
}

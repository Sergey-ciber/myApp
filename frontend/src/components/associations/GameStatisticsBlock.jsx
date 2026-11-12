import { Fragment, useMemo, useState } from 'react'
import { formatDate, formatStatTime } from './constants'

/**
 * Блок «Статистика игры» на странице карточки игры.
 *
 * Сворачиваемый блок с таблицей лидеров:
 *  - «Место», «Команда / Игрок», «Время игры», «Дата прохождения»;
 *  - по клику на строку раскрывается детализация по уровням
 *    (чистое время, штраф за подсказки, итог уровня).
 *
 * Данные приходят с сервера уже отсортированными по возрастанию времени.
 * Одиночные игроки помечаются иконкой 👤, команды — 👥.
 */

const placeLabel = (rank) => {
  if (rank === 1) return '🥇'
  if (rank === 2) return '🥈'
  if (rank === 3) return '🥉'
  return rank
}

/** Ключ строки: команда или игрок. */
const rowKey = (entry, index) =>
  entry.team ? `team-${entry.teamId ?? index}` : `user-${entry.userId ?? index}`

export default function GameStatisticsBlock({ entries, loading, error }) {
  // open — раскрыт ли сам блок; expandedRow — какая строка раскрыта внутри.
  const [open, setOpen] = useState(false)
  const [expandedRow, setExpandedRow] = useState(null)

  const rows = useMemo(() => (Array.isArray(entries) ? entries : []), [entries])

  const toggleRow = (key) => setExpandedRow((prev) => (prev === key ? null : key))

  return (
    <section className="assoc-game-stats">
      <button
        type="button"
        className="assoc-game-stats-toggle"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
      >
        <span className="assoc-game-stats-toggle-title">
          <span aria-hidden="true">📊</span> Статистика игры
        </span>
        <span className="assoc-game-stats-toggle-caret" aria-hidden="true">
          {open ? '▲' : '▼'}
        </span>
      </button>

      {open && (
        <div className="assoc-game-stats-body">
          {loading ? (
            <p className="assoc-game-stats-empty">Загрузка результатов…</p>
          ) : error ? (
            <p className="assoc-game-stats-empty assoc-game-stats-error">{error}</p>
          ) : rows.length === 0 ? (
            <p className="assoc-game-stats-empty">
              Пока никто не прошёл эту игру до конца. Разгадайте все уровни и станьте первым!
            </p>
          ) : (
            <div className="assoc-table-wrap">
              <table className="assoc-table assoc-game-stats-table">
                <thead>
                  <tr>
                    <th className="assoc-col-place">Место</th>
                    <th>Команда / Игрок</th>
                    <th className="assoc-col-time">Время игры</th>
                    <th className="assoc-col-date">Дата прохождения</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((entry, index) => {
                    const key = rowKey(entry, index)
                    const isOpen = expandedRow === key
                    const details = Array.isArray(entry.levelsStats) ? entry.levelsStats : []
                    return (
                      <Fragment key={key}>
                        <tr
                          className={`assoc-game-stats-row${isOpen ? ' is-open' : ''}`}
                          onClick={(e) => {
                            e.stopPropagation()
                            toggleRow(key)
                          }}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault()
                              e.stopPropagation()
                              toggleRow(key)
                            }
                          }}
                          tabIndex={0}
                          role="button"
                          aria-expanded={isOpen}
                        >
                          <td className="assoc-place">{placeLabel(entry.rank)}</td>
                          <td className="assoc-player-cell">
                            <span className="assoc-game-stats-kind" aria-hidden="true">
                              {entry.team ? '👨‍👩‍👧‍👧' : '🦍'}
                            </span>{' '}
                            {entry.playerName}
                          </td>
                          <td className="assoc-time-cell" title={`${entry.timeSeconds} сек`}>
                            {formatStatTime(entry.timeSeconds)}
                          </td>
                          <td className="assoc-date-cell">{formatDate(entry.completedAt)}</td>
                        </tr>
                        {isOpen && (
                          <tr className="assoc-game-stats-details-row">
                            <td colSpan={4}>
                              {details.length === 0 ? (
                                <p className="assoc-game-stats-empty">
                                  Детализация по уровням недоступна для этого прохождения.
                                </p>
                              ) : (
                                <table className="assoc-statistics-table assoc-game-stats-details-table">
                                  <thead>
                                    <tr>
                                      <th>Уровень</th>
                                      <th>Чистое время</th>
                                      <th>Штрафное время</th>
                                      <th>Общее время уровня</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {details.map((level, levelIndex) => (
                                      <tr key={`${key}-${level.levelId ?? levelIndex}`}>
                                        <td className="assoc-statistics-level">
                                          {level.levelName || `Уровень ${levelIndex + 1}`}
                                        </td>
                                        <td>{formatStatTime(level.netSeconds)}</td>
                                        <td>{formatStatTime(level.penaltySeconds)}</td>
                                        <td>{formatStatTime(level.totalSeconds)}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

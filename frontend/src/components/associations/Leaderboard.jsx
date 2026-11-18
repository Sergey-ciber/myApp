import { formatDate, formatStatTime } from './constants'

const placeLabel = (place) => {
  if (place === 1) return '🥇'
  if (place === 2) return '🥈'
  if (place === 3) return '🥉'
  return place
}

/**
 * Таблица лучших результатов игры.
 *
 * places — уже отсортированный сервером массив (лучшее время сверху).
 * В массиве смешаны одиночные игроки (👤) и команды (👥).
 */
export default function Leaderboard({ entries, loading, currentUserId, limit, title = 'Рейтинг игры' }) {
  const rows = limit ? entries.slice(0, limit) : entries

  return (
    <section className="assoc-leaderboard">
      {title && <h3 className="assoc-leaderboard-title">{title}</h3>}

      {loading ? (
        <p className="assoc-leaderboard-empty">Загрузка результатов…</p>
      ) : entries.length === 0 ? (
        <p className="assoc-leaderboard-empty">
          Пока никто не прошёл игру. Разгадайте колонку 5 и станьте первым!
        </p>
      ) : (
        <div className="assoc-table-wrap assoc-leaderboard-table-wrap">
          <table className="assoc-table assoc-leaderboard-table">
            <thead>
              <tr>
                <th className="assoc-col-place">Место</th>
                <th>Команда / Игрок</th>
                <th className="assoc-col-time">Время</th>
                <th className="assoc-col-date">Дата</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((entry, index) => {
                const key = entry.team ? `team-${entry.teamId ?? index}` : `user-${entry.userId ?? index}`
                const isMe = !entry.team && entry.userId != null && entry.userId === currentUserId
                return (
                  <tr key={key} className={isMe ? 'is-me' : ''}>
                    <td className="assoc-place" data-label="Место">
                      <span className="assoc-cell-value">{placeLabel(entry.rank)}</span>
                    </td>
                    <td className="assoc-player-cell" data-label="Команда / Игрок">
                      <span className="assoc-cell-value assoc-player-cell-value">
                        <span className="assoc-game-stats-kind" aria-hidden="true">
                          {entry.team ? '👨‍👩‍👧‍👧' : '🦍'}
                        </span>{' '}
                        <span className="assoc-player-name">{entry.playerName}</span>
                        {isMe && <span className="assoc-me">вы</span>}
                      </span>
                    </td>
                    <td className="assoc-time-cell" data-label="Время" title={`${entry.timeSeconds} сек`}>
                      <span className="assoc-cell-value">{formatStatTime(entry.timeSeconds)}</span>
                    </td>
                    <td className="assoc-date-cell" data-label="Дата">
                      <span className="assoc-cell-value">{formatDate(entry.completedAt)}</span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {limit && entries.length > rows.length && (
        <p className="assoc-leaderboard-more">…и ещё {entries.length - rows.length} в таблице ниже</p>
      )}
    </section>
  )
}

import { formatDate, formatTime } from './constants'

const placeLabel = (place) => {
  if (place === 1) return '🥇'
  if (place === 2) return '🥈'
  if (place === 3) return '🥉'
  return place
}

/**
 * Таблица лучших результатов игры.
 * places — уже отсортированный сервером массив (лучшее время сверху).
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
        <div className="assoc-table-wrap">
          <table className="assoc-table">
            <thead>
              <tr>
                <th className="assoc-col-place">Место</th>
                <th>Игрок</th>
                <th className="assoc-col-time">Время</th>
                <th className="assoc-col-date">Дата</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((entry) => (
                <tr key={entry.userId} className={entry.userId === currentUserId ? 'is-me' : ''}>
                  <td className="assoc-place">{placeLabel(entry.place)}</td>
                  <td className="assoc-player-cell">
                    {entry.username}
                    {entry.userId === currentUserId && <span className="assoc-me">вы</span>}
                  </td>
                  <td className="assoc-time-cell" title={`${entry.timeSeconds} сек`}>
                    {formatTime(entry.timeSeconds)}
                  </td>
                  <td className="assoc-date-cell">{formatDate(entry.completedAt)}</td>
                </tr>
              ))}
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

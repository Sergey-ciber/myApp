import { formatStatTime } from './constants'

/**
 * Таблица статистики времени по уровням.
 *
 * Используется и в командной игре (данные с сервера), и в одиночном режиме.
 * Ожидаемые поля строки: levelId, levelIndex, title, pureSeconds,
 * penaltySeconds, totalSeconds, timedOut.
 *
 * Время уже посчитано сервером в секундах — компонент только форматирует
 * его в ММ:СС (или ЧЧ:ММ:СС для партий длиннее часа).
 */
export default function GameStatisticsTable({ levels, totalSeconds, emptyText }) {
  const rows = Array.isArray(levels) ? levels : []

  if (rows.length === 0) {
    return <p className="assoc-statistics-empty">{emptyText || 'Статистика пока недоступна'}</p>
  }

  return (
    <div className="assoc-statistics-table-wrap">
      <table className="assoc-statistics-table">
        <thead>
          <tr>
            <th>Уровень</th>
            <th>Чистое время</th>
            <th>Штрафное время</th>
            <th>Общее время уровня</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.levelId ?? `level-${row.levelIndex ?? index}`}>
              <td data-label="Уровень">
                <span className="assoc-statistics-level">
                  {(row.levelIndex ?? index) + 1}. {row.title || `Уровень ${(row.levelIndex ?? index) + 1}`}
                </span>
                {row.timedOut && <span className="assoc-statistics-tag">таймаут</span>}
              </td>
              <td data-label="Чистое время">{formatStatTime(row.pureSeconds)}</td>
              <td data-label="Штрафное время">{formatStatTime(row.penaltySeconds)}</td>
              <td data-label="Общее время уровня">{formatStatTime(row.totalSeconds)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan="3">Общее время по всей игре</td>
            <td data-label="Общее время по всей игре">{formatStatTime(totalSeconds)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

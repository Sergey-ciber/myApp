import { formatStatTime } from './constants'

/**
 * Таблица статистики времени по уровням.
 *
 * Используется и в командной игре (данные с сервера), и в одиночном режиме.
 * Ожидаемые поля строки: levelId, levelIndex, title, pureSeconds,
 * penaltySeconds, totalSeconds, timedOut, includeTimeInStats.
 *
 * Время уже посчитано сервером в секундах — компонент только форматирует
 * его в ММ:СС (или ЧЧ:ММ:СС для партий длиннее часа).
 * Уровни с includeTimeInStats=false отображаются, но не входят в итог.
 */
export default function GameStatisticsTable({ levels, totalSeconds, emptyText }) {
  const rows = Array.isArray(levels) ? levels : []
  const effectiveTotal = rows.reduce((sum, row) => {
    const included = row?.includeTimeInStats !== false
    const value = Number(row?.totalSeconds ?? 0)
    return included ? sum + (Number.isFinite(value) ? value : 0) : sum
  }, 0)
  const shownTotal = rows.length > 0 ? effectiveTotal : totalSeconds

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
          {rows.map((row, index) => {
            const included = row?.includeTimeInStats !== false
            return (
              <tr
                key={row.levelId ?? `level-${row.levelIndex ?? index}`}
                className={included ? undefined : 'assoc-statistics-row-excluded'}
              >
                <td data-label="Уровень">
                  <span className="assoc-statistics-level">
                    {(row.levelIndex ?? index) + 1}. {row.title || `Уровень ${(row.levelIndex ?? index) + 1}`}
                  </span>
                  {row.timedOut && <span className="assoc-statistics-tag">таймаут</span>}
                  {!included && <span className="assoc-statistics-tag">не учитывается в итоге</span>}
                </td>
                <td data-label="Чистое время">{formatStatTime(row.pureSeconds)}</td>
                <td data-label="Штрафное время">{formatStatTime(row.penaltySeconds)}</td>
                <td data-label="Общее время уровня">{formatStatTime(row.totalSeconds)}</td>
              </tr>
            )
          })}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan="3">Общее время по всей игре</td>
            <td data-label="Общее время по всей игре">{formatStatTime(shownTotal)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

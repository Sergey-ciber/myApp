import { COLUMN_HEADERS } from './constants'

/**
 * Шапка пирамиды: та же сетка из 5 колонок, что и у поля,
 * поэтому заголовки стоят ровно над своими колонками.
 */
export default function PyramidHead() {
  return (
    <div className="assoc-pyramid-head">
      {COLUMN_HEADERS.map((header) => (
        <div className="assoc-column-header" key={header.title}>
          <h3>{header.title}</h3>
          <span>{header.subtitle}</span>
        </div>
      ))}
    </div>
  )
}

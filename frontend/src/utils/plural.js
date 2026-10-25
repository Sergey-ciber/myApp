/** 1 победа, 2 победы, 5 побед. */
export function plural(count, one, few, many) {
  const value = Math.abs(count) % 100
  const last = value % 10
  if (value > 10 && value < 20) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}

export const winsWord = (count) => plural(count, 'победа', 'победы', 'побед')
export const membersWord = (count) => plural(count, 'участник', 'участника', 'участников')
export const gamesWord = (count) => plural(count, 'игра', 'игры', 'игр')

export const winsLabel = (wins) => `${wins} ${winsWord(wins)}`

export const membersLabel = (count) => `${count} ${membersWord(count)}`

export const gamesLabel = (count) => `${count} ${gamesWord(count)}`

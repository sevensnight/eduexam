export function correctnessForScore(score, maxScore) {
  const value = Number(score)
  const maximum = Number(maxScore)
  if (!Number.isFinite(value) || value <= 0) return 'N'
  if (Number.isFinite(maximum) && maximum > 0 && value >= maximum) return 'Y'
  return 'P'
}

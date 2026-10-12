export function selectRecordForRow(records, row) {
  if (!Array.isArray(records) || row?.record_id == null) return null
  return records.find((record) => String(record.id) === String(row.record_id)) ?? null
}

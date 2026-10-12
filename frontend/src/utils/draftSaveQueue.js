/**
 * Serialize draft writes so an older, slower request can never overwrite a
 * newer snapshot. Submission locks the queue and drains any request that is
 * already in flight before reading the final compare-and-set revision.
 */
export function createDraftSaveQueue(save) {
  let tail = Promise.resolve()
  let locked = false
  let disposed = false

  function enqueue(payload) {
    if (locked || disposed) return Promise.resolve(undefined)
    const task = tail.catch(() => undefined).then(() => {
      // A task may have been queued before submission or unmount locked the
      // queue. Re-check at execution time so it cannot start afterwards.
      if (locked || disposed) return undefined
      return save(payload)
    })
    tail = task
    return task
  }

  return {
    enqueue,
    lock() {
      if (!disposed) locked = true
    },
    async waitForIdle() {
      await tail.catch(() => undefined)
    },
    unlock() {
      if (!disposed) locked = false
    },
    dispose() {
      disposed = true
      locked = true
    },
    isLocked() { return locked || disposed },
  }
}

/**
 * Decide whether a tab-local snapshot can safely overlay the server draft.
 * A snapshot is recoverable only when it was edited from the exact revision
 * returned by /start. Older snapshots must never borrow the server's newest
 * revision and thereby bypass compare-and-set protection.
 */
export function classifyLocalDraft(saved, recordId, serverVersion) {
  if (!saved || saved.record_id == null || recordId == null
      || String(saved.record_id) !== String(recordId)) {
    return { restore: false, discard: false }
  }

  const localVersion = Number(saved.draft_version)
  const currentVersion = Number(serverVersion)
  if (!Number.isInteger(localVersion) || !Number.isInteger(currentVersion)
      || localVersion !== currentVersion) {
    return { restore: false, discard: true }
  }

  return { restore: saved.dirty === true, discard: false }
}

export function answerListsMatch(left, right) {
  if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false
  return left.every((answer, index) => {
    const other = right[index]
    return String(answer?.question_id) === String(other?.question_id)
      && (answer?.user_answer ?? '') === (other?.user_answer ?? '')
  })
}

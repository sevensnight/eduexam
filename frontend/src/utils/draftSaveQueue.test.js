import { describe, expect, it, vi } from 'vitest'

import { answerListsMatch, classifyLocalDraft, createDraftSaveQueue } from './draftSaveQueue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('draft save queue', () => {
  it('serializes autosaves even when the first network request is slow', async () => {
    const first = deferred()
    const calls = []
    const save = vi.fn((payload) => {
      calls.push(payload.version)
      return payload.version === 'old' ? first.promise : Promise.resolve()
    })
    const queue = createDraftSaveQueue(save)

    const oldSave = queue.enqueue({ version: 'old' })
    const newSave = queue.enqueue({ version: 'new' })
    await vi.waitFor(() => expect(calls).toEqual(['old']))

    first.resolve()
    await Promise.all([oldSave, newSave])
    expect(calls).toEqual(['old', 'new'])
  })

  it('locks immediately without waiting for a slow autosave', async () => {
    const first = deferred()
    const calls = []
    const save = vi.fn((payload) => {
      calls.push(payload.version)
      return payload.version === 'old' ? first.promise : Promise.resolve()
    })
    const queue = createDraftSaveQueue(save)

    const oldSave = queue.enqueue({ version: 'old' })
    await vi.waitFor(() => expect(calls).toEqual(['old']))
    queue.lock()
    await queue.enqueue({ version: 'must-not-run' })
    expect(calls).toEqual(['old'])

    first.resolve()
    await oldSave
    expect(calls).toEqual(['old'])
  })

  it('can drain an in-flight save before final submission reads its revision', async () => {
    const first = deferred()
    const save = vi.fn(() => first.promise)
    const queue = createDraftSaveQueue(save)
    const running = queue.enqueue({ version: 'running' })
    await vi.waitFor(() => expect(save).toHaveBeenCalledTimes(1))

    queue.lock()
    let drained = false
    const waiting = queue.waitForIdle().then(() => { drained = true })
    await Promise.resolve()
    expect(drained).toBe(false)

    first.resolve()
    await Promise.all([running, waiting])
    expect(drained).toBe(true)
  })

  it('cannot be unlocked or enqueue more work after disposal', async () => {
    const first = deferred()
    const save = vi.fn(payload => payload.version === 'running' ? first.promise : Promise.resolve())
    const queue = createDraftSaveQueue(save)

    const running = queue.enqueue({ version: 'running' })
    const queued = queue.enqueue({ version: 'queued-before-unmount' })
    await vi.waitFor(() => expect(save).toHaveBeenCalledTimes(1))
    queue.dispose()
    queue.unlock()
    await queue.enqueue({ version: 'after-unmount' })
    first.resolve()
    await Promise.all([running, queued])

    expect(queue.isLocked()).toBe(true)
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('rejects an older tab-local snapshot instead of lending it the newest server revision', () => {
    const oldTab = {
      record_id: 8,
      draft_version: 1,
      dirty: true,
      answers: { 10: 'old answer' },
    }

    expect(classifyLocalDraft(oldTab, 8, 2)).toEqual({ restore: false, discard: true })
  })

  it('restores only a dirty local snapshot based on the same server revision', () => {
    expect(classifyLocalDraft({ record_id: 8, draft_version: 2, dirty: true }, 8, 2))
      .toEqual({ restore: true, discard: false })
    expect(classifyLocalDraft({ record_id: 8, draft_version: 2, dirty: false }, 8, 2))
      .toEqual({ restore: false, discard: false })
  })

  it('detects whether edits made during an in-flight save are still dirty', () => {
    const sent = [{ question_id: 1, user_answer: 'A' }]
    expect(answerListsMatch(sent, [{ question_id: 1, user_answer: 'A' }])).toBe(true)
    expect(answerListsMatch(sent, [{ question_id: 1, user_answer: 'B' }])).toBe(false)
  })
})

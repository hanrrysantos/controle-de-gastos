import { describe, expect, it } from 'vitest'
import { api, ApiError } from '../api'
import { mockApi } from './utils'

describe('api()', () => {
  it('prefixes /api and sends JSON + bearer headers only when needed', async () => {
    const { calls } = mockApi({ 'POST /x': { body: { ok: true } }, 'GET /y': { body: [1] } })

    await expect(api('/x', { method: 'POST', body: '{"a":1}' }, 'tk')).resolves.toEqual({ ok: true })
    await expect(api('/y')).resolves.toEqual([1])

    expect(calls[0].headers).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer tk' })
    expect(calls[1].headers).toEqual({})
  })

  it('returns null for 204', async () => {
    mockApi({ 'DELETE /z': { status: 204 } })
    await expect(api('/z', { method: 'DELETE' })).resolves.toBeNull()
  })

  it('throws ApiError with the server message and status', async () => {
    mockApi({ 'GET /e': { status: 422, body: { message: 'Valor inválido' } } })
    const error = await api('/e').catch((err: unknown) => err)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ message: 'Valor inválido', status: 422 })
  })

  it('falls back to a generic message when the body has none', async () => {
    mockApi({ 'GET /e': { status: 500, body: 'oops' } })
    await expect(api('/e')).rejects.toMatchObject({ message: 'Não foi possível concluir a operação.', status: 500 })
  })
})

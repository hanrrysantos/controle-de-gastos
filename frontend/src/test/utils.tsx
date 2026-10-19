import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import App from '../App'
import type { Sessao } from '../api'

export type Call = { method: string; path: string; body: unknown; headers: Record<string, string> }
type Reply = { status?: number; body?: unknown }
type Handler = (call: Call) => Reply | undefined

/** Stubs global fetch; handlers are matched by "METHOD /path" (query string included). */
export function mockApi(routes: Record<string, Reply | Handler>) {
  const calls: Call[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    if (!url.startsWith('/api')) throw new Error('Unexpected URL ' + url)
    const call: Call = {
      method: init.method ?? 'GET',
      path: url.slice('/api'.length),
      body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      headers: (init.headers ?? {}) as Record<string, string>,
    }
    calls.push(call)
    const route = routes[call.method + ' ' + call.path]
    const reply = typeof route === 'function' ? route(call) : route
    if (!reply) throw new Error('Unhandled request ' + call.method + ' ' + call.path)
    const status = reply.status ?? 200
    return new Response(status === 204 ? null : JSON.stringify(reply.body ?? null), {
      status, headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export const session: Sessao = { token: 'tok-123', nome: 'Ana Souza' }

export function renderApp(path: string, { loggedIn = true } = {}) {
  if (loggedIn) sessionStorage.setItem('cdg-session', JSON.stringify(session))
  window.history.pushState(null, '', path)
  const user = userEvent.setup()
  return { user, ...render(<App />) }
}

export const emptyResumo = (mes: string) => ({
  mes, totalReceitas: '0.00', totalDespesas: '0.00', saldo: '0.00', receitasMesAnterior: '0.00',
  despesasMesAnterior: '0.00', saldoAcumulado: '0.00', quantidadeLancamentos: 0, categorias: [],
})

export const currentMonth = () => {
  const date = new Date()
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0')
}

/** Routes the dashboard needs on load. */
export const dashboardRoutes = () => ({
  ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: emptyResumo(currentMonth()) },
  'GET /transacoes': { body: [] },
  'GET /integracoes/telegram/vinculo': { body: { vinculado: false } },
})

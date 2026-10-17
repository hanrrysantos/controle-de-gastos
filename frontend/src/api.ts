export type Tipo = 'RECEITA' | 'DESPESA'

export type Sessao = { token: string; nome: string }
export type Categoria = { id: number; nome: string; tipo: Tipo }
export type Transacao = {
  id: number
  valor: string
  descricao: string
  data: string
  nomeCategoria: string
  tipo: Tipo
}
export type Resumo = { totalReceitas: string; totalDespesas: string; saldo: string }

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export async function api<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(`${import.meta.env.VITE_API_URL ?? '/api'}${path}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(body?.message ?? 'Não foi possível concluir a operação.', response.status)
  }
  return body as T
}

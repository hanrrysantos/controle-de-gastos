import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Categoria, Transacao } from '../api'
import { currentMonth, dashboardRoutes, emptyResumo, mockApi, renderApp } from './utils'

const categorias: Categoria[] = [
  { id: 1, nome: 'Mercado', tipo: 'DESPESA', quantidadeLancamentos: 0 },
  { id: 2, nome: 'Transporte', tipo: 'DESPESA', quantidadeLancamentos: 1 },
  { id: 3, nome: 'Salário', tipo: 'RECEITA', quantidadeLancamentos: 0 },
]

const isoToday = () => {
  const date = new Date()
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}

describe('new transaction', () => {
  it('builds the amount from typed digits and posts the transaction', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'POST /transacoes': { status: 201, body: {} },
      ...dashboardRoutes(),
    })
    const { user } = renderApp('/novo')

    const amount = screen.getByLabelText('Valor em reais')
    expect(amount).toHaveValue('0,00')
    await user.type(amount, '4590')
    expect(amount).toHaveValue('45,90')

    await user.click(await screen.findByRole('radio', { name: 'Transporte' }))
    expect(screen.queryByRole('radio', { name: 'Salário' })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Descrição'), '  Uber  ')
    await user.click(screen.getByRole('button', { name: 'Salvar despesa' }))

    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeInTheDocument()
    const post = calls.find((call) => call.method === 'POST')
    expect(post).toMatchObject({
      path: '/transacoes',
      body: { valor: '45.90', descricao: 'Uber', data: isoToday(), categoriaId: 2 },
      headers: { Authorization: 'Bearer tok-123', 'Content-Type': 'application/json' },
    })
    expect(screen.getByRole('status')).toHaveTextContent('Lançamento salvo.')
  })

  it('keeps the draft and selects the new category after creating one', async () => {
    const lista = [...categorias]
    const { calls } = mockApi({
      'GET /categorias': () => ({ body: lista }),
      'POST /categorias': (call) => {
        const nova = { id: 9, ...(call.body as { nome: string; tipo: 'DESPESA' }), quantidadeLancamentos: 0 }
        lista.push(nova)
        return { status: 201, body: nova }
      },
    })
    const { user } = renderApp('/novo')

    await user.type(screen.getByLabelText('Valor em reais'), '4590')
    await user.type(screen.getByLabelText('Descrição'), 'Ração')
    await user.click(await screen.findByRole('link', { name: 'Nova' }))
    await user.type(await screen.findByLabelText('Nome da categoria'), 'Pets')
    await user.click(screen.getByRole('button', { name: 'Salvar categoria' }))

    expect(await screen.findByRole('radio', { name: 'Pets' })).toBeChecked()
    expect(screen.getByRole('heading', { name: 'Novo lançamento' })).toBeInTheDocument()
    expect(screen.getByLabelText('Valor em reais')).toHaveValue('45,90')
    expect(screen.getByLabelText('Descrição')).toHaveValue('Ração')
    expect(calls.find((call) => call.method === 'POST')).toMatchObject({ body: { nome: 'Pets', tipo: 'DESPESA' } })
  })

  it('switching to receita shows only income categories', async () => {
    mockApi({ 'GET /categorias': { body: categorias } })
    const { user } = renderApp('/novo')

    await screen.findByRole('radio', { name: 'Mercado' })
    await user.click(screen.getByRole('button', { name: 'Receita' }))

    expect(screen.getByRole('radio', { name: 'Salário' })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: 'Mercado' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar receita' })).toBeEnabled()
  })

  it('requires an amount and then a category', async () => {
    const { calls } = mockApi({ 'GET /categorias': { body: categorias } })
    const { user } = renderApp('/novo')

    await screen.findByRole('radio', { name: 'Mercado' })
    await user.type(screen.getByLabelText('Descrição'), 'Almoço')
    await user.click(screen.getByRole('button', { name: 'Salvar despesa' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Digite um valor maior que zero.')

    await user.type(screen.getByLabelText('Valor em reais'), '1200')
    await user.click(screen.getByRole('button', { name: 'Salvar despesa' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Escolha uma categoria.')

    expect(calls.filter((call) => call.method !== 'GET')).toHaveLength(0)
  })

  it('disables saving when there is no category of the selected type', async () => {
    mockApi({ 'GET /categorias': { body: categorias.filter((item) => item.tipo === 'RECEITA') } })
    renderApp('/novo')

    expect(await screen.findByText('Crie uma categoria de despesa para continuar.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar despesa' })).toBeDisabled()
  })
})

describe('edit transaction', () => {
  const transacao: Transacao = {
    id: 7, valor: '1234.50', descricao: 'Aluguel', data: '2026-09-05', categoriaId: 1, nomeCategoria: 'Mercado', tipo: 'DESPESA',
  }

  it('pre-fills the form from the API and sends PUT', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'GET /transacoes': { body: [{ ...transacao, id: 6, descricao: 'Outro' }, transacao] },
      'PUT /transacoes/7': { body: transacao },
      ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: emptyResumo(currentMonth()) },
    })
    const { user } = renderApp('/lancamentos/7/editar')

    expect(await screen.findByRole('heading', { name: 'Editar lançamento' })).toBeInTheDocument()
    const amount = await screen.findByLabelText('Valor em reais')
    expect(amount).toHaveValue('1.234,50')
    expect(screen.getByLabelText('Descrição')).toHaveValue('Aluguel')
    expect(screen.getByRole('radio', { name: 'Mercado' })).toBeChecked()
    expect(screen.getByLabelText('Escolher data')).toHaveValue('2026-09-05')
    expect(screen.getByRole('button', { name: 'Despesa' })).toHaveAttribute('aria-pressed', 'true')

    await user.type(amount, '0')
    expect(amount).toHaveValue('12.345,00')
    const description = screen.getByLabelText('Descrição')
    await user.clear(description)
    await user.type(description, 'Aluguel setembro')
    await user.click(screen.getByRole('radio', { name: 'Transporte' }))
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByRole('heading', { name: 'Extrato' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT')).toMatchObject({
      path: '/transacoes/7',
      body: { valor: '12345.00', descricao: 'Aluguel setembro', data: '2026-09-05', categoriaId: 2 },
    })
  })

  it('shows an error when the transaction does not exist', async () => {
    mockApi({ 'GET /categorias': { body: categorias }, 'GET /transacoes': { body: [transacao] } })
    renderApp('/lancamentos/99/editar')

    expect(await screen.findByRole('alert')).toHaveTextContent('Lançamento não encontrado.')
  })
})

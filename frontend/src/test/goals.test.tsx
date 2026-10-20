import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Categoria, ResumoCategoria } from '../api'
import { currentMonth, dashboardRoutes, emptyResumo, mockApi, renderApp } from './utils'

const categorias: Categoria[] = [
  { id: 3, nome: 'Mercado', tipo: 'DESPESA', quantidadeLancamentos: 2, metaMensal: '800.00' },
  { id: 6, nome: 'Salário', tipo: 'RECEITA', quantidadeLancamentos: 0, metaMensal: null },
]

describe('monthly goals', () => {
  it('shows the saved goal and sends a new one', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'PUT /categorias/3': { body: {} },
      ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: emptyResumo(currentMonth()) },
    })
    const { user } = renderApp('/categorias/3/editar')

    const goal = await screen.findByLabelText('Meta mensal em reais')
    expect(goal).toHaveValue('800,00')
    await user.click(screen.getByRole('button', { name: 'Remover meta' }))
    expect(goal).toHaveValue('')
    await user.type(goal, '125050')
    expect(goal).toHaveValue('1.250,50')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT')).toMatchObject({ body: { nome: 'Mercado', tipo: 'DESPESA', metaMensal: '1250.50' } })
  })

  it('hides the goal for income categories and sends none', async () => {
    const { calls } = mockApi({ 'GET /categorias': { body: categorias }, 'PUT /categorias/6': { body: {} },
      ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: emptyResumo(currentMonth()) } })
    const { user } = renderApp('/categorias/6/editar')

    await screen.findByLabelText('Nome da categoria')
    expect(screen.queryByLabelText('Meta mensal em reais')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT')).toMatchObject({ body: { tipo: 'RECEITA', metaMensal: null } })
  })

  it('lists goal progress on the dashboard, most used first', async () => {
    const resumo: ResumoCategoria[] = [
      { id: 1, nome: 'Mercado', tipo: 'DESPESA', total: '900.00', quantidade: 3, metaMensal: '800.00' },
      { id: 2, nome: 'Lazer', tipo: 'DESPESA', total: '170.00', quantidade: 1, metaMensal: '200.00' },
      { id: 3, nome: 'Saúde', tipo: 'DESPESA', total: '20.00', quantidade: 1, metaMensal: '100.00' },
      { id: 4, nome: 'Transporte', tipo: 'DESPESA', total: '50.00', quantidade: 1, metaMensal: null },
    ]
    mockApi({ ...dashboardRoutes(), ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: { ...emptyResumo(currentMonth()), totalDespesas: '1140.00', categorias: resumo } } })
    renderApp('/')

    const card = within(await screen.findByRole('region', { name: 'Metas do mês' }))
    const items = card.getAllByRole('listitem')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Mercado'), expect.stringContaining('Lazer'), expect.stringContaining('Saúde'),
    ])
    expect(items[0]).toHaveTextContent('Passou R$ 100,00 da meta')
    expect(items[1]).toHaveTextContent('Restam R$ 30,00 · perto do limite')
    expect(items[2]).toHaveTextContent('Restam R$ 80,00')
    expect(card.getByRole('img', { name: '112,5% da meta' })).toBeInTheDocument()
  })

  it('invites to create a goal when there is none', async () => {
    mockApi(dashboardRoutes())
    renderApp('/')

    const card = within(await screen.findByRole('region', { name: 'Metas do mês' }))
    expect(card.getByRole('link', { name: 'Criar meta' })).toHaveAttribute('href', '/categorias')
  })
})

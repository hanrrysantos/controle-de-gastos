import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Categoria } from '../api'
import { currentMonth, emptyResumo, mockApi, renderApp } from './utils'

const categorias: Categoria[] = [
  { id: 3, nome: 'Mercado', tipo: 'DESPESA', quantidadeLancamentos: 2 },
  { id: 4, nome: 'Lazer', tipo: 'DESPESA', quantidadeLancamentos: 0 },
  { id: 5, nome: 'Transporte', tipo: 'DESPESA', quantidadeLancamentos: 0 },
  { id: 6, nome: 'Salário', tipo: 'RECEITA', quantidadeLancamentos: 0 },
]
const resumoRoute = { ['GET /transacoes/resumo?mes=' + currentMonth()]: { body: emptyResumo(currentMonth()) } }

describe('category form', () => {
  it('locks the other tipo when the category already has transactions', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'PUT /categorias/3': { body: {} },
      ...resumoRoute,
    })
    const { user } = renderApp('/categorias/3/editar')

    const nome = await screen.findByLabelText('Nome da categoria')
    expect(nome).toHaveValue('Mercado')
    expect(screen.getByRole('radio', { name: /Despesa/ })).toBeChecked()
    expect(screen.getByRole('radio', { name: /Receita/ })).toBeDisabled()
    expect(screen.getByText('O tipo não pode ser alterado porque a categoria já tem 2 lançamentos.')).toBeInTheDocument()

    await user.clear(nome)
    await user.type(nome, 'Supermercado')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'PUT')).toMatchObject({ path: '/categorias/3', body: { nome: 'Supermercado', tipo: 'DESPESA' } })
  })

  it('allows changing the tipo of an unused category', async () => {
    mockApi({ 'GET /categorias': { body: categorias } })
    renderApp('/categorias/4/editar')

    expect(await screen.findByRole('radio', { name: /Receita/ })).toBeEnabled()
    expect(screen.getByRole('radio', { name: /Despesa/ })).toBeEnabled()
  })

  it('requires a destination before deleting a category with transactions', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'DELETE /categorias/3?moverPara=5': { status: 204 },
      ...resumoRoute,
    })
    const { user } = renderApp('/categorias/3/editar')

    await user.click(await screen.findByRole('button', { name: 'Excluir categoria' }))
    const dialog = screen.getByRole('dialog', { name: 'Excluir categoria?' })
    expect(within(dialog).getByText('2 lançamentos serão movidos para:')).toBeInTheDocument()

    const destinations = within(dialog).getByRole('radiogroup', { name: 'Categoria de destino' })
    expect(within(destinations).getAllByRole('radio').map((radio) => radio.closest('label')?.textContent)).toEqual(['Lazer', 'Transporte'])
    const confirm = within(dialog).getByRole('button', { name: 'Excluir' })
    expect(confirm).toBeDisabled()

    await user.click(within(destinations).getByRole('radio', { name: 'Transporte' }))
    expect(confirm).toBeEnabled()
    await user.click(confirm)

    expect(await screen.findByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'DELETE')?.path).toBe('/categorias/3?moverPara=5')
  })

  it('deletes an unused category without moverPara', async () => {
    const { calls } = mockApi({
      'GET /categorias': { body: categorias },
      'DELETE /categorias/4': { status: 204 },
      ...resumoRoute,
    })
    const { user } = renderApp('/categorias/4/editar')

    await user.click(await screen.findByRole('button', { name: 'Excluir categoria' }))
    const dialog = screen.getByRole('dialog', { name: 'Excluir categoria?' })
    expect(within(dialog).getByText('Lazer será removida. Ela não tem lançamentos.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'DELETE')?.path).toBe('/categorias/4')
  })

  it('shows the API error inside the delete dialog', async () => {
    mockApi({
      'GET /categorias': { body: categorias },
      'DELETE /categorias/4': { status: 409, body: { message: 'Categoria em uso.' } },
    })
    const { user } = renderApp('/categorias/4/editar')

    await user.click(await screen.findByRole('button', { name: 'Excluir categoria' }))
    const dialog = screen.getByRole('dialog', { name: 'Excluir categoria?' })
    await user.click(within(dialog).getByRole('button', { name: 'Excluir' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Categoria em uso.')
    expect(within(dialog).getByRole('button', { name: 'Excluir' })).toBeEnabled()
  })
})

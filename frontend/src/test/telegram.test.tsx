import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dashboardRoutes, mockApi, renderApp } from './utils'

describe('telegram connect', () => {
  it('generates a link and shows the "Abrir no Telegram" button', async () => {
    const expiraEm = new Date(Date.now() + 10 * 60_000).toISOString()
    const { calls } = mockApi({
      ...dashboardRoutes(),
      'POST /integracoes/telegram/vinculo': { body: { codigo: 'ABC123', expiraEm, link: 'https://t.me/cdg_bot?start=ABC123' } },
    })
    const { user } = renderApp('/')

    const trigger = await screen.findByRole('button', { name: 'Conectar Telegram' })
    await screen.findByText('Telegram')
    await user.click(trigger)

    const dialog = screen.getByRole('dialog', { name: 'Lance pelo Telegram' })
    await user.click(within(dialog).getByRole('button', { name: 'Conectar Telegram' }))

    expect(await within(dialog).findByRole('heading', { name: 'Confirme no Telegram' })).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: 'Abrir no Telegram' })).toHaveAttribute('href', 'https://t.me/cdg_bot?start=ABC123')
    expect(within(dialog).getByText('/start ABC123')).toBeInTheDocument()
    expect(calls.find((call) => call.method === 'POST')).toMatchObject({
      path: '/integracoes/telegram/vinculo', headers: { Authorization: 'Bearer tok-123' },
    })
  })

  it('falls back to the command when the API returns no link', async () => {
    const expiraEm = new Date(Date.now() + 10 * 60_000).toISOString()
    mockApi({
      ...dashboardRoutes(),
      'POST /integracoes/telegram/vinculo': { body: { codigo: 'XYZ789', expiraEm, link: null } },
    })
    const { user } = renderApp('/')

    await screen.findByText('Telegram')
    await user.click(screen.getByRole('button', { name: 'Conectar Telegram' }))
    const dialog = screen.getByRole('dialog', { name: 'Lance pelo Telegram' })
    await user.click(within(dialog).getByRole('button', { name: 'Conectar Telegram' }))

    expect(await within(dialog).findByText('/start XYZ789')).toBeInTheDocument()
    expect(within(dialog).queryByRole('link', { name: 'Abrir no Telegram' })).not.toBeInTheDocument()
  })

  it('shows the connected state when already linked', async () => {
    mockApi({ ...dashboardRoutes(), 'GET /integracoes/telegram/vinculo': { body: { vinculado: true } } })
    renderApp('/')

    expect(await screen.findByRole('button', { name: 'Telegram conectado' })).toBeInTheDocument()
  })
})

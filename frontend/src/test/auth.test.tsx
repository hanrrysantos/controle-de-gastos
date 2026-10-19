import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dashboardRoutes, mockApi, renderApp } from './utils'

describe('login', () => {
  it('stores the session and lands on the dashboard', async () => {
    const { calls } = mockApi({
      'POST /auth/login': { body: { token: 'novo-token', nome: 'Bruno Lima' } },
      ...dashboardRoutes(),
    })
    const { user } = renderApp('/entrar', { loggedIn: false })

    await user.type(screen.getByLabelText('E-mail'), 'bruno@exemplo.com')
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'segredo1')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeInTheDocument()
    expect(screen.getByText('Olá, Bruno')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
    expect(JSON.parse(sessionStorage.getItem('cdg-session') ?? 'null')).toEqual({ token: 'novo-token', nome: 'Bruno Lima' })
    expect(calls[0]).toMatchObject({ method: 'POST', path: '/auth/login', body: { email: 'bruno@exemplo.com', senha: 'segredo1' } })
    const authorized = calls.filter((call) => call.path !== '/auth/login')
    expect(authorized.length).toBeGreaterThan(0)
    for (const call of authorized) expect(call.headers.Authorization).toBe('Bearer novo-token')
  })

  it('shows the API error message and stays on the login page', async () => {
    mockApi({ 'POST /auth/login': { status: 401, body: { message: 'E-mail ou senha inválidos.' } } })
    const { user } = renderApp('/entrar', { loggedIn: false })

    await user.type(screen.getByLabelText('E-mail'), 'bruno@exemplo.com')
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'errada')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    expect(sessionStorage.getItem('cdg-session')).toBeNull()
    expect(window.location.pathname).toBe('/entrar')
  })

  it('redirects protected routes to the login page without a session', () => {
    mockApi({})
    renderApp('/novo', { loggedIn: false })

    expect(screen.getByRole('heading', { name: 'Entre na sua conta' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/entrar')
  })
})

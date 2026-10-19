import { useCallback, useEffect, useState } from 'react'
import { BrowserRouter, Navigate, NavLink, Outlet, Route, Routes } from 'react-router'
import { House, Plus, ReceiptText, Shapes } from 'lucide-react'
import { api, type Sessao } from './api'
import { AuthPage, CategoriesPage, CategoryFormPage, DashboardPage, TransactionFormPage, TransactionsPage } from './screens'
import './App.css'

type TelegramWebApp = { initData: string; ready(): void; expand(): void }
const telegram = (window as Window & { Telegram?: { WebApp?: TelegramWebApp } }).Telegram?.WebApp

export type ScreenContext = {
  session: Sessao
  onUnauthorized: () => void
  notify: (message: string) => void
}

function Protected({ context }: { context: ScreenContext | null }) {
  if (!context) return <Navigate to="/entrar" replace />

  return (
    <div className="app-shell">
      <main className="app-content"><Outlet context={context} /></main>
      <nav className="tab-bar" aria-label="Navegação principal">
        <NavLink to="/" end className={({ isActive }) => isActive ? 'tab active' : 'tab'}>
          <House size={21} aria-hidden="true" /><span>Início</span>
        </NavLink>
        <NavLink to="/extrato" className={({ isActive }) => isActive ? 'tab active' : 'tab'}>
          <ReceiptText size={21} aria-hidden="true" /><span>Extrato</span>
        </NavLink>
        <NavLink to="/novo" className="tab add-tab" aria-label="Novo lançamento">
          <span className="add-circle"><Plus size={24} aria-hidden="true" /></span>
        </NavLink>
        <NavLink to="/categorias" className={({ isActive }) => isActive ? 'tab active' : 'tab'}>
          <Shapes size={21} aria-hidden="true" /><span>Categorias</span>
        </NavLink>
      </nav>
    </div>
  )
}

function App() {
  const [session, setSession] = useState<Sessao | null>(() => {
    try {
      const saved = sessionStorage.getItem('cdg-session')
      return saved ? JSON.parse(saved) as Sessao : null
    } catch {
      return null
    }
  })
  const [toast, setToast] = useState('')
  const [telegramLoading, setTelegramLoading] = useState(Boolean(telegram?.initData))
  const notify = useCallback((message: string) => setToast(message), [])
  const logout = useCallback(() => {
    sessionStorage.removeItem('cdg-session')
    setSession(null)
  }, [])
  const login = useCallback((data: Sessao) => {
    sessionStorage.setItem('cdg-session', JSON.stringify(data))
    setSession(data)
  }, [])

  useEffect(() => {
    if (!telegram?.initData) return
    telegram.ready()
    telegram.expand()
    api<Sessao>('/auth/telegram/webapp', {
      method: 'POST', body: JSON.stringify({ initData: telegram.initData }),
    }).then(login).catch(() => {
      logout()
      notify('Não foi possível validar o Telegram. Abra o painel novamente pelo bot.')
    }).finally(() => setTelegramLoading(false))
  }, [login, logout, notify])

  useEffect(() => {
    if (!toast) return
    const timeout = window.setTimeout(() => setToast(''), 2200)
    return () => window.clearTimeout(timeout)
  }, [toast])

  const context = session ? { session, onUnauthorized: logout, notify } : null

  return (
    <BrowserRouter>
      {telegramLoading ? <div className="app-shell"><main className="page-content loading">Validando acesso pelo Telegram...</main></div> : <Routes>
        <Route path="/entrar" element={session ? <Navigate to="/" replace /> : <AuthPage mode="login" onLogin={login} notify={notify} />} />
        <Route path="/cadastro" element={session ? <Navigate to="/" replace /> : <AuthPage mode="register" onLogin={login} notify={notify} />} />
        <Route element={<Protected context={context} />}>
          <Route path="/" element={<DashboardPage onLogout={logout} />} />
          <Route path="/dashboard" element={<Navigate to="/" replace />} />
          <Route path="/extrato" element={<TransactionsPage />} />
          <Route path="/novo" element={<TransactionFormPage />} />
          <Route path="/lancamentos/:id/editar" element={<TransactionFormPage />} />
          <Route path="/categorias" element={<CategoriesPage />} />
          <Route path="/categorias/nova" element={<CategoryFormPage />} />
          <Route path="/categorias/:id/editar" element={<CategoryFormPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>}
      {toast && <div className="toast" role="status">{toast}</div>}
    </BrowserRouter>
  )
}

export default App

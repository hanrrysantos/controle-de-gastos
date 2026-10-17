import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router'
import {
  ArrowDownLeft, ArrowRight, ArrowUpRight, BriefcaseBusiness,
  Car, Check, ChevronDown, ChevronLeft, ChevronRight, CircleCheck, Eye, EyeOff, Info,
  LogOut, Mail, MessageCircle, Plus, ShoppingBasket, Tag, Ticket, Trash2, UserRound,
} from 'lucide-react'
import { api, ApiError, type Categoria, type Resumo, type Sessao, type Tipo, type Transacao } from './api'
import type { ScreenContext } from './App'

const money = (value: string) => {
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = (negative ? value.slice(1) : value).split('.')
  return (negative ? '−' : '') + 'R$ ' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + fraction.padEnd(2, '0').slice(0, 2)
}
const cents = (value: string) => {
  const [whole, fraction = ''] = value.split('.')
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
}
const decimalFromCents = (value: bigint) => (value / 100n).toString() + '.' + (value % 100n).toString().padStart(2, '0')
const moneyFromCents = (value: bigint) => money(decimalFromCents(value))
const errorMessage = (error: unknown) => error instanceof TypeError ? 'Não foi possível conectar ao servidor.' : error instanceof Error ? error.message : 'Não foi possível concluir a operação.'
const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('pt-BR', { day: 'numeric', month: 'short' })
const today = () => {
  const date = new Date()
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}
const recentFirst = (items: Transacao[]) => [...items].sort((a, b) => b.data.localeCompare(a.data) || b.id - a.id)

function useScreen() {
  return useOutletContext<ScreenContext>()
}

function requestError(error: unknown, onUnauthorized: () => void, setError: (message: string) => void) {
  if (error instanceof ApiError && error.status === 401) onUnauthorized()
  else setError(errorMessage(error))
}

function ErrorState({ message }: { message: string }) {
  return <div className="state-panel" role="alert"><p>{message}</p><button className="text-button" onClick={() => window.location.reload()}>Tentar novamente</button></div>
}

function categoryIcon(name: string) {
  if (/alimenta|mercado|comida/i.test(name)) return <ShoppingBasket size={19} aria-hidden="true" />
  if (/trabalho|sal.rio|freelance/i.test(name)) return <BriefcaseBusiness size={19} aria-hidden="true" />
  if (/transporte|uber/i.test(name)) return <Car size={19} aria-hidden="true" />
  if (/lazer|cinema/i.test(name)) return <Ticket size={19} aria-hidden="true" />
  return <Tag size={19} aria-hidden="true" />
}

function TransactionRow({ item, onDelete }: { item: Transacao; onDelete?: (item: Transacao) => void }) {
  return (
    <div className="transaction-row">
      <span className={'transaction-icon ' + (item.tipo === 'RECEITA' ? 'income' : 'expense')}>{categoryIcon(item.nomeCategoria)}</span>
      <div className="transaction-copy">
        <strong>{item.descricao}</strong>
        <span>{item.nomeCategoria} · {dateLabel(item.data)}</span>
      </div>
      <strong className={'transaction-value ' + (item.tipo === 'RECEITA' ? 'income-text' : '')}>
        {item.tipo === 'RECEITA' ? '+' : '−'} {money(item.valor)}
      </strong>
      {onDelete && <button className="icon-button delete-button" type="button" onClick={() => onDelete(item)} aria-label={'Excluir ' + item.descricao}><Trash2 size={17} /></button>}
    </div>
  )
}

function CashFlowChart({ transactions }: { transactions: Transacao[] }) {
  const anchorDate = new Date()
  anchorDate.setHours(12, 0, 0, 0)
  const [periodOffset, setPeriodOffset] = useState(0)
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(anchorDate)
    date.setDate(anchorDate.getDate() + periodOffset * 7 - 6 + index)
    return {
      key: [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-'),
      weekday: date.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', ''),
      label: String(date.getDate()),
    }
  })
  const [selected, setSelected] = useState(6)
  const periodStart = new Date(days[0].key + 'T12:00:00')
  const periodEnd = new Date(days[6].key + 'T12:00:00')
  const month = (date: Date) => date.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
  const periodLabel = periodStart.getMonth() === periodEnd.getMonth() && periodStart.getFullYear() === periodEnd.getFullYear()
    ? `${periodStart.getDate()}–${periodEnd.getDate()} ${month(periodEnd)}`
    : `${periodStart.getDate()} ${month(periodStart)} – ${periodEnd.getDate()} ${month(periodEnd)}`
  const periodYear = periodStart.getFullYear() === periodEnd.getFullYear()
    ? String(periodEnd.getFullYear())
    : `${periodStart.getFullYear()}–${periodEnd.getFullYear()}`
  const hasEarlier = transactions.some((item) => item.data < days[0].key)
  const daily = days.map(({ key }) => {
    const entries = transactions.filter((item) => item.data === key)
    return {
      income: entries.filter((item) => item.tipo === 'RECEITA').reduce((sum, item) => sum + cents(item.valor), 0n),
      expense: entries.filter((item) => item.tipo === 'DESPESA').reduce((sum, item) => sum + cents(item.valor), 0n),
    }
  })
  const max = daily.flatMap((day) => [day.income, day.expense]).reduce((current, amount) => amount > current ? amount : current, 0n)
  const x = (index: number) => 24 + index * 272 / 6
  const y = (amount: bigint) => max === 0n ? 130 : 130 - Number(amount * 11000n / max) / 100
  const points = (type: 'income' | 'expense') => daily.map((day, index) => x(index) + ',' + y(day[type])).join(' ')
  const balance = daily[selected].income - daily[selected].expense
  const selectedDate = days[selected].weekday.charAt(0).toUpperCase() + days[selected].weekday.slice(1) + ', ' + dateLabel(days[selected].key)
  const balanceLabel = (balance > 0n ? '+' : balance < 0n ? '−' : '') + moneyFromCents(balance < 0n ? -balance : balance)

  function selectDay(event: ReactPointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect()
    const position = (event.clientX - bounds.left) * 320 / bounds.width
    setSelected(Math.max(0, Math.min(6, Math.round((position - 24) * 6 / 272))))
  }

  function moveDay(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      setSelected((current) => event.key === 'Home' ? 0 : event.key === 'End' ? 6 : Math.max(0, Math.min(6, current + (event.key === 'ArrowLeft' ? -1 : 1))))
    }
  }

  function showPeriod(offset: number) {
    setPeriodOffset(offset)
    setSelected(6)
  }

  return (
    <section className="flow-card" aria-labelledby="flow-title">
      <div className="section-heading"><h2 id="flow-title">Entradas e saídas</h2>{periodOffset === 0 ? <span>7 dias</span> : <button className="flow-today" type="button" onClick={() => showPeriod(0)}>Hoje</button>}</div>
      <div className="flow-period" role="group" aria-label="Período do gráfico">
        <button type="button" onClick={() => showPeriod(periodOffset - 1)} disabled={!hasEarlier} aria-label="Sete dias anteriores"><ChevronLeft size={20} /></button>
        <div><strong>{periodLabel}</strong><span>{periodYear}</span></div>
        <button type="button" onClick={() => showPeriod(periodOffset + 1)} disabled={periodOffset === 0} aria-label="Próximos sete dias"><ChevronRight size={20} /></button>
      </div>
      <div className="flow-readout">
        <div className="flow-readout-top"><strong>{selectedDate}</strong><span className={'flow-balance ' + (balance < 0n ? 'negative' : balance > 0n ? 'positive' : '')}>Saldo do dia {balanceLabel}</span></div>
        <div className="flow-values">
          <div className="flow-income"><span>Entrou</span><strong>{moneyFromCents(daily[selected].income)}</strong></div>
          <div className="flow-expense"><span>Saiu</span><strong>{moneyFromCents(daily[selected].expense)}</strong></div>
        </div>
      </div>
      <div className="flow-control" role="slider" tabIndex={0} aria-label="Dia do gráfico" aria-valuemin={0} aria-valuemax={6} aria-valuenow={selected} aria-valuetext={selectedDate + ', entrou ' + moneyFromCents(daily[selected].income) + ', saiu ' + moneyFromCents(daily[selected].expense) + ', saldo ' + balanceLabel} onKeyDown={moveDay}>
        <svg viewBox="0 0 320 150" aria-hidden="true" onPointerDown={(event) => { event.currentTarget.setPointerCapture(event.pointerId); selectDay(event) }} onPointerMove={selectDay}>
          <defs>
            <linearGradient id="flow-income-fill" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#0c8d83" stopOpacity=".16" /><stop offset="1" stopColor="#0c8d83" stopOpacity="0" /></linearGradient>
            <linearGradient id="flow-expense-fill" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#e56c77" stopOpacity=".13" /><stop offset="1" stopColor="#e56c77" stopOpacity="0" /></linearGradient>
          </defs>
          <rect x={x(selected) - 19} y="9" width="38" height="130" rx="12" className="flow-highlight" />
          <line x1="24" x2="296" y1="20" y2="20" className="flow-grid" />
          <line x1="24" x2="296" y1="75" y2="75" className="flow-grid" />
          <line x1="24" x2="296" y1="130" y2="130" className="flow-grid" />
          <polygon points={`24,130 ${points('income')} 296,130`} fill="url(#flow-income-fill)" />
          <polygon points={`24,130 ${points('expense')} 296,130`} fill="url(#flow-expense-fill)" />
          <polyline points={points('income')} className="flow-line income" />
          <polyline points={points('expense')} className="flow-line expense" />
          {daily.map((day, index) => <g key={days[index].key}>
            <circle cx={x(index)} cy={y(day.income)} r="3" className="flow-dot income" />
            <circle cx={x(index)} cy={y(day.expense)} r="3" className="flow-dot expense" />
          </g>)}
          <circle cx={x(selected)} cy={y(daily[selected].income)} r="6" className="flow-point income" />
          <circle cx={x(selected)} cy={y(daily[selected].expense)} r="6" className="flow-point expense" />
          <rect width="320" height="150" fill="transparent" />
        </svg>
      </div>
      <div className="flow-axis">{days.map((day, index) => <button type="button" key={day.key} className={index === selected ? 'selected' : ''} onClick={() => setSelected(index)} aria-label={day.weekday + ', ' + dateLabel(day.key)} aria-pressed={index === selected}><span>{day.weekday}</span><strong>{day.label}</strong></button>)}</div>
      <div className="flow-key"><span><i className="income" /> Entradas</span><span><i className="expense" /> Saídas</span><small>Toque ou arraste para explorar os dias</small></div>
    </section>
  )
}

function SpendingDonut({ transactions }: { transactions: Transacao[] }) {
  const byCategory = new Map<string, bigint>()
  for (const item of transactions) {
    if (item.tipo === 'DESPESA') byCategory.set(item.nomeCategoria, (byCategory.get(item.nomeCategoria) ?? 0n) + cents(item.valor))
  }
  const categories = [...byCategory].sort((a, b) => a[1] === b[1] ? 0 : a[1] > b[1] ? -1 : 1)
  const totalCents = categories.reduce((sum, [, amount]) => sum + amount, 0n)
  const colors = ['#7447e8', '#0c8d83', '#e56c77', '#e5aa4d', '#6881ce', '#9b6bb7']
  const color = (index: number) => colors[index] ?? `hsl(${index * 137 % 360} 60% 55%)`
  const slices = categories.map(([, amount], index) => {
    const before = categories.slice(0, index).reduce((sum, [, value]) => sum + value, 0n)
    const start = Number(before * 10000n / totalCents) / 100
    const end = index === categories.length - 1 ? 100 : Number((before + amount) * 10000n / totalCents) / 100
    return `${color(index)} ${start}% ${end}%`
  }).join(', ')

  return (
    <section className="spending-card" aria-labelledby="spending-title">
      <h2 id="spending-title">Gastos por categoria</h2>
      {totalCents === 0n ? <p className="spending-empty">Seus gastos aparecerão aqui após o primeiro lançamento.</p> : <>
        <div className="spending-donut" style={{ background: `conic-gradient(from -90deg, ${slices})` }} aria-hidden="true">
          <div className="spending-donut-center"><span>Total gasto</span><strong>{moneyFromCents(totalCents)}</strong></div>
        </div>
        <ul className="spending-legend">
          {categories.map(([name, amount], index) => <li key={name}>
            <span className="legend-dot" style={{ background: color(index) }} aria-hidden="true" />
            <div className="legend-copy"><strong>{name}</strong><span>{moneyFromCents(amount)}</span></div>
            <span className="legend-percent">{(Number((amount * 1000n + totalCents / 2n) / totalCents) / 10).toLocaleString('pt-BR')}%</span>
          </li>)}
        </ul>
      </>}
    </section>
  )
}

function TelegramLinkCard() {
  const { session, onUnauthorized, notify } = useScreen()
  const [linked, setLinked] = useState<boolean | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api<{ vinculado: boolean }>('/integracoes/telegram/vinculo', {}, session.token)
      .then((result) => { if (active) setLinked(result.vinculado) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  async function generateCode() {
    setBusy(true)
    setError('')
    try {
      const result = await api<{ codigo: string }>('/integracoes/telegram/vinculo', { method: 'POST' }, session.token)
      setCode(result.codigo)
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  async function checkLink() {
    setBusy(true)
    setError('')
    try {
      const result = await api<{ vinculado: boolean }>('/integracoes/telegram/vinculo', {}, session.token)
      setLinked(result.vinculado)
      if (result.vinculado) setCode('')
      else notify('Ainda não recebemos a confirmação do bot.')
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  async function copyCommand() {
    try {
      await navigator.clipboard.writeText('/start ' + code)
      notify('Comando copiado.')
    } catch {
      notify('Selecione e copie o comando exibido.')
    }
  }

  return <section className="telegram-card" aria-label="Conexão com Telegram">
    <div className="telegram-card-heading"><span><MessageCircle size={19} aria-hidden="true" /></span><div><strong>{linked ? 'Telegram conectado' : 'Anote pelo Telegram'}</strong><p>{linked ? 'Envie entradas e despesas pelo chat do bot. Elas aparecerão aqui.' : 'Conecte seu chat para registrar lançamentos por mensagem.'}</p></div></div>
    {linked === false && (code ? <div className="telegram-steps">
      <p>No chat privado com o bot, envie este comando em até 10 minutos:</p>
      <code>/start {code}</code>
      <div><button type="button" onClick={copyCommand}>Copiar comando</button><button type="button" onClick={checkLink} disabled={busy}>Verificar conexão</button></div>
      <button className="telegram-new-code" type="button" onClick={generateCode} disabled={busy}>Gerar outro código</button>
    </div> : <button className="telegram-connect" type="button" onClick={generateCode} disabled={busy}>{busy ? 'Aguarde...' : 'Conectar Telegram'}</button>)}
    {error && <p className="form-error" role="alert">{error}</p>}
  </section>
}

export function DashboardPage({ onLogout }: { onLogout: () => void }) {
  const { session, onUnauthorized } = useScreen()
  const [data, setData] = useState<{ resumo: Resumo; transacoes: Transacao[] } | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([
      api<Resumo>('/transacoes/dashboard', {}, session.token),
      api<Transacao[]>('/transacoes', {}, session.token),
    ]).then(([resumo, transacoes]) => {
      if (active) setData({ resumo, transacoes: recentFirst(transacoes) })
    }).catch((err: unknown) => {
      if (active) requestError(err, onUnauthorized, setError)
    })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  return (
    <>
      <header className="dashboard-hero">
        <div className="hero-top"><span className="brand">cdg.</span><span className="period">Acumulado</span><button className="hero-logout" type="button" onClick={onLogout} aria-label="Sair"><LogOut size={19} /></button></div>
        <p className="hero-greeting">Olá, {session.nome?.split(' ')[0] || 'você'}</p>
        <p className="hero-label">Seu saldo total</p>
        <h1>{data ? money(data.resumo.saldo) : '—'}</h1>
      </header>
      <div className="page-content dashboard-content">
        {error ? <ErrorState message={error} /> : !data ? <p className="loading">Carregando suas finanças...</p> : (
          <>
            <div className="metric-strip">
              <div className="metric"><div className="metric-label"><span className="metric-icon income"><ArrowDownLeft size={15} /></span>Entradas</div><strong>{money(data.resumo.totalReceitas)}</strong></div>
              <div className="metric"><div className="metric-label"><span className="metric-icon expense"><ArrowUpRight size={15} /></span>Saídas</div><strong>{money(data.resumo.totalDespesas)}</strong></div>
            </div>
            <TelegramLinkCard />
            <CashFlowChart transactions={data.transacoes} />
            <section className="recent-section">
              <div className="section-heading"><h2>Últimos lançamentos</h2><Link to="/extrato">Ver todos <ArrowRight size={14} /></Link></div>
              {data.transacoes.length
                ? data.transacoes.slice(0, 3).map((item) => <TransactionRow key={item.id} item={item} />)
                : <div className="empty-state"><p>Nenhum lançamento ainda.</p><Link to="/novo">Registrar o primeiro</Link></div>}
            </section>
          </>
        )}
      </div>
    </>
  )
}

export function AuthPage({ mode, onLogin, notify }: { mode: 'login' | 'register'; onLogin: (session: Sessao) => void; notify: (message: string) => void }) {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const isLogin = mode === 'login'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (isLogin) {
        const session = await api<Sessao>('/auth/login', { method: 'POST', body: JSON.stringify({ email, senha: password }) })
        onLogin(session)
        navigate('/', { replace: true })
      } else {
        await api('/auth/register', { method: 'POST', body: JSON.stringify({ nome: name.trim(), email, senha: password }) })
        notify('Conta criada. Entre para continuar.')
        navigate('/entrar', { replace: true })
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell auth-shell">
      <header className="auth-hero"><span className="brand">cdg.</span><h1>{isLogin ? 'Suas finanças,\nmais claras.' : 'Comece a cuidar\ndo seu dinheiro.'}</h1></header>
      <main className="page-content auth-content">
        <h2>{isLogin ? 'Entre na sua conta' : 'Crie sua conta'}</h2>
        <p>{isLogin ? 'Acompanhe seus lançamentos.' : 'Organize receitas e despesas em um só lugar.'}</p>
        <form onSubmit={submit} className="form-stack">
          {!isLogin && <label className="field"><span>Nome</span><div><input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" placeholder="Digite seu nome" required /><UserRound size={18} /></div></label>}
          <label className="field"><span>E-mail</span><div><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="seu@email.com" required /><Mail size={18} /></div></label>
          <label className="field"><span>Senha</span><div><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={isLogin ? 'current-password' : 'new-password'} placeholder="Sua senha" required /><button className="field-icon-button" type="button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></div></label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={busy}>{busy ? 'Aguarde...' : isLogin ? 'Entrar' : 'Criar conta'} <ArrowRight size={18} /></button>
        </form>
        <p className="auth-switch">{isLogin ? 'Ainda não tem conta?' : 'Já tem conta?'} <Link to={isLogin ? '/cadastro' : '/entrar'}>{isLogin ? 'Cadastre-se' : 'Entrar'}</Link></p>
      </main>
    </div>
  )
}

function ScreenTop({ context, title }: { context: string; title: string }) {
  return <header className="screen-top"><div><span className="brand">cdg.</span><span className="eyebrow">{context}</span></div><h1>{title}</h1></header>
}

export function TransactionsPage() {
  const { session, onUnauthorized, notify } = useScreen()
  const [items, setItems] = useState<Transacao[] | null>(null)
  const [filter, setFilter] = useState<'TODAS' | Tipo>('TODAS')
  const [selected, setSelected] = useState<Transacao | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [deleteError, setDeleteError] = useState('')
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    let active = true
    api<Transacao[]>('/transacoes', {}, session.token)
      .then((result) => { if (active) setItems(recentFirst(result)) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  useEffect(() => {
    if (selected && !dialog.current?.open) dialog.current?.showModal()
    if (!selected && dialog.current?.open) dialog.current?.close()
  }, [selected])

  async function remove() {
    if (!selected) return
    setBusy(true)
    setDeleteError('')
    try {
      await api<void>('/transacoes/' + selected.id, { method: 'DELETE' }, session.token)
      setItems((current) => current?.filter((item) => item.id !== selected.id) ?? null)
      setSelected(null)
      notify('Lançamento excluído.')
    } catch (err) {
      requestError(err, onUnauthorized, setDeleteError)
    } finally {
      setBusy(false)
    }
  }

  const visible = items?.filter((item) => filter === 'TODAS' || item.tipo === filter) ?? []

  return (
    <div className="page-content">
      <ScreenTop context="EXTRATO" title="Movimentações" />
      {items && <SpendingDonut transactions={items} />}
      <div className="segmented extrato-filter" role="group" aria-label="Filtrar lançamentos">
        {([['TODAS', 'Todas'], ['RECEITA', 'Entradas'], ['DESPESA', 'Saídas']] as const).map(([value, label]) =>
          <button key={value} type="button" className={filter === value ? 'selected' : ''} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</button>)}
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      {!items ? !error && <p className="loading">Carregando lançamentos...</p> : <>
        <section className="transaction-list">
          <div className="section-heading"><h2>Lançamentos</h2><span>{visible.length} {visible.length === 1 ? 'item' : 'itens'}</span></div>
          {visible.length ? visible.map((item) => <TransactionRow key={item.id} item={item} onDelete={(entry) => { setDeleteError(''); setSelected(entry) }} />)
            : <div className="empty-state"><p>Nenhum lançamento nesta seleção.</p><Link to="/novo">Registrar lançamento</Link></div>}
        </section>
      </>}
      <dialog ref={dialog} className="confirm-dialog" onClose={() => setSelected(null)} aria-labelledby="delete-title">
        <span className="dialog-icon"><Trash2 size={23} /></span>
        <h2 id="delete-title">Excluir lançamento?</h2>
        <p>{selected?.descricao} · {selected && money(selected.valor)} será removido do extrato.</p>
        {deleteError && <p className="form-error" role="alert">{deleteError}</p>}
        <div className="dialog-actions">
          <button type="button" onClick={() => setSelected(null)} disabled={busy}>Cancelar</button>
          <button type="button" className="danger-button" onClick={remove} disabled={busy}>{busy ? 'Excluindo...' : 'Excluir'}</button>
        </div>
      </dialog>
    </div>
  )
}

export function TransactionFormPage() {
  const { session, onUnauthorized, notify } = useScreen()
  const navigate = useNavigate()
  const [categories, setCategories] = useState<Categoria[] | null>(null)
  const [type, setType] = useState<Tipo>('DESPESA')
  const [amountDigits, setAmountDigits] = useState('')
  const amountInput = useRef<HTMLInputElement>(null)
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [date, setDate] = useState(today)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useLayoutEffect(() => {
    const input = amountInput.current
    if (input && document.activeElement === input) input.setSelectionRange(input.value.length, input.value.length)
  }, [amountDigits])

  useEffect(() => {
    let active = true
    api<Categoria[]>('/categorias', {}, session.token)
      .then((result) => { if (active) setCategories(result) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (!amountDigits) {
      setError('Digite um valor maior que zero.')
      return
    }
    setBusy(true)
    try {
      await api('/transacoes', {
        method: 'POST',
        body: JSON.stringify({ valor: decimalFromCents(BigInt(amountDigits)), descricao: description.trim(), data: date, categoriaId: Number(categoryId) }),
      }, session.token)
      notify('Lançamento salvo.')
      navigate('/')
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  const available = categories?.filter((category) => category.tipo === type) ?? []

  return (
    <div className="page-content">
      <ScreenTop context="NOVO LANÇAMENTO" title="Registrar" />
      <div className="segmented transaction-types" role="group" aria-label="Tipo do lançamento">
        <button type="button" className={type === 'DESPESA' ? 'selected' : ''} aria-pressed={type === 'DESPESA'} onClick={() => { setType('DESPESA'); setCategoryId('') }}><ArrowUpRight size={17} /> Despesa</button>
        <button type="button" className={type === 'RECEITA' ? 'selected' : ''} aria-pressed={type === 'RECEITA'} onClick={() => { setType('RECEITA'); setCategoryId('') }}><ArrowDownLeft size={17} /> Receita</button>
      </div>
      <form className="form-stack" onSubmit={submit}>
        <label className="amount-card"><span>Valor da {type === 'DESPESA' ? 'despesa' : 'receita'}</span><input ref={amountInput} value={moneyFromCents(BigInt(amountDigits || '0'))} onChange={(event) => setAmountDigits(event.target.value.replace(/\D/g, '').replace(/^0+/, '').slice(0, 19))} onFocus={(event) => event.currentTarget.setSelectionRange(event.currentTarget.value.length, event.currentTarget.value.length)} onClick={(event) => event.currentTarget.setSelectionRange(event.currentTarget.value.length, event.currentTarget.value.length)} inputMode="numeric" aria-label="Valor em reais" aria-describedby="amount-help" /><small id="amount-help">Os dois últimos números são centavos</small></label>
        <label className="field"><span>Descrição</span><div><input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Ex.: Compras no mercado" maxLength={255} required /><Tag size={18} /></div></label>
        <label className="field"><span>Categoria</span><div><select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required><option value="">Selecione uma categoria</option>{available.map((category) => <option value={category.id} key={category.id}>{category.nome}</option>)}</select><ChevronDown size={18} /></div></label>
        {categories && !available.length && <Link className="inline-link" to="/categorias/nova">Crie uma categoria de {type === 'DESPESA' ? 'despesa' : 'receita'} para continuar.</Link>}
        <label className="field"><span>Data</span><div><input type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></div></label>
        <p className="form-note"><Info size={15} /> A categoria define se é receita ou despesa.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary-button" type="submit" disabled={busy || !categories || !available.length}>{busy ? 'Salvando...' : 'Salvar lançamento'} <ArrowRight size={18} /></button>
      </form>
    </div>
  )
}

export function CategoriesPage() {
  const { session, onUnauthorized } = useScreen()
  const [categories, setCategories] = useState<Categoria[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api<Categoria[]>('/categorias', {}, session.token)
      .then((result) => { if (active) setCategories(result) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  return (
    <div className="page-content">
      <ScreenTop context="ORGANIZAÇÃO" title="Categorias" />
      <p className="page-subtitle">Organize suas entradas e saídas.</p>
      <Link className="primary-button link-button" to="/categorias/nova"><Plus size={19} /> Nova categoria</Link>
      {error ? <ErrorState message={error} /> : !categories ? <p className="loading">Carregando categorias...</p> : (
        <>
          {(['DESPESA', 'RECEITA'] as const).map((type) => {
            const group = categories.filter((category) => category.tipo === type)
            return <section className="category-section" key={type}>
              <div className="section-heading"><h2>{type === 'DESPESA' ? 'Despesas' : 'Receitas'}</h2><span>{group.length} {group.length === 1 ? 'categoria' : 'categorias'}</span></div>
              {group.length ? <div className="category-list">{group.map((category) => <div className="category-row" key={category.id}><span className={'category-initial ' + (type === 'RECEITA' ? 'income' : '')}>{category.nome.charAt(0).toUpperCase()}</span><strong>{category.nome}</strong></div>)}</div>
                : <p className="empty-category">Nenhuma categoria de {type === 'DESPESA' ? 'despesa' : 'receita'}.</p>}
            </section>
          })}
        </>
      )}
    </div>
  )
}

export function CategoryFormPage() {
  const { session, onUnauthorized, notify } = useScreen()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [type, setType] = useState<Tipo>('DESPESA')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Digite o nome da categoria.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api('/categorias', { method: 'POST', body: JSON.stringify({ nome: name.trim(), tipo: type }) }, session.token)
      notify('Categoria criada.')
      navigate('/categorias')
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page-content">
      <Link className="back-link" to="/categorias">‹ Categorias</Link>
      <ScreenTop context="ORGANIZAÇÃO" title="Nova categoria" />
      <p className="page-subtitle">Dê um nome e escolha como usar.</p>
      <form className="form-stack" onSubmit={submit}>
        <label className="field"><span>Nome da categoria</span><div><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Saúde" maxLength={100} required /><Tag size={18} /></div></label>
        <fieldset className="category-choice"><legend>Tipo da categoria</legend>
          {(['DESPESA', 'RECEITA'] as const).map((value) => <label className={type === value ? 'choice selected' : 'choice'} key={value}>
            <input type="radio" name="tipo" checked={type === value} onChange={() => setType(value)} />
            <span className="choice-icon">{value === 'DESPESA' ? <ArrowUpRight size={20} /> : <ArrowDownLeft size={20} />}</span>
            <span><strong>{value === 'DESPESA' ? 'Despesa' : 'Receita'}</strong><small>{value === 'DESPESA' ? 'Para gastos e pagamentos' : 'Para dinheiro que entra'}</small></span>
            <CircleCheck className="choice-check" size={21} />
          </label>)}
        </fieldset>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary-button" type="submit" disabled={busy}><Check size={18} /> {busy ? 'Salvando...' : 'Salvar categoria'}</button>
      </form>
    </div>
  )
}

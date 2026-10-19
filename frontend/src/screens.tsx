import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type FormEvent, type MouseEvent as ReactMouseEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useOutletContext, useParams, useSearchParams } from 'react-router'
import {
  ArrowDownLeft, ArrowRight, ArrowUpRight, BriefcaseBusiness, Calendar, Car, Check, ChevronLeft, ChevronRight,
  CircleCheck, Copy, ExternalLink, Eye, EyeOff, Gamepad2, GraduationCap, HeartPulse, House, LogOut, Mail,
  PencilLine, PiggyBank, Plus, Send, Tag, Trash2, UserRound, Utensils, X, type LucideIcon,
} from 'lucide-react'
import { api, ApiError, type Categoria, type ResumoCategoria, type ResumoMensal, type Sessao, type Tipo, type Transacao, type VinculoTelegram } from './api'
import type { ScreenContext } from './App'

const money = (value: string) => {
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = (negative ? value.slice(1) : value).split('.')
  return (negative ? '−' : '') + 'R$ ' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + fraction.padEnd(2, '0').slice(0, 2)
}
const cents = (value: string) => {
  const negative = value.startsWith('-')
  const [whole, fraction = ''] = (negative ? value.slice(1) : value).split('.')
  const amount = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
  return negative ? -amount : amount
}
const decimalFromCents = (value: bigint) => (value < 0n ? '-' : '') + ((value < 0n ? -value : value) / 100n).toString() + '.' + ((value < 0n ? -value : value) % 100n).toString().padStart(2, '0')
const moneyFromCents = (value: bigint) => money(decimalFromCents(value))
const compactMoney = (value: bigint) => {
  const reais = Number(value) / 100
  return Math.abs(reais) >= 1000 ? 'R$ ' + (reais / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + 'k' : moneyFromCents(value)
}
const share = (part: bigint, total: bigint) => total === 0n ? 0 : Number(part * 1000n / total) / 10
const errorMessage = (error: unknown) => error instanceof TypeError ? 'Não foi possível conectar ao servidor.' : error instanceof Error ? error.message : 'Não foi possível concluir a operação.'
const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('pt-BR', { day: 'numeric', month: 'short' }).replace('.', '')
const isoDate = (date: Date) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
const today = () => isoDate(new Date())
const yesterday = () => {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return isoDate(date)
}
const currentMonth = () => today().slice(0, 7)
const shiftMonth = (month: string, offset: number) => {
  const [year, index] = month.split('-').map(Number)
  const date = new Date(year, index - 1 + offset, 1)
  return isoDate(date).slice(0, 7)
}
const monthName = (month: string, format: 'long' | 'short' = 'long') => new Date(month + '-01T12:00:00').toLocaleDateString('pt-BR', { month: format }).replace('.', '')
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)
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

const categoryStyles: [RegExp, LucideIcon, string][] = [
  [/alimenta|mercado|comida|restaurante|lanche/i, Utensils, '#ff6b1a'],
  [/morad|aluguel|casa|condom|conta/i, House, '#ffb020'],
  [/transporte|uber|combust|carro|ônibus|onibus/i, Car, '#3b82f6'],
  [/lazer|cinema|jogo|viagem|passeio/i, Gamepad2, '#8b5cf6'],
  [/sa[uú]de|farm[aá]cia|m[eé]dic/i, HeartPulse, '#e5484d'],
  [/sal[aá]rio|trabalho|freela|renda/i, BriefcaseBusiness, '#16a34a'],
  [/educa|curso|escola|faculdade/i, GraduationCap, '#0ea5e9'],
  [/invest|poupan|reserva/i, PiggyBank, '#14b8a6'],
]
const fallbackColors = ['#ff6b1a', '#3b82f6', '#8b5cf6', '#ffb020', '#14b8a6', '#ec4899']

function categoryStyle(name: string) {
  const match = categoryStyles.find(([pattern]) => pattern.test(name))
  if (match) return { Icon: match[1], color: match[2] }
  const hash = [...name].reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return { Icon: Tag, color: fallbackColors[hash % fallbackColors.length] }
}

function CategoryIcon({ name, size = 'md' }: { name: string; size?: 'md' | 'lg' }) {
  const { Icon, color } = categoryStyle(name)
  return <span className={'category-icon ' + size} style={{ '--category': color } as CSSProperties}><Icon size={size === 'lg' ? 20 : 19} aria-hidden="true" /></span>
}

function TransactionRow({ item, onDelete, showDate = true }: { item: Transacao; onDelete?: (item: Transacao) => void; showDate?: boolean }) {
  return (
    <div className="transaction-row">
      <Link className="transaction-link" to={'/lancamentos/' + item.id + '/editar'}>
        <CategoryIcon name={item.nomeCategoria} />
        <div className="transaction-copy">
          <strong>{item.descricao}</strong>
          <span>{item.nomeCategoria}{showDate && ' · ' + dateLabel(item.data)}</span>
        </div>
        <strong className={'transaction-value ' + (item.tipo === 'RECEITA' ? 'income-text' : '')}>
          {item.tipo === 'RECEITA' ? '+' : '−'} {money(item.valor)}
        </strong>
      </Link>
      {onDelete && <button className="icon-button delete-button" type="button" onClick={() => onDelete(item)} aria-label={'Excluir ' + item.descricao}><Trash2 size={17} /></button>}
    </div>
  )
}

function ScreenTop({ context, title, action }: { context: string; title: string; action?: ReactNode }) {
  return <header className="screen-top"><div><span className="eyebrow">{context}</span><h1>{title}</h1></div>{action}</header>
}

function useResumo(month: string) {
  const { session, onUnauthorized } = useScreen()
  const [state, setState] = useState<{ month: string; data: ResumoMensal | null; error: string }>({ month: '', data: null, error: '' })

  useEffect(() => {
    let active = true
    api<ResumoMensal>('/transacoes/resumo?mes=' + month, {}, session.token)
      .then((data) => { if (active) setState({ month, data, error: '' }) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, (error) => setState({ month, data: null, error })) })
    return () => { active = false }
  }, [month, session.token, onUnauthorized])

  return state.month === month ? state : { month, data: null, error: '' }
}

function useTransactions() {
  const { session, onUnauthorized } = useScreen()
  const [items, setItems] = useState<Transacao[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    api<Transacao[]>('/transacoes', {}, session.token)
      .then((result) => { if (active) setItems(recentFirst(result)) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  return { items, setItems, error }
}

function TelegramConnect() {
  const { session, onUnauthorized, notify } = useScreen()
  const [linked, setLinked] = useState<boolean | null>(null)
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState<VinculoTelegram | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    if (!pending) return
    const timeout = window.setTimeout(() => setExpired(true), Math.max(0, new Date(pending.expiraEm).getTime() - Date.now()))
    return () => window.clearTimeout(timeout)
  }, [pending])

  useEffect(() => {
    let active = true
    api<{ vinculado: boolean }>('/integracoes/telegram/vinculo', {}, session.token)
      .then((result) => { if (active) setLinked(result.vinculado) })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setError) })
    return () => { active = false }
  }, [session.token, onUnauthorized])

  useEffect(() => {
    if (open && !dialog.current?.open) dialog.current?.showModal()
    if (!open && dialog.current?.open) dialog.current?.close()
  }, [open])

  useEffect(() => {
    if (!open || !pending || linked) return
    const interval = window.setInterval(() => {
      api<{ vinculado: boolean }>('/integracoes/telegram/vinculo', {}, session.token)
        .then((result) => {
          if (!result.vinculado) return
          setLinked(true)
          setPending(null)
          notify('Telegram conectado.')
        })
        .catch(() => undefined)
    }, 3000)
    return () => window.clearInterval(interval)
  }, [open, pending, linked, session.token, notify])

  async function generateCode() {
    setBusy(true)
    setError('')
    try {
      setExpired(false)
      setPending(await api<VinculoTelegram>('/integracoes/telegram/vinculo', { method: 'POST' }, session.token))
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
      if (result.vinculado) setPending(null)
      else notify('Ainda não recebemos a confirmação do bot.')
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  async function copyCommand() {
    if (!pending) return
    try {
      await navigator.clipboard.writeText('/start ' + pending.codigo)
      notify('Comando copiado.')
    } catch {
      notify('Selecione e copie o comando exibido.')
    }
  }

  function closeOnBackdrop(event: ReactMouseEvent<HTMLDialogElement>) {
    if (event.target === event.currentTarget) setOpen(false)
  }

  return <>
    <button className={'telegram-button' + (linked ? ' linked' : '')} type="button" onClick={() => setOpen(true)}
      aria-label={linked ? 'Telegram conectado' : 'Conectar Telegram'}>
      <Send size={17} aria-hidden="true" />{linked === false && <span>Telegram</span>}{linked && <i className="status-dot" aria-hidden="true" />}
    </button>
    <dialog ref={dialog} className="sheet" onClose={() => setOpen(false)} onClick={closeOnBackdrop} aria-labelledby="telegram-title">
      <button className="sheet-close" type="button" onClick={() => setOpen(false)} aria-label="Fechar"><X size={18} /></button>
      <span className={'sheet-icon telegram' + (linked ? ' linked' : '')}>{linked ? <CircleCheck size={26} /> : <Send size={24} />}</span>
      {linked ? <>
        <h2 id="telegram-title">Telegram conectado</h2>
        <p>Envie entradas e despesas pelo chat do bot, como “gastei 32 no Uber”. Elas aparecem aqui automaticamente.</p>
        <button className="primary-button" type="button" onClick={() => setOpen(false)}>Entendi</button>
      </> : pending && !expired ? <>
        <h2 id="telegram-title">Confirme no Telegram</h2>
        {pending.link ? <>
          <p>Abra o bot e toque em <strong>Iniciar</strong>. Esta tela atualiza sozinha quando a conexão for confirmada.</p>
          <a className="primary-button telegram-open" href={pending.link} target="_blank" rel="noreferrer"><ExternalLink size={18} /> Abrir no Telegram</a>
          <p className="sheet-fallback">Ou envie este comando no chat privado com o bot:</p>
        </> : <p>Envie este comando no chat privado com o bot. Esta tela atualiza sozinha quando a conexão for confirmada.</p>}
        <div className="command-box"><code>/start {pending.codigo}</code><button type="button" onClick={copyCommand} aria-label="Copiar comando"><Copy size={17} /></button></div>
        <p className="sheet-waiting"><span className="pulse" aria-hidden="true" /> Aguardando confirmação · o código vale por 10 minutos</p>
        <div className="sheet-actions">
          <button type="button" onClick={checkLink} disabled={busy}>Verificar agora</button>
          <button type="button" onClick={generateCode} disabled={busy}>Gerar outro código</button>
        </div>
      </> : <>
        <h2 id="telegram-title">Lance pelo Telegram</h2>
        <p>Conecte sua conta ao bot e registre gastos mandando uma mensagem, sem abrir o app.</p>
        <ol className="sheet-steps">
          <li><span>1</span>Toque em conectar para gerar seu código</li>
          <li><span>2</span>Abra o bot e toque em Iniciar</li>
          <li><span>3</span>Pronto: é só escrever “almoço 35 reais”</li>
        </ol>
        {expired && <p className="form-error">O código expirou. Gere um novo para continuar.</p>}
        <button className="primary-button" type="button" onClick={generateCode} disabled={busy || linked === null}>{busy ? 'Aguarde...' : 'Conectar Telegram'}</button>
      </>}
      {error && <p className="form-error" role="alert">{error}</p>}
    </dialog>
  </>
}

function Gauge({ percent }: { percent: number }) {
  const value = Math.max(0, Math.min(100, percent))
  return (
    <svg className="gauge-arc" viewBox="0 0 260 140" aria-hidden="true">
      <defs>
        <linearGradient id="gauge-gradient" x1="0" y1="0" x2="1" y2="0"><stop stopColor="var(--accent)" /><stop offset="1" stopColor="var(--accent-2)" /></linearGradient>
      </defs>
      <path d="M 18 130 A 112 112 0 0 1 242 130" pathLength={100} className="gauge-track" />
      {value > 0 && <path d="M 18 130 A 112 112 0 0 1 242 130" pathLength={100} className="gauge-value" strokeDasharray={`${value} 100`} />}
    </svg>
  )
}

function Delta({ current, previous, inverse, month }: { current: string; previous: string; inverse?: boolean; month: string }) {
  const now = cents(current)
  const before = cents(previous)
  if (before === 0n) return <small className="delta">Sem dados de {monthName(shiftMonth(month, -1), 'short')}</small>
  const change = Number((now - before) * 1000n / before) / 10
  const good = inverse ? change <= 0 : change >= 0
  return <small className={'delta ' + (good ? 'good' : 'bad')}>{change > 0 ? '+' : change < 0 ? '−' : ''}{Math.abs(change).toLocaleString('pt-BR')}% vs {monthName(shiftMonth(month, -1), 'short')}</small>
}

function FlowChart({ transactions }: { transactions: Transacao[] }) {
  const [periodOffset, setPeriodOffset] = useState(0)
  const [selected, setSelected] = useState(6)
  const [anchor] = useState(() => {
    const date = new Date()
    date.setHours(12, 0, 0, 0)
    return date
  })
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(anchor)
    date.setDate(anchor.getDate() + periodOffset * 7 - 6 + index)
    const key = isoDate(date)
    const entries = transactions.filter((item) => item.data === key)
    return {
      key,
      weekday: date.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', ''),
      label: String(date.getDate()),
      income: entries.filter((item) => item.tipo === 'RECEITA').reduce((sum, item) => sum + cents(item.valor), 0n),
      expense: entries.filter((item) => item.tipo === 'DESPESA').reduce((sum, item) => sum + cents(item.valor), 0n),
    }
  })
  const max = days.flatMap((day) => [day.income, day.expense]).reduce((current, amount) => amount > current ? amount : current, 0n)
  const height = (amount: bigint) => max === 0n || amount === 0n ? 4 : Math.max(6, Number(amount * 1200n / max) / 10)
  const hasEarlier = transactions.some((item) => item.data < days[0].key)
  const day = days[selected]
  const balance = day.income - day.expense

  function showPeriod(offset: number) {
    setPeriodOffset(offset)
    setSelected(6)
  }

  return (
    <section className="card flow-card" aria-labelledby="flow-title">
      <div className="section-heading">
        <h2 id="flow-title">Entradas e saídas</h2>
        <div className="week-nav" role="group" aria-label="Semana do gráfico">
          <button type="button" onClick={() => showPeriod(periodOffset - 1)} disabled={!hasEarlier} aria-label="Sete dias anteriores"><ChevronLeft size={16} /></button>
          <span>{periodOffset === 0 ? '7 dias' : dateLabel(days[0].key) + ' – ' + dateLabel(days[6].key)}</span>
          <button type="button" onClick={() => showPeriod(periodOffset + 1)} disabled={periodOffset === 0} aria-label="Próximos sete dias"><ChevronRight size={16} /></button>
        </div>
      </div>
      <div className="chart-legend"><span><i className="income" /> Entradas</span><span><i className="expense" /> Saídas</span></div>
      <div className="bar-chart">
        {days.map((item, index) => <button type="button" key={item.key} className={'bar-day' + (index === selected ? ' selected' : '')} onClick={() => setSelected(index)}
          aria-pressed={index === selected} aria-label={`${item.weekday}, ${dateLabel(item.key)}: entrou ${moneyFromCents(item.income)}, saiu ${moneyFromCents(item.expense)}`}>
          <span className="bars" aria-hidden="true">
            <span className={'bar income' + (item.income ? '' : ' empty')} style={{ height: height(item.income) }} />
            <span className={'bar expense' + (item.expense ? '' : ' empty')} style={{ height: height(item.expense) }} />
          </span>
          <span className="bar-label"><small>{item.weekday.charAt(0).toUpperCase()}</small><strong>{item.label}</strong></span>
        </button>)}
      </div>
      <div className="flow-readout" aria-live="polite">
        <strong>{capitalize(day.weekday)}, {dateLabel(day.key)}</strong>
        <span className="income-text">+ {moneyFromCents(day.income)}</span>
        <span className="expense-text">− {moneyFromCents(day.expense)}</span>
        <span className={'flow-balance ' + (balance < 0n ? 'negative' : 'positive')}>{balance < 0n ? '−' : '+'} {moneyFromCents(balance < 0n ? -balance : balance)}</span>
      </div>
    </section>
  )
}

function SpendingDonut({ categories, month }: { categories: ResumoCategoria[]; month: string }) {
  const spending = categories.filter((item) => item.tipo === 'DESPESA' && cents(item.total) > 0n)
  const total = spending.reduce((sum, item) => sum + cents(item.total), 0n)
  const top = spending.slice(0, 4).map((item) => ({ name: item.nome, amount: cents(item.total), color: categoryStyle(item.nome).color }))
  const rest = spending.slice(4).reduce((sum, item) => sum + cents(item.total), 0n)
  const slices = rest > 0n ? [...top, { name: 'Outras', amount: rest, color: '#a3a3ac' }] : top
  const gap = slices.length > 1 ? 1.2 : 0
  const arcs = slices.map((slice, index) => ({
    ...slice,
    size: share(slice.amount, total),
    offset: slices.slice(0, index).reduce((sum, item) => sum + share(item.amount, total), 0),
  }))

  return (
    <section className="card spending-card" aria-labelledby="spending-title">
      <div className="section-heading"><h2 id="spending-title">Gastos por categoria</h2><span className="chip">{capitalize(monthName(month))}</span></div>
      {total === 0n ? <p className="empty-note">Seus gastos do mês aparecerão aqui após o primeiro lançamento.</p> : <div className="spending-body">
        <div className="donut">
          <svg viewBox="0 0 120 120" aria-hidden="true">
            <circle cx="60" cy="60" r="50" pathLength={100} className="donut-track" />
            {arcs.map((arc) => <circle key={arc.name} cx="60" cy="60" r="50" pathLength={100} stroke={arc.color}
              strokeDasharray={`${Math.max(arc.size - gap, 0.5)} ${100 - Math.max(arc.size - gap, 0.5)}`} strokeDashoffset={-arc.offset} />)}
          </svg>
          <div className="donut-center"><strong>{compactMoney(total)}</strong><span>total gasto</span></div>
        </div>
        <ul className="donut-legend">
          {slices.map((slice) => <li key={slice.name}>
            <i style={{ background: slice.color }} aria-hidden="true" /><span>{slice.name}</span><strong>{share(slice.amount, total).toLocaleString('pt-BR')}%</strong>
          </li>)}
        </ul>
      </div>}
    </section>
  )
}

export function DashboardPage({ onLogout }: { onLogout: () => void }) {
  const { session } = useScreen()
  const [month, setMonth] = useState(currentMonth)
  const resumo = useResumo(month)
  const transactions = useTransactions()
  const error = resumo.error || transactions.error
  const data = resumo.data
  const spentPercent = data ? share(cents(data.totalDespesas), cents(data.totalReceitas)) : 0

  return (
    <div className="page-content">
      <header className="dashboard-header">
        <span className="avatar" aria-hidden="true">{(session.nome?.trim().charAt(0) || 'V').toUpperCase()}</span>
        <div><p>Olá, {session.nome?.split(' ')[0] || 'você'}</p><h1>Visão geral</h1></div>
        <TelegramConnect />
        <button className="round-button" type="button" onClick={onLogout} aria-label="Sair"><LogOut size={18} /></button>
      </header>
      {error ? <ErrorState message={error} /> : <>
        <section className="card gauge-card" aria-label="Resumo do mês">
          <div className="gauge">
            <Gauge percent={spentPercent} />
            <div className="gauge-center">
              <span>Saldo do mês</span>
              <strong>{data ? money(data.saldo) : '—'}</strong>
              <small>{!data ? 'Carregando...' : cents(data.totalReceitas) === 0n ? 'Nenhuma entrada no mês' : spentPercent.toLocaleString('pt-BR') + '% das entradas já saíram'}</small>
            </div>
          </div>
          <div className="month-nav" role="group" aria-label="Mês do resumo">
            <button type="button" onClick={() => setMonth(shiftMonth(month, -1))} aria-label="Mês anterior"><ChevronLeft size={18} /></button>
            <strong>{capitalize(monthName(month))}, {month.slice(0, 4)}</strong>
            <button type="button" onClick={() => setMonth(shiftMonth(month, 1))} disabled={month >= currentMonth()} aria-label="Próximo mês"><ChevronRight size={18} /></button>
          </div>
          <div className="metrics">
            <div className="metric income">
              <span><ArrowDownLeft size={14} aria-hidden="true" /> Entradas</span>
              <strong>{data ? money(data.totalReceitas) : '—'}</strong>
              {data && <Delta current={data.totalReceitas} previous={data.receitasMesAnterior} month={month} />}
            </div>
            <div className="metric expense">
              <span><ArrowUpRight size={14} aria-hidden="true" /> Saídas</span>
              <strong>{data ? money(data.totalDespesas) : '—'}</strong>
              {data && <Delta current={data.totalDespesas} previous={data.despesasMesAnterior} month={month} inverse />}
            </div>
          </div>
          {data && <p className="accumulated">Saldo acumulado <strong>{money(data.saldoAcumulado)}</strong></p>}
        </section>
        {transactions.items && <FlowChart transactions={transactions.items} />}
        {data && <SpendingDonut categories={data.categorias} month={month} />}
        <section className="recent-section">
          <div className="section-heading"><h2>Últimos lançamentos</h2><Link to="/extrato">Ver todos <ArrowRight size={14} /></Link></div>
          {!transactions.items ? <p className="loading">Carregando lançamentos...</p> : transactions.items.length
            ? transactions.items.slice(0, 4).map((item) => <TransactionRow key={item.id} item={item} />)
            : <div className="empty-state"><p>Nenhum lançamento ainda.</p><Link to="/novo">Registrar o primeiro</Link></div>}
        </section>
      </>}
    </div>
  )
}

export function TransactionsPage() {
  const { session, onUnauthorized, notify } = useScreen()
  const month = currentMonth()
  const resumo = useResumo(month)
  const { items, setItems, error } = useTransactions()
  const [filter, setFilter] = useState<'TODAS' | Tipo>('TODAS')
  const [selected, setSelected] = useState<Transacao | null>(null)
  const [busy, setBusy] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const dialog = useRef<HTMLDialogElement>(null)

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
  const groups = visible.reduce<{ date: string; items: Transacao[] }[]>((list, item) => {
    const last = list[list.length - 1]
    if (last?.date === item.data) last.items.push(item)
    else list.push({ date: item.data, items: [item] })
    return list
  }, [])
  const groupLabel = (date: string) => date === today() ? 'Hoje' : date === yesterday() ? 'Ontem' : capitalize(new Date(date + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'short', day: 'numeric', month: 'short' }).replace(/\./g, ''))
  const groupTotal = (entries: Transacao[]) => entries.reduce((sum, item) => sum + (item.tipo === 'RECEITA' ? cents(item.valor) : -cents(item.valor)), 0n)
  const data = resumo.data

  return (
    <div className="page-content">
      <ScreenTop context="Seus lançamentos" title="Extrato" />
      <section className="summary-card" aria-label={'Resumo de ' + monthName(month)}>
        <span>Saldo de {monthName(month)}</span>
        <strong>{data ? money(data.saldo) : '—'}</strong>
        <div className="summary-cells">
          <div><span>Entradas</span><strong>{data ? compactMoney(cents(data.totalReceitas)) : '—'}</strong></div>
          <div><span>Saídas</span><strong>{data ? compactMoney(cents(data.totalDespesas)) : '—'}</strong></div>
          <div><span>Lançamentos</span><strong>{data ? data.quantidadeLancamentos : '—'}</strong></div>
        </div>
      </section>
      <div className="segmented" role="group" aria-label="Filtrar lançamentos">
        {([['TODAS', 'Todas'], ['RECEITA', 'Entradas'], ['DESPESA', 'Saídas']] as const).map(([value, label]) =>
          <button key={value} type="button" className={filter === value ? 'selected' : ''} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</button>)}
      </div>
      {error ? <ErrorState message={error} /> : !items ? <p className="loading">Carregando lançamentos...</p> : groups.length ? groups.map((group) =>
        <section className="day-group" key={group.date} aria-label={groupLabel(group.date)}>
          <div className="day-heading"><h2>{groupLabel(group.date)}</h2><span>{(() => { const total = groupTotal(group.items); return (total < 0n ? '− ' : '+ ') + moneyFromCents(total < 0n ? -total : total) })()}</span></div>
          <div className="card list-card">{group.items.map((item) => <TransactionRow key={item.id} item={item} showDate={false} onDelete={(entry) => { setDeleteError(''); setSelected(entry) }} />)}</div>
        </section>)
        : <div className="empty-state"><p>Nenhum lançamento nesta seleção.</p><Link to="/novo">Registrar lançamento</Link></div>}
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
  const location = useLocation()
  const { id } = useParams()
  const [categories, setCategories] = useState<Categoria[] | null>(null)
  const [loaded, setLoaded] = useState(!id)
  const [loadError, setLoadError] = useState('')
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
    Promise.all([api<Categoria[]>('/categorias', {}, session.token), id ? api<Transacao[]>('/transacoes', {}, session.token) : null])
      .then(([result, transactions]) => {
        if (!active) return
        setCategories(result)
        if (!transactions) return
        const item = transactions.find((entry) => String(entry.id) === id)
        if (!item) {
          setLoadError('Lançamento não encontrado.')
          return
        }
        setType(result.find((category) => category.id === item.categoriaId)?.tipo ?? item.tipo)
        setAmountDigits(cents(item.valor).toString().replace(/^0+/, ''))
        setDescription(item.descricao)
        setCategoryId(String(item.categoriaId))
        setDate(item.data)
        setLoaded(true)
      })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, id ? setLoadError : setError) })
    return () => { active = false }
  }, [id, session.token, onUnauthorized])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (!amountDigits) {
      setError('Digite um valor maior que zero.')
      return
    }
    if (!categoryId) {
      setError('Escolha uma categoria.')
      return
    }
    setBusy(true)
    try {
      await api(id ? '/transacoes/' + id : '/transacoes', {
        method: id ? 'PUT' : 'POST',
        body: JSON.stringify({ valor: decimalFromCents(BigInt(amountDigits)), descricao: description.trim(), data: date, categoriaId: Number(categoryId) }),
      }, session.token)
      notify(id ? 'Lançamento atualizado.' : 'Lançamento salvo.')
      if (!id) navigate('/')
      else if (location.key === 'default') navigate('/extrato', { replace: true })
      else navigate(-1)
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  const available = categories?.filter((category) => category.tipo === type) ?? []
  const keepCaretAtEnd = (input: HTMLInputElement) => input.setSelectionRange(input.value.length, input.value.length)

  return (
    <div className="page-content">
      <header className="form-header">
        <button className="round-button" type="button" onClick={() => navigate(-1)} aria-label="Voltar"><ChevronLeft size={19} /></button>
        <h1>{id ? 'Editar lançamento' : 'Novo lançamento'}</h1>
        <Link className="round-button" to={id ? '/extrato' : '/'} aria-label="Cancelar"><X size={18} /></Link>
      </header>
      {loadError ? <ErrorState message={loadError} /> : !loaded ? <p className="loading">Carregando lançamento...</p> : <>
        <div className={'segmented type-toggle ' + type.toLowerCase()} role="group" aria-label="Tipo do lançamento">
          <button type="button" className={type === 'DESPESA' ? 'selected' : ''} aria-pressed={type === 'DESPESA'} onClick={() => { setType('DESPESA'); setCategoryId('') }}><ArrowUpRight size={16} /> Despesa</button>
          <button type="button" className={type === 'RECEITA' ? 'selected' : ''} aria-pressed={type === 'RECEITA'} onClick={() => { setType('RECEITA'); setCategoryId('') }}><ArrowDownLeft size={16} /> Receita</button>
        </div>
        <form className="form-stack" onSubmit={submit}>
          <label className="amount-input">
            <span>Valor da {type === 'DESPESA' ? 'despesa' : 'receita'}</span>
            <span className="amount-value"><small>R$</small><input ref={amountInput} value={moneyFromCents(BigInt(amountDigits || '0')).replace('R$ ', '')}
              onChange={(event) => setAmountDigits(event.target.value.replace(/\D/g, '').replace(/^0+/, '').slice(0, 19))}
              onFocus={(event) => keepCaretAtEnd(event.currentTarget)} onClick={(event) => keepCaretAtEnd(event.currentTarget)}
              inputMode="numeric" aria-label="Valor em reais" aria-describedby="amount-help" size={Math.max(4, moneyFromCents(BigInt(amountDigits || '0')).length - 3)} /></span>
            <small id="amount-help">Os dois últimos números são centavos</small>
          </label>
          <fieldset className="category-picker">
            <legend><span>Categoria</span><Link to="/categorias">Gerenciar</Link></legend>
            {!categories ? <p className="loading">Carregando categorias...</p> : <div className="category-grid">
              {available.map((category) => <label key={category.id} className={'category-tile' + (String(category.id) === categoryId ? ' selected' : '')}>
                <input type="radio" name="categoria" value={category.id} checked={String(category.id) === categoryId} onChange={() => setCategoryId(String(category.id))} />
                <CategoryIcon name={category.nome} size="lg" />
                <span>{category.nome}</span>
              </label>)}
              <Link className="category-tile add" to="/categorias/nova"><span className="category-icon lg neutral"><Plus size={19} /></span><span>Nova</span></Link>
            </div>}
            {categories && !available.length && <p className="form-note">Crie uma categoria de {type === 'DESPESA' ? 'despesa' : 'receita'} para continuar.</p>}
          </fieldset>
          <label className="field"><span>Descrição</span><div><PencilLine size={18} /><input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Ex.: Compras no mercado" maxLength={255} required /></div></label>
          <div className="field date-field"><span id="date-label">Data</span>
            <div className="date-options" role="group" aria-labelledby="date-label">
              <button type="button" className={date === today() ? 'selected' : ''} aria-pressed={date === today()} onClick={() => setDate(today())}>Hoje</button>
              <button type="button" className={date === yesterday() ? 'selected' : ''} aria-pressed={date === yesterday()} onClick={() => setDate(yesterday())}>Ontem</button>
              <label className={'date-picker' + (date !== today() && date !== yesterday() ? ' selected' : '')}><Calendar size={16} aria-hidden="true" />
                <input type="date" value={date} onChange={(event) => setDate(event.target.value)} required aria-label="Escolher data" /></label>
            </div>
          </div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={busy || !categories || !available.length}><Check size={19} /> {busy ? 'Salvando...' : id ? 'Salvar alterações' : type === 'DESPESA' ? 'Salvar despesa' : 'Salvar receita'}</button>
        </form>
      </>}
    </div>
  )
}

const bubbleSlots = [[50, 46], [21, 64], [80, 30], [79, 77]]

export function CategoriesPage() {
  const month = currentMonth()
  const { data, error } = useResumo(month)
  const [type, setType] = useState<Tipo>('DESPESA')

  const expenses = data?.categorias.filter((item) => item.tipo === 'DESPESA') ?? []
  const spent = expenses.reduce((sum, item) => sum + cents(item.total), 0n)
  const bubbles = expenses.filter((item) => cents(item.total) > 0n).slice(0, 4)
  const group = data?.categorias.filter((item) => item.tipo === type) ?? []
  const groupTotal = group.reduce((sum, item) => sum + cents(item.total), 0n)

  return (
    <div className="page-content">
      <ScreenTop context="Organize seus gastos" title="Categorias" action={<Link className="pill-button" to="/categorias/nova"><Plus size={16} /> Nova</Link>} />
      {error ? <ErrorState message={error} /> : !data ? <p className="loading">Carregando categorias...</p> : <>
        <section className="card bubble-card" aria-labelledby="bubble-title">
          <h2 id="bubble-title">Para onde vai seu dinheiro</h2>
          <p>{capitalize(monthName(month))} · {money(decimalFromCents(spent))} em despesas</p>
          {bubbles.length ? <div className="bubbles">
            {bubbles.map((item, index) => {
              const percent = share(cents(item.total), spent)
              const size = Math.round(Math.min(150, 64 + percent * 2.3))
              return <div key={item.id} className="bubble" style={{ '--category': categoryStyle(item.nome).color, width: size, height: size, left: bubbleSlots[index][0] + '%', top: bubbleSlots[index][1] + '%', fontSize: size / 5 } as CSSProperties}>
                <strong>{Math.round(percent)}%</strong><span>{item.nome}</span>
              </div>
            })}
          </div> : <p className="empty-note">Quando você lançar despesas neste mês, a divisão por categoria aparece aqui.</p>}
        </section>
        <div className="segmented" role="group" aria-label="Tipo de categoria">
          {(['DESPESA', 'RECEITA'] as const).map((value) => <button key={value} type="button" className={type === value ? 'selected' : ''} aria-pressed={type === value} onClick={() => setType(value)}>
            {value === 'DESPESA' ? 'Despesas' : 'Receitas'}<span className="count">{data.categorias.filter((item) => item.tipo === value).length}</span>
          </button>)}
        </div>
        <div className="category-cards">
          {group.map((item) => {
            const total = cents(item.total)
            return <Link className="card category-card" key={item.id} to={'/categorias/' + item.id + '/editar'}>
              <CategoryIcon name={item.nome} size="lg" />
              <div><h3>{item.nome}</h3><span>{item.quantidade ? item.quantidade + (item.quantidade === 1 ? ' lançamento' : ' lançamentos') : 'Sem lançamentos'}</span></div>
              <strong className={total ? '' : 'muted'}>{money(item.total)}</strong>
              <span className="progress" aria-label={share(total, groupTotal).toLocaleString('pt-BR') + '% do total'}><i style={{ width: share(total, groupTotal) + '%', background: categoryStyle(item.nome).color }} /></span>
            </Link>
          })}
          <Link className="category-card add" to="/categorias/nova"><span><Plus size={18} /></span>Nova categoria</Link>
        </div>
      </>}
    </div>
  )
}

const authBars = [42, 68, 36, 84, 56, 100, 72]

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
      <header className="auth-hero"><span className="brand">cdg.</span><h1>{isLogin ? 'Suas finanças,\nmais claras.' : 'Comece a cuidar\ndo seu dinheiro.'}</h1>
        <div className="auth-hero-card" aria-hidden="true">
          <span>Saldo do mês</span>
          <strong>R$ 3.240,00</strong>
          <div>{authBars.map((height, index) => <i key={index} style={{ height: height + '%' }} />)}</div>
        </div>
      </header>
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

const nameSuggestions: Record<Tipo, string[]> = {
  DESPESA: ['Mercado', 'Moradia', 'Transporte', 'Lazer', 'Saúde', 'Educação'],
  RECEITA: ['Salário', 'Freelance', 'Investimentos', 'Renda extra'],
}

export function CategoryFormPage() {
  const { session, onUnauthorized, notify } = useScreen()
  const navigate = useNavigate()
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const [categories, setCategories] = useState<Categoria[] | null>(null)
  const [loadError, setLoadError] = useState('')
  const [name, setName] = useState('')
  const [type, setType] = useState<Tipo>(searchParams.get('tipo') === 'RECEITA' ? 'RECEITA' : 'DESPESA')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [target, setTarget] = useState('')
  const [deleteError, setDeleteError] = useState('')
  const dialog = useRef<HTMLDialogElement>(null)
  const current = categories?.find((category) => String(category.id) === id)

  useEffect(() => {
    if (!id) return
    let active = true
    api<Categoria[]>('/categorias', {}, session.token)
      .then((result) => {
        if (!active) return
        const category = result.find((item) => String(item.id) === id)
        if (!category) {
          setLoadError('Categoria não encontrada.')
          return
        }
        setCategories(result)
        setName(category.nome)
        setType(category.tipo)
      })
      .catch((err: unknown) => { if (active) requestError(err, onUnauthorized, setLoadError) })
    return () => { active = false }
  }, [id, session.token, onUnauthorized])

  useEffect(() => {
    if (deleting && !dialog.current?.open) dialog.current?.showModal()
    if (!deleting && dialog.current?.open) dialog.current?.close()
  }, [deleting])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Digite o nome da categoria.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api(id ? '/categorias/' + id : '/categorias', { method: id ? 'PUT' : 'POST', body: JSON.stringify({ nome: name.trim(), tipo: type }) }, session.token)
      notify(id ? 'Categoria atualizada.' : 'Categoria criada.')
      navigate('/categorias')
    } catch (err) {
      requestError(err, onUnauthorized, setError)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!current) return
    setBusy(true)
    setDeleteError('')
    try {
      await api<void>('/categorias/' + current.id + (current.quantidadeLancamentos ? '?moverPara=' + target : ''), { method: 'DELETE' }, session.token)
      notify('Categoria excluída.')
      navigate('/categorias', { replace: true })
    } catch (err) {
      requestError(err, onUnauthorized, setDeleteError)
      setBusy(false)
    }
  }

  const preview = name.trim() || (type === 'DESPESA' ? 'Nova despesa' : 'Nova receita')
  const { Icon: PreviewIcon, color: previewColor } = categoryStyle(preview)
  const used = current?.quantidadeLancamentos ?? 0
  const usedLabel = used + (used === 1 ? ' lançamento' : ' lançamentos')
  const destinations = categories?.filter((category) => category.tipo === current?.tipo && category.id !== current?.id) ?? []
  const typeLabel = (value: Tipo) => value === 'DESPESA' ? 'despesa' : 'receita'

  return (
    <div className="page-content">
      <header className="form-header">
        <button className="round-button" type="button" onClick={() => navigate(-1)} aria-label="Voltar"><ChevronLeft size={19} /></button>
        <h1>{id ? 'Editar categoria' : 'Nova categoria'}</h1>
        <Link className="round-button" to="/categorias" aria-label="Cancelar"><X size={18} /></Link>
      </header>
      {loadError ? <ErrorState message={loadError} /> : id && !current ? <p className="loading">Carregando categoria...</p> : <>
        <section className={'category-preview ' + type.toLowerCase()} aria-label="Prévia da categoria">
          <span className="category-preview-icon" style={{ '--category': previewColor } as CSSProperties}><PreviewIcon size={30} aria-hidden="true" /></span>
          <strong>{preview}</strong>
          <span className="category-preview-type">{type === 'DESPESA' ? <ArrowUpRight size={14} /> : <ArrowDownLeft size={14} />} {type === 'DESPESA' ? 'Despesa' : 'Receita'}</span>
        </section>
        <form className="form-stack" onSubmit={submit}>
          <fieldset className="category-choice" aria-describedby={used ? 'type-locked' : undefined}><legend>Tipo da categoria</legend>
            {(['DESPESA', 'RECEITA'] as const).map((value) => {
              const locked = used > 0 && value !== current?.tipo
              return <label className={'choice ' + value.toLowerCase() + (type === value ? ' selected' : '') + (locked ? ' disabled' : '')} key={value}>
                <input type="radio" name="tipo" checked={type === value} disabled={locked} onChange={() => setType(value)} />
                <span className="choice-icon">{value === 'DESPESA' ? <ArrowUpRight size={20} /> : <ArrowDownLeft size={20} />}</span>
                <span><strong>{value === 'DESPESA' ? 'Despesa' : 'Receita'}</strong><small>{value === 'DESPESA' ? 'Para gastos e pagamentos' : 'Para dinheiro que entra'}</small></span>
                <CircleCheck className="choice-check" size={21} />
              </label>
            })}
            {used > 0 && <p className="form-note choice-note" id="type-locked">O tipo não pode ser alterado porque a categoria já tem {usedLabel}.</p>}
          </fieldset>
          <label className="field"><span>Nome da categoria</span><div><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ex.: Saúde" maxLength={100} required /><Tag size={18} /></div></label>
          <div className="name-suggestions" role="group" aria-label="Sugestões de nome">
            {nameSuggestions[type].map((suggestion) => <button type="button" key={suggestion} className={name.trim() === suggestion ? 'selected' : ''} onClick={() => setName(suggestion)}>
              <CategoryIcon name={suggestion} />{suggestion}
            </button>)}
          </div>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={busy}><Check size={18} /> {busy ? 'Salvando...' : id ? 'Salvar alterações' : 'Salvar categoria'}</button>
          {current && <button className="danger-outline" type="button" onClick={() => { setDeleteError(''); setTarget(''); setDeleting(true) }} disabled={busy}><Trash2 size={17} /> Excluir categoria</button>}
        </form>
      </>}
      <dialog ref={dialog} className="confirm-dialog" onClose={() => setDeleting(false)} aria-labelledby="delete-category-title">
        <span className="dialog-icon"><Trash2 size={23} /></span>
        <h2 id="delete-category-title">Excluir categoria?</h2>
        {!used ? <p>{current?.nome} será removida. Ela não tem lançamentos.</p> : destinations.length ? <>
          <p>{usedLabel} {used === 1 ? 'será movido' : 'serão movidos'} para:</p>
          <div className="category-grid move-grid" role="radiogroup" aria-label="Categoria de destino">
            {destinations.map((category) => <label key={category.id} className={'category-tile' + (String(category.id) === target ? ' selected' : '')}>
              <input type="radio" name="destino" value={category.id} checked={String(category.id) === target} onChange={() => setTarget(String(category.id))} />
              <CategoryIcon name={category.nome} size="lg" />
              <span>{category.nome}</span>
            </label>)}
          </div>
        </> : <p>{current?.nome} tem {usedLabel}. Para excluí-la, primeiro <Link className="inline-link" to={'/categorias/nova?tipo=' + current?.tipo}>crie outra categoria de {typeLabel(current?.tipo ?? 'DESPESA')}</Link> para recebê-{used === 1 ? 'lo' : 'los'}.</p>}
        {deleteError && <p className="form-error" role="alert">{deleteError}</p>}
        <div className="dialog-actions">
          <button type="button" onClick={() => setDeleting(false)} disabled={busy}>Cancelar</button>
          <button type="button" className="danger-button" onClick={remove} disabled={busy || (used > 0 && !target)}>{busy ? 'Excluindo...' : 'Excluir'}</button>
        </div>
      </dialog>
    </div>
  )
}

import { FormEvent, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ArrowRight, BookOpen, CheckCircle2, Eye, EyeOff, Loader2, LockKeyhole } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { errorMessage } from '../lib/api'

export default function AuthPage() {
  const { user, login, signup } = useAuth()
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  if (user) return <Navigate to="/" replace />

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'login') await login(email, password)
      else await signup(name, email, password)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid min-h-screen bg-white lg:grid-cols-2">
      <section className="relative hidden overflow-hidden bg-ink p-12 text-white lg:flex lg:flex-col">
        <div className="absolute -right-40 -top-40 h-96 w-96 rounded-full bg-brand-500/20 blur-3xl" />
        <div className="relative flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-500"><BookOpen size={21} /></div>
          <div className="font-semibold">Nexus Knowledge</div>
        </div>
        <div className="relative my-auto max-w-xl">
          <p className="mb-5 text-sm font-semibold uppercase tracking-[.22em] text-brand-100">Knowledge, made useful</p>
          <h1 className="text-5xl font-semibold leading-tight tracking-tight">Turn company documents into trusted answers.</h1>
          <p className="mt-6 text-lg leading-8 text-slate-300">A secure, source-grounded workspace that helps every team find what matters—without digging through folders.</p>
          <div className="mt-10 space-y-4 text-sm text-slate-300">
            {['Answers grounded in your private documents', 'Clear citations back to every source', 'Enterprise-grade access and audit boundaries'].map(item => (
              <div className="flex items-center gap-3" key={item}><CheckCircle2 className="text-brand-500" size={19} />{item}</div>
            ))}
          </div>
        </div>
        <p className="relative text-xs text-slate-500">Built for teams that care where the answer came from.</p>
      </section>
      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-md">
          <div className="mb-9 lg:hidden"><div className="flex items-center gap-2 font-semibold"><BookOpen className="text-brand-600" />Nexus Knowledge</div></div>
          <div className="mb-8">
            <h2 className="text-3xl font-bold tracking-tight">{mode === 'login' ? 'Welcome back' : 'Create your workspace account'}</h2>
            <p className="mt-2 text-sm text-slate-500">{mode === 'login' ? 'Sign in to continue to your knowledge base.' : 'Start asking better questions of your documents.'}</p>
          </div>
          <div className="mb-7 grid grid-cols-2 rounded-xl bg-slate-100 p-1">
            {(['login', 'signup'] as const).map(item => (
              <button
                type="button"
                key={item}
                onClick={() => { setMode(item); setError('') }}
                className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${mode === item ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
              >
                {item === 'login' ? 'Sign in' : 'Create account'}
              </button>
            ))}
          </div>
          <form onSubmit={submit} className="space-y-5">
            {mode === 'signup' && <div><label className="label">Full name</label><input className="input" value={name} onChange={e => setName(e.target.value)} required maxLength={120} placeholder="Ada Lovelace" /></div>}
            <div><label className="label">Work email</label><input className="input" type="email" value={email} onChange={e => setEmail(e.target.value)} required placeholder="you@company.com" /></div>
            <div>
              <label className="label">Password</label>
              <div className="relative">
                <input className="input pr-11" type={showPassword ? 'text' : 'password'} value={password} onChange={e => setPassword(e.target.value)} required minLength={8} placeholder="At least 8 characters" />
                <button type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword(value => !value)} className="absolute inset-y-0 right-0 grid w-11 place-items-center text-slate-400 hover:text-slate-600">
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
            </div>
            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700">{error}</div>}
            <button className="btn-primary w-full py-3" disabled={busy}>
              {busy ? <Loader2 className="animate-spin" size={18} /> : <>{mode === 'login' ? 'Sign in' : 'Create account'}<ArrowRight size={17} /></>}
            </button>
          </form>
          <div className="mt-10 flex items-center justify-center gap-2 text-xs text-slate-400"><LockKeyhole size={14} />JWT-secured sessions</div>
        </div>
      </section>
    </div>
  )
}

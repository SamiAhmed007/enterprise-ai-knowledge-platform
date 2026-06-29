import {
  ArrowRight,
  BookOpen,
  Bot,
  Boxes,
  Check,
  Database,
  FileSearch,
  Github,
  Layers3,
  LockKeyhole,
  MessageSquareText,
  Network,
  Sparkles,
  Workflow,
} from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const features = [
  {
    icon: FileSearch,
    title: 'Answers you can verify',
    description: 'Hybrid retrieval finds the right passages, then shows the document and page behind every answer.',
  },
  {
    icon: Layers3,
    title: 'Knowledge by workspace',
    description: 'Keep teams, documents, and conversations organized with secure workspace-level boundaries.',
  },
  {
    icon: Workflow,
    title: 'Built for real operations',
    description: 'Async ingestion, retries, live status, rate limits, and admin analytics make the platform production ready.',
  },
]

const stack = [
  ['React + TypeScript', 'Responsive product experience'],
  ['Spring Boot + Java 17', 'Secure REST and streaming APIs'],
  ['PostgreSQL + pgvector', 'Hybrid semantic search'],
  ['Redis', 'Caching and rate limits'],
  ['OpenAI / Azure OpenAI', 'Grounded generation'],
  ['Docker + GitHub Actions', 'Repeatable delivery'],
]

const workflow = [
  ['01', 'Upload', 'Add PDF, DOCX, or TXT sources to an isolated workspace.'],
  ['02', 'Index', 'Background workers extract, chunk, and embed every useful passage.'],
  ['03', 'Ask', 'Hybrid search finds evidence and streams a grounded answer.'],
  ['04', 'Verify', 'Open citations, inspect the source preview, and share with confidence.'],
]

export default function LandingPage() {
  const { user } = useAuth()
  if (user) return <Navigate to="/dashboard" replace />

  return (
    <div className="min-h-screen overflow-hidden bg-[#070b14] text-white">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[760px] bg-[radial-gradient(circle_at_50%_-10%,rgba(99,102,241,.28),transparent_50%)]" />
      <header className="relative z-10 mx-auto flex h-20 max-w-7xl items-center px-5 sm:px-8">
        <Link to="/" className="flex items-center gap-3 font-semibold tracking-tight">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 shadow-lg shadow-brand-500/25">
            <BookOpen size={20} />
          </span>
          <span>Nexus <span className="hidden text-slate-400 sm:inline">Knowledge</span></span>
        </Link>
        <nav className="ml-auto flex items-center gap-2 sm:gap-3">
          <a href="#features" className="hidden px-3 py-2 text-sm text-slate-400 transition hover:text-white sm:block">Features</a>
          <Link to="/login" className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-200 transition hover:bg-white/5">Login</Link>
          <Link to="/login?mode=signup" className="inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-slate-950 shadow-xl transition hover:-translate-y-0.5 hover:bg-brand-50">
            Try demo <ArrowRight size={15} />
          </Link>
        </nav>
      </header>

      <main className="relative">
        <section className="mx-auto grid max-w-7xl items-center gap-14 px-5 pb-24 pt-20 sm:px-8 lg:grid-cols-[1.02fr_.98fr] lg:pb-32 lg:pt-28">
          <div>
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-brand-400/20 bg-brand-500/10 px-3 py-1.5 text-xs font-semibold text-brand-100">
              <Sparkles size={13} /> Enterprise knowledge, finally useful
            </div>
            <h1 className="max-w-3xl text-5xl font-semibold leading-[1.05] tracking-[-.045em] sm:text-6xl lg:text-7xl">
              Turn scattered documents into <span className="bg-gradient-to-r from-indigo-300 via-violet-300 to-sky-300 bg-clip-text text-transparent">trusted answers.</span>
            </h1>
            <p className="mt-7 max-w-xl text-lg leading-8 text-slate-400">
              A secure AI knowledge platform that understands your team’s documents, answers questions in real time, and cites every source.
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <Link to="/login?mode=signup" className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-500 px-6 py-3.5 text-sm font-semibold shadow-xl shadow-brand-500/20 transition hover:-translate-y-0.5 hover:bg-brand-400">
                Try the platform <ArrowRight size={17} />
              </Link>
              <Link to="/login" className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/10 bg-white/5 px-6 py-3.5 text-sm font-semibold text-slate-200 backdrop-blur transition hover:bg-white/10">
                Login to workspace
              </Link>
            </div>
            <div className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-xs text-slate-500">
              {['Source-grounded answers', 'Workspace isolation', 'Streaming responses'].map(item => (
                <span key={item} className="flex items-center gap-2"><Check size={14} className="text-emerald-400" />{item}</span>
              ))}
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-xl">
            <div className="absolute -inset-10 rounded-full bg-brand-500/10 blur-3xl" />
            <div className="relative overflow-hidden rounded-[28px] border border-white/10 bg-slate-950/80 p-3 shadow-2xl shadow-black/40 backdrop-blur">
              <div className="rounded-2xl border border-white/10 bg-[#101624]">
                <div className="flex items-center gap-2 border-b border-white/10 px-5 py-4">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-400/80" />
                  <span className="h-2.5 w-2.5 rounded-full bg-amber-400/80" />
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/80" />
                  <span className="ml-3 text-xs text-slate-500">Knowledge Assistant</span>
                </div>
                <div className="space-y-6 p-5 sm:p-7">
                  <div className="ml-auto max-w-[82%] rounded-2xl rounded-tr-md bg-brand-500 px-4 py-3 text-sm">
                    Summarize our data retention policy.
                  </div>
                  <div className="flex gap-3">
                    <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-white text-brand-600"><Bot size={16} /></span>
                    <div className="space-y-3 text-sm leading-6 text-slate-300">
                      <p>Customer records are retained for seven years, with quarterly access reviews and documented deletion procedures.</p>
                      <div className="rounded-xl border border-white/10 bg-white/[.04] p-3">
                        <div className="flex items-center gap-2 text-xs font-semibold text-white"><BookOpen size={13} className="text-brand-300" />Data-Retention-Policy.pdf · Page 4</div>
                        <div className="mt-2 h-1.5 w-full rounded-full bg-white/5"><div className="h-full w-[92%] rounded-full bg-gradient-to-r from-brand-500 to-sky-400" /></div>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-slate-500">
                    Ask a follow-up question…
                    <span className="ml-auto grid h-8 w-8 place-items-center rounded-lg bg-brand-500 text-white"><ArrowRight size={15} /></span>
                  </div>
                </div>
              </div>
            </div>
            <div className="absolute -bottom-5 -left-4 hidden items-center gap-3 rounded-2xl border border-white/10 bg-slate-900/90 px-4 py-3 shadow-xl backdrop-blur sm:flex">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-emerald-400/10 text-emerald-400"><LockKeyhole size={17} /></span>
              <div><p className="text-xs font-semibold">Workspace secured</p><p className="text-[10px] text-slate-500">Role-based access enabled</p></div>
            </div>
          </div>
        </section>

        <section id="features" className="border-y border-white/[.07] bg-white/[.025] py-24">
          <div className="mx-auto max-w-7xl px-5 sm:px-8">
            <div className="max-w-2xl">
              <p className="text-sm font-semibold text-brand-300">One knowledge layer</p>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Everything your team needs to find the right answer.</h2>
              <p className="mt-4 leading-7 text-slate-400">From ingestion to retrieval to governance, Nexus turns RAG infrastructure into a product people actually enjoy using.</p>
            </div>
            <div className="mt-12 grid gap-5 md:grid-cols-3">
              {features.map(({ icon: Icon, title, description }) => (
                <article key={title} className="group rounded-2xl border border-white/[.08] bg-white/[.035] p-6 transition hover:-translate-y-1 hover:border-brand-400/30 hover:bg-white/[.055]">
                  <span className="grid h-11 w-11 place-items-center rounded-xl border border-brand-400/20 bg-brand-500/10 text-brand-300"><Icon size={20} /></span>
                  <h3 className="mt-6 text-lg font-semibold">{title}</h3>
                  <p className="mt-3 text-sm leading-6 text-slate-400">{description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
          <div className="text-center">
            <p className="text-sm font-semibold text-brand-300">From upload to answer</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">A workflow designed for trust.</h2>
          </div>
          <div className="relative mt-12 grid gap-4 md:grid-cols-4">
            <div className="absolute left-[12%] right-[12%] top-7 hidden h-px bg-gradient-to-r from-transparent via-brand-400/40 to-transparent md:block" />
            {workflow.map(([number, title, description]) => (
              <article key={number} className="relative rounded-2xl border border-white/[.08] bg-[#0d1321] p-5">
                <span className="grid h-12 w-12 place-items-center rounded-xl border border-brand-400/20 bg-brand-500/10 text-xs font-bold text-brand-300">{number}</span>
                <h3 className="mt-5 font-semibold">{title}</h3>
                <p className="mt-2 text-xs leading-5 text-slate-500">{description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="border-y border-white/[.07] bg-white/[.025]">
          <div className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
          <div className="grid items-start gap-14 lg:grid-cols-[.8fr_1.2fr]">
            <div>
              <span className="grid h-11 w-11 place-items-center rounded-xl bg-brand-500/10 text-brand-300"><Boxes size={21} /></span>
              <h2 className="mt-6 text-3xl font-semibold tracking-tight">A production-grade stack, end to end.</h2>
              <p className="mt-4 leading-7 text-slate-400">Modern, observable, containerized, and designed to scale from a local demo to cloud deployment.</p>
              <div className="mt-7 flex items-center gap-3 text-sm text-slate-400"><Network size={17} className="text-brand-300" />Streaming SSE architecture</div>
              <div className="mt-3 flex items-center gap-3 text-sm text-slate-400"><Database size={17} className="text-brand-300" />Vector and full-text hybrid ranking</div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {stack.map(([name, purpose]) => (
                <div key={name} className="rounded-xl border border-white/[.08] bg-white/[.03] p-4">
                  <p className="text-sm font-semibold text-slate-200">{name}</p>
                  <p className="mt-1 text-xs text-slate-500">{purpose}</p>
                </div>
              ))}
            </div>
          </div>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 pb-24 sm:px-8">
          <div className="relative overflow-hidden rounded-[28px] border border-brand-300/15 bg-gradient-to-br from-brand-600 to-violet-700 px-6 py-14 text-center shadow-2xl shadow-brand-950/30 sm:px-12">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_0%,rgba(255,255,255,.18),transparent_35%)]" />
            <div className="relative">
              <MessageSquareText className="mx-auto text-brand-100" size={30} />
              <h2 className="mt-5 text-3xl font-semibold tracking-tight">Your knowledge is ready to talk.</h2>
              <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-indigo-100">Create a workspace, upload your first document, and see source-grounded answers in minutes.</p>
              <Link to="/login?mode=signup" className="mt-7 inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-brand-700 shadow-xl transition hover:-translate-y-0.5">
                Try demo <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-white/[.07]">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-8 text-xs text-slate-500 sm:flex-row sm:items-center sm:px-8">
          <div className="flex items-center gap-2"><BookOpen size={14} />Nexus Knowledge</div>
          <p className="sm:ml-auto">Enterprise AI Knowledge Platform</p>
          <span className="hidden sm:block">·</span>
          <span className="flex items-center gap-1.5"><Github size={13} />Production-style full stack</span>
        </div>
      </footer>
    </div>
  )
}

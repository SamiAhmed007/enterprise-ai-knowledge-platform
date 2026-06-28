import { useCallback, useEffect, useState } from 'react'
import { ArrowRight, CheckCircle2, Clock3, FileText, MessageSquareText, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/PageHeader'
import InlineError from '../components/InlineError'
import { useAuth } from '../context/AuthContext'
import { api, errorMessage, formatBytes } from '../lib/api'
import { Document, SessionSummary } from '../types'
import { useWorkspace } from '../context/WorkspaceContext'
import StatusBadge from '../components/StatusBadge'

export default function DashboardPage() {
  const { user } = useAuth()
  const { activeWorkspaceId, activeWorkspace } = useWorkspace()
  const [documents, setDocuments] = useState<Document[]>([])
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    if (!activeWorkspaceId) {
      setDocuments([]); setSessions([]); setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    Promise.all([
      api.get<Document[]>(`/workspaces/${activeWorkspaceId}/documents`),
      api.get<SessionSummary[]>(`/workspaces/${activeWorkspaceId}/chats`),
    ])
      .then(([docs, chats]) => { setDocuments(docs.data); setSessions(chats.data) })
      .catch(err => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [activeWorkspaceId])
  useEffect(() => { load() }, [load])

  const ready = documents.filter(document => document.status === 'READY').length
  const indexed = documents.filter(document => document.status === 'READY').reduce((sum, document) => sum + document.sizeBytes, 0)

  return (
    <>
      <PageHeader title={`Good ${new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening'}, ${user?.name.split(' ')[0]}`}
        description={`Here’s what’s happening in ${activeWorkspace?.name || 'your workspace'}.`} />
      <InlineError message={error} retry={load} />
      <div className="grid gap-4 sm:grid-cols-3">
        {[
          { label: 'Documents', value: loading ? '—' : documents.length, detail: `${ready} ready to search`, icon: FileText, color: 'bg-indigo-50 text-indigo-600' },
          { label: 'Conversations', value: loading ? '—' : sessions.length, detail: 'Saved in your history', icon: MessageSquareText, color: 'bg-emerald-50 text-emerald-600' },
          { label: 'Knowledge indexed', value: loading ? '—' : formatBytes(indexed), detail: 'Available to the assistant', icon: Sparkles, color: 'bg-amber-50 text-amber-600' },
        ].map(({ label, value, detail, icon: Icon, color }) => (
          <div className="panel p-5" key={label}>
            <div className="flex items-start justify-between"><div><p className="text-sm font-medium text-slate-500">{label}</p><p className="mt-2 text-3xl font-bold tracking-tight">{value}</p></div><div className={`rounded-xl p-2.5 ${color}`}><Icon size={20} /></div></div>
            <p className="mt-4 text-xs text-slate-400">{detail}</p>
          </div>
        ))}
      </div>
      <div className="mt-6 grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <section className="panel p-6">
          <div className="flex items-center justify-between"><div><h2 className="font-semibold">Recent documents</h2><p className="mt-1 text-sm text-slate-500">Latest additions to your knowledge base</p></div><Link className="text-sm font-semibold text-brand-600" to="/documents">View all</Link></div>
          <div className="mt-5 divide-y">
            {documents.slice(0, 5).map(document => (
              <div className="flex items-center gap-3 py-3.5" key={document.id}>
                <div className="rounded-lg bg-slate-100 p-2 text-slate-500"><FileText size={18} /></div>
                <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{document.name}</div><div className="mt-0.5 text-xs text-slate-400">{formatBytes(document.sizeBytes)} · {new Date(document.createdAt).toLocaleDateString()}</div></div>
                <StatusBadge status={document.status} />
              </div>
            ))}
            {!documents.length && <div className="py-12 text-center text-sm text-slate-400">{loading ? 'Loading documents…' : 'No documents yet. Add your first one to get started.'}</div>}
          </div>
        </section>
        <section className="relative overflow-hidden rounded-2xl bg-ink p-6 text-white shadow-panel">
          <div className="absolute -bottom-20 -right-16 h-48 w-48 rounded-full bg-brand-500/20 blur-2xl" />
          <div className="relative">
            <div className="mb-10 grid h-11 w-11 place-items-center rounded-xl bg-brand-500"><Sparkles size={21} /></div>
            <h2 className="text-xl font-semibold">Ask your knowledge base</h2>
            <p className="mt-2 text-sm leading-6 text-slate-400">Get grounded answers with direct citations to your team’s source documents.</p>
            <Link to="/chat" className="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-white">Start a conversation <ArrowRight size={16} /></Link>
          </div>
          <div className="relative mt-10 flex gap-5 border-t border-white/10 pt-5 text-xs text-slate-400">
            <span className="flex items-center gap-1.5"><CheckCircle2 size={14} className="text-emerald-400" />Source grounded</span>
            <span className="flex items-center gap-1.5"><Clock3 size={14} />History saved</span>
          </div>
        </section>
      </div>
    </>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { ArrowRight, CheckCircle2, Clock3, FilePlus2, FileText, MessageSquarePlus, MessageSquareText, Sparkles } from 'lucide-react'
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
          <div className="panel group p-5 hover:-translate-y-0.5 hover:shadow-lg" key={label}>
            <div className="flex items-start justify-between"><div><p className="text-sm font-medium text-slate-500">{label}</p><p className="mt-2 text-3xl font-bold tracking-tight">{value}</p></div><div className={`rounded-xl p-2.5 ${color}`}><Icon size={20} /></div></div>
            <p className="mt-4 flex items-center gap-1.5 text-xs text-slate-400"><span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />{detail}</p>
          </div>
        ))}
      </div>
      <div className="mt-6 grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <section className="panel p-5 sm:p-6">
          <div className="flex items-center justify-between"><div><h2 className="font-semibold">Recent documents</h2><p className="mt-1 text-sm text-slate-500">Latest additions to your knowledge base</p></div><Link className="text-sm font-semibold text-brand-600" to="/documents">View all</Link></div>
          <div className="mt-5 divide-y">
            {documents.slice(0, 5).map(document => (
              <div className="flex items-center gap-3 py-3.5" key={document.id}>
                <div className="rounded-lg bg-slate-100 p-2 text-slate-500"><FileText size={18} /></div>
                <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{document.name}</div><div className="mt-0.5 text-xs text-slate-400">{formatBytes(document.sizeBytes)} · {new Date(document.createdAt).toLocaleDateString()}</div></div>
                <StatusBadge status={document.status} />
              </div>
            ))}
            {!documents.length && <div className="py-12 text-center">{loading ? <p className="text-sm text-slate-400">Loading documents…</p> : <><span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-slate-50 text-slate-300"><FilePlus2 size={22} /></span><p className="mt-3 text-sm font-semibold text-slate-700">No documents yet</p><p className="mt-1 text-xs text-slate-400">Add your first source to make this workspace searchable.</p><Link to="/documents" className="btn-secondary mt-4 px-3 py-2 text-xs"><FilePlus2 size={14} />Upload document</Link></>}</div>}
          </div>
        </section>
        <div className="space-y-6">
          <section className="relative overflow-hidden rounded-2xl bg-[#101624] p-6 text-white shadow-panel">
            <div className="absolute -bottom-20 -right-16 h-48 w-48 rounded-full bg-brand-500/25 blur-2xl" />
            <div className="relative">
              <div className="mb-8 grid h-11 w-11 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 shadow-lg shadow-black/20"><Sparkles size={21} /></div>
              <h2 className="text-xl font-semibold">Ask your knowledge base</h2>
              <p className="mt-2 text-sm leading-6 text-slate-400">Get grounded answers with direct citations to your team’s source documents.</p>
              <Link to="/chat" className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-white transition hover:gap-3">Start a conversation <ArrowRight size={16} /></Link>
            </div>
            <div className="relative mt-8 flex flex-wrap gap-5 border-t border-white/10 pt-5 text-xs text-slate-400">
              <span className="flex items-center gap-1.5"><CheckCircle2 size={14} className="text-emerald-400" />Source grounded</span>
              <span className="flex items-center gap-1.5"><Clock3 size={14} />History saved</span>
            </div>
          </section>
          <section className="panel p-5">
            <div className="flex items-center justify-between"><h2 className="text-sm font-semibold">Recent conversations</h2><Link to="/chat" className="text-xs font-semibold text-brand-600">View all</Link></div>
            <div className="mt-3 space-y-1">
              {sessions.slice(0, 3).map(session => (
                <Link to="/chat" key={session.id} className="flex items-center gap-3 rounded-xl px-2 py-2.5 transition hover:bg-slate-50">
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600"><MessageSquareText size={14} /></span>
                  <span className="min-w-0 flex-1 truncate text-xs font-medium text-slate-700">{session.title}</span>
                  <ArrowRight size={13} className="text-slate-300" />
                </Link>
              ))}
              {!sessions.length && !loading && (
                <div className="py-5 text-center">
                  <MessageSquarePlus className="mx-auto text-slate-300" size={21} />
                  <p className="mt-2 text-xs font-medium text-slate-500">No chats in this workspace</p>
                  <Link to="/chat" className="mt-2 inline-block text-xs font-semibold text-brand-600">Ask your first question</Link>
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </>
  )
}

import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { BookOpen, Check, ChevronRight, Copy, Loader2, MessageSquarePlus, Pencil, RefreshCw, Send, Sparkles, Square, Trash2, UserRound } from 'lucide-react'
import { api, errorMessage } from '../lib/api'
import { ChatStreamError, isAbortError, streamChat } from '../lib/chatStream'
import { AskResponse, ChatMessage, SessionDetail, SessionSummary } from '../types'
import { useWorkspace } from '../context/WorkspaceContext'
import MarkdownContent from '../components/MarkdownContent'
import CitationCard from '../components/CitationCard'
import ConfirmModal from '../components/ConfirmModal'
import PromptModal from '../components/PromptModal'
import Toast from '../components/Toast'

const suggestions = [
  'Summarize the key ideas in my documents',
  'What policies should I pay attention to?',
  'Compare the main recommendations',
]

export default function ChatPage() {
  const { activeWorkspaceId, activeWorkspace } = useWorkspace()
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadingSession, setLoadingSession] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [renaming, setRenaming] = useState<SessionSummary | null>(null)
  const [deleting, setDeleting] = useState<SessionSummary | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [copiedMessage, setCopiedMessage] = useState<string | number | null>(null)
  const bottom = useRef<HTMLDivElement>(null)
  const abortController = useRef<AbortController | null>(null)

  const loadSessions = useCallback(() => {
    if (!activeWorkspaceId) { setSessions([]); return Promise.resolve() }
    return api.get<SessionSummary[]>(`/workspaces/${activeWorkspaceId}/chats`)
      .then(response => setSessions(response.data))
      .catch(err => setError(errorMessage(err)))
  }, [activeWorkspaceId])
  useEffect(() => {
    abortController.current?.abort()
    setActiveId(null)
    setMessages([])
    setSessions([])
    setError('')
    void loadSessions()
  }, [loadSessions])
  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, busy])
  useEffect(() => () => abortController.current?.abort(), [])
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        abortController.current?.abort()
        setActiveId(null); setMessages([]); setError('')
      }
    }
    window.addEventListener('keydown', shortcut)
    return () => window.removeEventListener('keydown', shortcut)
  }, [])
  useEffect(() => {
    if (!success) return
    const timer = window.setTimeout(() => setSuccess(''), 3500)
    return () => window.clearTimeout(timer)
  }, [success])

  const openSession = async (id: string) => {
    if (!activeWorkspaceId) return
    setLoadingSession(true)
    setError('')
    try {
      const response = await api.get<SessionDetail>(`/workspaces/${activeWorkspaceId}/chats/${id}`)
      setActiveId(id)
      setMessages(response.data.messages)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoadingSession(false)
    }
  }

  const send = async (event: FormEvent) => {
    event.preventDefault()
    const text = question.trim()
    if (!text || busy || !activeWorkspaceId) return
    const assistantId = `stream-${Date.now()}`
    const controller = new AbortController()
    abortController.current = controller
    setQuestion(''); setBusy(true); setError('')
    setMessages(items => [
      ...items,
      { role: 'USER', content: text, citations: [] },
      { id: assistantId, role: 'ASSISTANT', content: '', citations: [], streaming: true },
    ])

    const updateAssistant = (update: (message: ChatMessage) => ChatMessage) => {
      setMessages(items => items.map(message => message.id === assistantId ? update(message) : message))
    }

    try {
      await streamChat(
        activeWorkspaceId,
        { sessionId: activeId, question: text },
        controller.signal,
        {
          onSources: citations => updateAssistant(message => ({ ...message, citations })),
          onDelta: content => updateAssistant(message => ({ ...message, content: message.content + content })),
          onDone: (sessionId, citations) => {
            setActiveId(sessionId)
            updateAssistant(message => ({ ...message, citations, streaming: false }))
          },
        },
      )
      await loadSessions()
    } catch (err) {
      if (isAbortError(err)) {
        updateAssistant(message => ({
          ...message,
          content: message.content || 'Response stopped.',
          streaming: false,
        }))
        setError('Generation stopped. The partial response was not saved.')
      } else if (err instanceof ChatStreamError && err.fallbackAllowed && !err.receivedDelta) {
        try {
          const response = (await api.post<AskResponse>(
            `/workspaces/${activeWorkspaceId}/chats/ask`,
            { sessionId: activeId, question: text },
          )).data
          setActiveId(response.sessionId)
          updateAssistant(message => ({
            ...message,
            content: response.answer,
            citations: response.citations,
            streaming: false,
          }))
          await loadSessions()
        } catch (fallbackError) {
          updateAssistant(message => ({ ...message, streaming: false }))
          setError(errorMessage(fallbackError))
          setQuestion(text)
        }
      } else {
        updateAssistant(message => ({ ...message, streaming: false }))
        setError(errorMessage(err))
        if (!(err instanceof ChatStreamError && err.receivedDelta)) setQuestion(text)
      }
    } finally {
      abortController.current = null
      setBusy(false)
    }
  }

  const cancel = () => abortController.current?.abort()

  const remove = async () => {
    if (!activeWorkspaceId || !deleting) return
    setDeleteBusy(true)
    try {
      await api.delete(`/workspaces/${activeWorkspaceId}/chats/${deleting.id}`)
      if (activeId === deleting.id) { setActiveId(null); setMessages([]) }
      await loadSessions()
      setDeleting(null)
      setSuccess('Conversation deleted.')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setDeleteBusy(false)
    }
  }

  const rename = async (title: string) => {
    if (!activeWorkspaceId || !renaming) return
    try {
      const response = await api.patch<SessionSummary>(
        `/workspaces/${activeWorkspaceId}/chats/${renaming.id}`,
        { title },
      )
      setSessions(items => items.map(item => item.id === renaming.id ? response.data : item))
      setRenaming(null)
      setSuccess('Conversation renamed.')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const regenerate = async () => {
    if (!activeWorkspaceId || !activeId || busy) return
    setBusy(true); setError('')
    try {
      const response = (await api.post<AskResponse>(
        `/workspaces/${activeWorkspaceId}/chats/${activeId}/regenerate`,
      )).data
      setMessages(items => {
        const lastAssistant = items.map(item => item.role).lastIndexOf('ASSISTANT')
        if (lastAssistant < 0) return items
        return items.map((message, index) => index === lastAssistant
          ? { ...message, content: response.answer, citations: response.citations, streaming: false }
          : message)
      })
      setSuccess('Answer regenerated.')
      await loadSessions()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const copyAnswer = async (content: string, key: string | number) => {
    await navigator.clipboard.writeText(content)
    setCopiedMessage(key)
    window.setTimeout(() => setCopiedMessage(null), 1800)
  }

  return (
    <>
    <Toast message={success} dismiss={() => setSuccess('')} />
    <PromptModal open={!!renaming} title="Rename conversation" label="Conversation title" initialValue={renaming?.title || ''} onCancel={() => setRenaming(null)} onSubmit={rename} />
    <ConfirmModal open={!!deleting} title="Delete conversation?" description="This permanently deletes the conversation and its saved messages." confirmLabel="Delete conversation" destructive busy={deleteBusy} onCancel={() => setDeleting(null)} onConfirm={() => void remove()} />
    <div className="flex h-[calc(100vh-7rem)] min-h-[620px] overflow-hidden rounded-[22px] border border-slate-200/70 bg-white shadow-panel dark:border-slate-800 dark:bg-slate-900">
      <aside className="hidden w-72 shrink-0 border-r border-slate-200/70 bg-slate-50/60 p-4 md:flex md:flex-col">
        <button className="btn-primary w-full shadow-brand-500/10" disabled={busy} onClick={() => { setActiveId(null); setMessages([]); setError('') }}><MessageSquarePlus size={17} />New conversation</button>
        <p className="mb-2 mt-6 px-2 text-[10px] font-semibold uppercase tracking-[.16em] text-slate-400">Recent conversations</p>
        <div className="space-y-1 overflow-y-auto">
          {sessions.map(session => (
            <button key={session.id} disabled={busy} onClick={() => openSession(session.id)} className={`group flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm disabled:opacity-50 ${activeId === session.id ? 'bg-white font-medium shadow-sm' : 'text-slate-600 hover:bg-white/70'}`}>
              <span className="min-w-0 flex-1 truncate">{session.title}</span>
              <span className="flex shrink-0 opacity-0 transition group-hover:opacity-100">
                <Pencil onClick={event => { event.stopPropagation(); setRenaming(session) }} className="text-slate-300 hover:text-brand-500" size={13} />
                <Trash2 onClick={event => { event.stopPropagation(); setDeleting(session) }} className="ml-2 text-slate-300 hover:text-red-500" size={13} />
              </span>
            </button>
          ))}
          {!sessions.length && (
            <div className="mx-2 mt-4 rounded-xl border border-dashed bg-white/70 px-3 py-5 text-center">
              <MessageSquarePlus className="mx-auto text-slate-300" size={20} />
              <p className="mt-2 text-xs font-medium text-slate-500">No conversations yet</p>
              <p className="mt-1 text-[11px] leading-4 text-slate-400">Your saved chats will appear here.</p>
            </div>
          )}
        </div>
      </aside>
      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex min-h-[72px] shrink-0 flex-wrap items-center gap-3 border-b border-slate-200/70 px-4 py-3 sm:px-6">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-md shadow-brand-100"><Sparkles size={18} /></div>
          <div className="min-w-0 flex-1"><h1 className="text-sm font-semibold text-slate-950">Knowledge Assistant</h1><p className="mt-0.5 truncate text-xs text-slate-400">Grounded in {activeWorkspace?.name || 'your workspace'} · Sources included</p></div>
          {activeId && <div className="flex items-center gap-0.5"><button title="Rename conversation" aria-label="Rename conversation" onClick={() => { const session = sessions.find(item => item.id === activeId); if (session) setRenaming(session) }} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800"><Pencil size={15} /></button><button title="Clear conversation" aria-label="Clear conversation" onClick={() => { const session = sessions.find(item => item.id === activeId); if (session) setDeleting(session) }} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10"><Trash2 size={15} /></button></div>}
          <select
            aria-label="Chat history"
            className="max-w-[48%] rounded-lg border bg-slate-50 px-2.5 py-2 text-xs text-slate-600 md:hidden"
            value={activeId || ''}
            onChange={event => {
              if (event.target.value) void openSession(event.target.value)
              else { setActiveId(null); setMessages([]); setError('') }
            }}
          >
            <option value="">New conversation</option>
            {sessions.map(session => <option key={session.id} value={session.id}>{session.title}</option>)}
          </select>
        </header>
        <div className="flex-1 overflow-y-auto bg-[radial-gradient(circle_at_50%_0%,rgba(238,242,255,.7),transparent_38%)] px-4 py-6 sm:px-8">
          {!messages.length && !loadingSession && (
            <div className="mx-auto flex h-full max-w-2xl flex-col items-center justify-center py-8 text-center">
              <div className="grid h-16 w-16 place-items-center rounded-2xl border border-brand-100 bg-white text-brand-600 shadow-lg shadow-brand-100/60"><BookOpen size={27} /></div>
              <h2 className="mt-6 text-2xl font-semibold tracking-tight">Ask your knowledge base</h2>
              <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">I’ll search your READY documents, synthesize the answer, and show the exact passages used.</p>
              <div className="mt-7 grid w-full gap-2 sm:grid-cols-3">
                {suggestions.map(suggestion => (
                  <button key={suggestion} onClick={() => setQuestion(suggestion)} className="group rounded-xl border bg-white p-3 text-left text-xs leading-5 text-slate-600 shadow-sm transition hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-md">
                    {suggestion}<ChevronRight className="mt-2 text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-brand-500" size={14} />
                  </button>
                ))}
              </div>
            </div>
          )}
          <div className="mx-auto max-w-3xl space-y-8">
            {loadingSession && <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-400"><Loader2 className="animate-spin" size={17} />Loading conversation…</div>}
            {messages.map((message, index) => (
              <div key={message.id || index} className={`flex items-start gap-3 ${message.role === 'USER' ? 'justify-end' : ''}`}>
                {message.role === 'ASSISTANT' && <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-sm"><Sparkles size={16} /></div>}
                <div className={message.role === 'USER' ? 'max-w-[84%] rounded-2xl rounded-tr-md bg-[#111827] px-4 py-3 text-sm leading-6 text-white shadow-sm sm:max-w-[78%]' : 'min-w-0 max-w-[calc(100%-3rem)] flex-1'}>
                  {message.role === 'ASSISTANT'
                    ? message.content
                      ? <MarkdownContent>{message.content}</MarkdownContent>
                      : message.streaming && (
                        <div className="flex h-8 items-center gap-1.5" aria-label="AI is thinking">
                          {[0, 1, 2].map(dot => <span key={dot} className="h-2 w-2 animate-bounce rounded-full bg-brand-400" style={{ animationDelay: `${dot * 120}ms` }} />)}
                        </div>
                      )
                    : <p className="whitespace-pre-wrap">{message.content}</p>}
                  {message.streaming && message.content && <span className="ml-1 inline-block h-4 w-1.5 animate-pulse rounded-sm bg-brand-500 align-middle" aria-label="Streaming response" />}
                  {!!message.citations.length && <div className="mt-5 grid gap-2 sm:grid-cols-2">
                    {message.citations.map((citation, citationIndex) => (
                      <CitationCard key={`${citation.documentId}-${citation.chunkIndex}`} citation={citation} index={citationIndex} />
                    ))}
                  </div>}
                  {message.role === 'ASSISTANT' && !message.streaming && message.content && (
                    <div className="mt-3 flex items-center gap-1">
                      <button title="Copy answer" onClick={() => void copyAnswer(message.content, message.id || index)} className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[11px] font-medium text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-200">
                        {copiedMessage === (message.id || index) ? <Check size={13} /> : <Copy size={13} />}{copiedMessage === (message.id || index) ? 'Copied' : 'Copy'}
                      </button>
                      {index === messages.length - 1 && activeId && <button title="Regenerate answer" onClick={() => void regenerate()} className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[11px] font-medium text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-200"><RefreshCw size={13} />Regenerate</button>}
                    </div>
                  )}
                </div>
                {message.role === 'USER' && <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-slate-200 text-slate-600"><UserRound size={16} /></div>}
              </div>
            ))}
            <div ref={bottom} />
          </div>
        </div>
        <div className="shrink-0 border-t border-slate-200/70 bg-white p-3 sm:px-8 sm:py-4">
          {error && <p className="mx-auto mb-2 max-w-3xl rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>}
          <form onSubmit={send} className="mx-auto flex max-w-3xl items-end gap-2 rounded-2xl border border-slate-200 bg-white p-2 shadow-[0_6px_24px_rgba(15,23,42,.08)] transition focus-within:border-brand-400 focus-within:ring-4 focus-within:ring-brand-50">
            <textarea value={question} onChange={event => setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit() } }}
              rows={1} className="max-h-32 min-h-[42px] flex-1 resize-none border-0 bg-transparent px-3 py-2.5 text-sm outline-none" placeholder="Ask about your documents…" />
            {busy
              ? <button type="button" aria-label="Stop generating" onClick={cancel} className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-900 text-white"><Square size={15} fill="currentColor" /></button>
              : <button aria-label="Send message" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-600 text-white disabled:opacity-40" disabled={!question.trim() || !activeWorkspaceId}><Send size={17} /></button>}
          </form>
          <p className="mt-2 text-center text-[11px] text-slate-400"><span className="hidden sm:inline">Enter to send · Shift+Enter for newline · ⌘/Ctrl+K for new chat · </span>Verify important information using cited sources.</p>
        </div>
      </section>
    </div>
    </>
  )
}

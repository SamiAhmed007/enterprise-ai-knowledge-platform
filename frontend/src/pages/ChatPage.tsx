import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { BookOpen, Loader2, MessageSquarePlus, Send, Sparkles, Square, Trash2, UserRound } from 'lucide-react'
import { api, errorMessage } from '../lib/api'
import { ChatStreamError, isAbortError, streamChat } from '../lib/chatStream'
import { AskResponse, ChatMessage, SessionDetail, SessionSummary } from '../types'
import { useWorkspace } from '../context/WorkspaceContext'

export default function ChatPage() {
  const { activeWorkspaceId, activeWorkspace } = useWorkspace()
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadingSession, setLoadingSession] = useState(false)
  const [error, setError] = useState('')
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

  const remove = async (event: React.MouseEvent, id: string) => {
    event.stopPropagation()
    if (!activeWorkspaceId) return
    if (!window.confirm('Delete this conversation permanently?')) return
    try {
      await api.delete(`/workspaces/${activeWorkspaceId}/chats/${id}`)
      if (activeId === id) { setActiveId(null); setMessages([]) }
      await loadSessions()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="flex h-[calc(100vh-7rem)] min-h-[580px] overflow-hidden rounded-2xl border bg-white shadow-panel">
      <aside className="hidden w-72 shrink-0 border-r bg-slate-50/60 p-4 md:flex md:flex-col">
        <button className="btn-primary w-full" disabled={busy} onClick={() => { setActiveId(null); setMessages([]); setError('') }}><MessageSquarePlus size={17} />New conversation</button>
        <p className="mb-2 mt-6 px-2 text-xs font-semibold uppercase tracking-wider text-slate-400">Recent</p>
        <div className="space-y-1 overflow-y-auto">
          {sessions.map(session => (
            <button key={session.id} disabled={busy} onClick={() => openSession(session.id)} className={`group flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm disabled:opacity-50 ${activeId === session.id ? 'bg-white font-medium shadow-sm' : 'text-slate-600 hover:bg-white/70'}`}>
              <span className="min-w-0 flex-1 truncate">{session.title}</span>
              <Trash2 onClick={event => remove(event, session.id)} className="shrink-0 text-slate-300 opacity-0 hover:text-red-500 group-hover:opacity-100" size={14} />
            </button>
          ))}
        </div>
      </aside>
      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex min-h-16 shrink-0 flex-wrap items-center gap-3 border-b px-4 py-3 sm:px-5">
          <div className="grid h-9 w-9 place-items-center rounded-xl bg-brand-50 text-brand-600"><Sparkles size={18} /></div>
          <div className="min-w-0 flex-1"><h1 className="text-sm font-semibold">Knowledge Assistant</h1><p className="truncate text-xs text-slate-400">Answers grounded in {activeWorkspace?.name || 'your workspace'}</p></div>
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
        <div className="flex-1 overflow-y-auto px-5 py-6 sm:px-8">
          {!messages.length && !loadingSession && (
            <div className="mx-auto flex h-full max-w-xl flex-col items-center justify-center text-center">
              <div className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-600"><BookOpen size={25} /></div>
              <h2 className="mt-5 text-xl font-semibold">What would you like to know?</h2>
              <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">Ask a question and I’ll search across your processed documents, answer with context, and show exactly where it came from.</p>
            </div>
          )}
          <div className="mx-auto max-w-3xl space-y-7">
            {loadingSession && <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-400"><Loader2 className="animate-spin" size={17} />Loading conversation…</div>}
            {messages.map((message, index) => (
              <div key={message.id || index} className={`flex gap-3 ${message.role === 'USER' ? 'justify-end' : ''}`}>
                {message.role === 'ASSISTANT' && <div className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-brand-600 text-white"><Sparkles size={15} /></div>}
                <div className={message.role === 'USER' ? 'max-w-[80%] rounded-2xl rounded-tr-md bg-ink px-4 py-3 text-sm leading-6 text-white' : 'min-w-0 max-w-[88%] text-sm leading-7 text-slate-700'}>
                  <p className="whitespace-pre-wrap">{message.content}{message.streaming && <span className="ml-1 inline-block h-4 w-1.5 animate-pulse rounded-sm bg-brand-500 align-middle" aria-label="Streaming response" />}</p>
                  {!!message.citations.length && <div className="mt-4 grid gap-2">
                    {message.citations.map((citation, citationIndex) => (
                      <div key={`${citation.documentId}-${citation.chunkIndex}`} className="rounded-xl border bg-gradient-to-br from-white to-slate-50 p-3.5 shadow-sm transition hover:border-brand-200 hover:shadow">
                        <div className="flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-700">
                          <BookOpen size={13} className="text-brand-600" />
                          <span>Source {citationIndex + 1} · {citation.documentName}{citation.pageNumber ? ` · Page ${citation.pageNumber}` : ''}</span>
                          <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-brand-700">
                            {Math.round(citation.score * 100)}% match
                          </span>
                        </div>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">{citation.excerpt}</p>
                      </div>
                    ))}
                  </div>}
                </div>
                {message.role === 'USER' && <div className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-slate-200 text-slate-600"><UserRound size={15} /></div>}
              </div>
            ))}
            <div ref={bottom} />
          </div>
        </div>
        <div className="shrink-0 border-t bg-white p-4 sm:px-8">
          {error && <p className="mx-auto mb-2 max-w-3xl text-xs text-red-600">{error}</p>}
          <form onSubmit={send} className="mx-auto flex max-w-3xl items-end gap-2 rounded-2xl border bg-white p-2 shadow-sm focus-within:border-brand-500 focus-within:ring-4 focus-within:ring-brand-50">
            <textarea value={question} onChange={event => setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit() } }}
              rows={1} className="max-h-32 min-h-[42px] flex-1 resize-none border-0 bg-transparent px-3 py-2.5 text-sm outline-none" placeholder="Ask about your documents…" />
            {busy
              ? <button type="button" aria-label="Stop generating" onClick={cancel} className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-900 text-white"><Square size={15} fill="currentColor" /></button>
              : <button aria-label="Send message" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-600 text-white disabled:opacity-40" disabled={!question.trim() || !activeWorkspaceId}><Send size={17} /></button>}
          </form>
          <p className="mt-2 text-center text-[11px] text-slate-400">AI can make mistakes. Verify important information using the cited sources.</p>
        </div>
      </section>
    </div>
  )
}

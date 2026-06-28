import { ChangeEvent, DragEvent, useCallback, useEffect, useRef, useState } from 'react'
import { AlertCircle, FileText, Loader2, RotateCcw, Trash2, UploadCloud } from 'lucide-react'
import PageHeader from '../components/PageHeader'
import InlineError from '../components/InlineError'
import { api, errorMessage, formatBytes } from '../lib/api'
import { Document } from '../types'
import { useWorkspace } from '../context/WorkspaceContext'
import StatusBadge from '../components/StatusBadge'
import Toast from '../components/Toast'

export default function DocumentsPage() {
  const { activeWorkspaceId, activeWorkspace } = useWorkspace()
  const [documents, setDocuments] = useState<Document[]>([])
  const [busy, setBusy] = useState(false)
  const [retryingId, setRetryingId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [dragActive, setDragActive] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  const load = useCallback(() => {
    if (!activeWorkspaceId) { setDocuments([]); return Promise.resolve() }
    return api.get<Document[]>(`/workspaces/${activeWorkspaceId}/documents`)
      .then(response => { setDocuments(response.data); setError('') })
      .catch(err => setError(errorMessage(err)))
  }, [activeWorkspaceId])
  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!documents.some(document => ['UPLOADED', 'PROCESSING'].includes(document.status))) return
    const timer = window.setInterval(() => { void load() }, 3000)
    return () => window.clearInterval(timer)
  }, [documents, load])

  useEffect(() => {
    if (!success) return
    const timer = window.setTimeout(() => setSuccess(''), 4000)
    return () => window.clearTimeout(timer)
  }, [success])

  const uploadFile = async (file?: File) => {
    if (!file || !activeWorkspaceId) return
    setBusy(true); setError('')
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await api.post<Document>(`/workspaces/${activeWorkspaceId}/documents`, body)
      setDocuments(items => [response.data, ...items.filter(item => item.id !== response.data.id)])
      setSuccess(`${file.name} was uploaded and queued for indexing.`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    await uploadFile(event.target.files?.[0])
    event.target.value = ''
  }

  const drop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setDragActive(false)
    void uploadFile(event.dataTransfer.files?.[0])
  }

  const retry = async (id: string) => {
    if (!activeWorkspaceId) return
    setRetryingId(id); setError('')
    try {
      const response = await api.post<Document>(`/workspaces/${activeWorkspaceId}/documents/${id}/retry`)
      setDocuments(items => items.map(item => item.id === id ? response.data : item))
      setSuccess(`${response.data.name} was queued for reprocessing.`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setRetryingId(null)
    }
  }

  const remove = async (id: string) => {
    if (!activeWorkspaceId) return
    if (!window.confirm('Delete this document and its indexed content?')) return
    setError('')
    try {
      await api.delete(`/workspaces/${activeWorkspaceId}/documents/${id}`)
      setDocuments(items => items.filter(item => item.id !== id))
      setSuccess('Document and indexed content deleted.')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <>
      <PageHeader title="Documents" description={`Upload and manage sources in ${activeWorkspace?.name || 'your workspace'}.`}
        action={<><input ref={input} type="file" className="hidden" accept=".pdf,.txt,.docx" onChange={upload} /><button className="btn-primary" disabled={busy} onClick={() => input.current?.click()}>{busy ? <Loader2 className="animate-spin" size={17} /> : <UploadCloud size={17} />}Upload document</button></>} />
      <InlineError message={error} retry={() => { void load() }} />
      <Toast message={success} dismiss={() => setSuccess('')} />
      <div className="panel overflow-hidden">
        <div className="border-b px-5 py-4">
          <h2 className="font-semibold">Knowledge sources</h2>
          <p className="mt-1 text-xs text-slate-500">PDF, TXT, or DOCX · 25 MB maximum</p>
        </div>
        <div className="border-b bg-slate-50/60 p-4">
          <div
            onDragEnter={event => { event.preventDefault(); setDragActive(true) }}
            onDragOver={event => event.preventDefault()}
            onDragLeave={() => setDragActive(false)}
            onDrop={drop}
            className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-5 py-6 text-center transition sm:flex-row sm:text-left ${dragActive ? 'border-brand-500 bg-brand-50' : 'border-slate-200 bg-white'}`}
          >
            <span className="mb-3 grid h-10 w-10 place-items-center rounded-xl bg-brand-50 text-brand-600 sm:mb-0 sm:mr-3"><UploadCloud size={19} /></span>
            <div>
              <p className="text-sm font-semibold">Drop a document here</p>
              <p className="mt-0.5 text-xs text-slate-400">or use the upload button above</p>
            </div>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-3 font-medium">Name</th><th className="px-5 py-3 font-medium">Size</th><th className="px-5 py-3 font-medium">Uploaded</th><th className="px-5 py-3 font-medium">Status</th><th className="px-5 py-3" /></tr></thead>
            <tbody className="divide-y">
              {documents.map(document => (
                <tr key={document.id} className="text-sm">
                  <td className="px-5 py-4"><div className="flex items-start gap-3"><span className="rounded-lg bg-brand-50 p-2 text-brand-600"><FileText size={18} /></span><div><div className="max-w-xs truncate font-medium">{document.name}</div>{document.status === 'PROCESSING' && <div className="mt-1 text-xs text-amber-600">Extracting, chunking, and indexing…</div>}{document.status === 'UPLOADED' && <div className="mt-1 text-xs text-slate-500">Queued for processing…</div>}{document.errorMessage && <div className="mt-1 flex max-w-md items-start gap-1.5 text-xs leading-5 text-red-600"><AlertCircle className="mt-0.5 shrink-0" size={12} /><span>{document.errorMessage}</span></div>}</div></div></td>
                  <td className="px-5 py-4 text-slate-500">{formatBytes(document.sizeBytes)}</td>
                  <td className="px-5 py-4 text-slate-500">{new Date(document.createdAt).toLocaleDateString()}</td>
                  <td className="px-5 py-4"><StatusBadge status={document.status} /></td>
                  <td className="px-5 py-4 text-right"><div className="flex justify-end gap-1">{document.status === 'FAILED' && <button title="Retry ingestion" aria-label={`Retry ingestion for ${document.name}`} disabled={retryingId === document.id} onClick={() => retry(document.id)} className="rounded-lg p-2 text-brand-600 hover:bg-brand-50 disabled:opacity-50">{retryingId === document.id ? <Loader2 className="animate-spin" size={16} /> : <RotateCcw size={16} />}</button>}<button title="Delete document" aria-label={`Delete ${document.name}`} onClick={() => remove(document.id)} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={16} /></button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
          {!documents.length && <div className="px-5 py-16 text-center"><UploadCloud className="mx-auto text-slate-300" size={34} /><p className="mt-3 text-sm font-medium">Your knowledge base is empty</p><p className="mt-1 text-sm text-slate-400">Upload a document to start building it.</p></div>}
        </div>
      </div>
    </>
  )
}

import { ChangeEvent, DragEvent, useCallback, useEffect, useRef, useState } from 'react'
import { AlertCircle, Eye, FileCheck2, FileText, Loader2, Pencil, RotateCcw, Trash2, UploadCloud } from 'lucide-react'
import PageHeader from '../components/PageHeader'
import InlineError from '../components/InlineError'
import { api, errorMessage, formatBytes } from '../lib/api'
import { Document } from '../types'
import { useWorkspace } from '../context/WorkspaceContext'
import StatusBadge from '../components/StatusBadge'
import Toast from '../components/Toast'
import ConfirmModal from '../components/ConfirmModal'
import PromptModal from '../components/PromptModal'

export default function DocumentsPage() {
  const { activeWorkspaceId, activeWorkspace } = useWorkspace()
  const [documents, setDocuments] = useState<Document[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploadingName, setUploadingName] = useState('')
  const [retryingId, setRetryingId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [dragActive, setDragActive] = useState(false)
  const [renaming, setRenaming] = useState<Document | null>(null)
  const [deleting, setDeleting] = useState<Document | null>(null)
  const [deletingBusy, setDeletingBusy] = useState(false)
  const [previewingId, setPreviewingId] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)

  const load = useCallback(() => {
    if (!activeWorkspaceId) { setDocuments([]); setLoading(false); return Promise.resolve() }
    setLoading(true)
    return api.get<Document[]>(`/workspaces/${activeWorkspaceId}/documents`)
      .then(response => { setDocuments(response.data); setError('') })
      .catch(err => setError(errorMessage(err)))
      .finally(() => setLoading(false))
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
    setBusy(true); setUploadProgress(0); setUploadingName(file.name); setError('')
    try {
      const body = new FormData()
      body.append('file', file)
      const response = await api.post<Document>(`/workspaces/${activeWorkspaceId}/documents`, body, {
        onUploadProgress: progress => {
          if (progress.total) setUploadProgress(Math.round((progress.loaded / progress.total) * 100))
        },
      })
      setDocuments(items => [response.data, ...items.filter(item => item.id !== response.data.id)])
      setSuccess(`${file.name} was uploaded and queued for indexing.`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
      setUploadProgress(0)
      setUploadingName('')
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

  const rename = async (name: string) => {
    if (!activeWorkspaceId || !renaming) return
    setError('')
    try {
      const response = await api.patch<Document>(
        `/workspaces/${activeWorkspaceId}/documents/${renaming.id}`,
        { name },
      )
      setDocuments(items => items.map(item => item.id === renaming.id ? response.data : item))
      setSuccess('Document renamed.')
      setRenaming(null)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const preview = async (document: Document) => {
    if (!activeWorkspaceId) return
    setPreviewingId(document.id); setError('')
    try {
      const response = await api.get(
        `/workspaces/${activeWorkspaceId}/documents/${document.id}/preview`,
        { responseType: 'blob' },
      )
      const url = URL.createObjectURL(response.data)
      window.open(url, '_blank', 'noopener,noreferrer')
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPreviewingId(null)
    }
  }

  const remove = async () => {
    if (!activeWorkspaceId || !deleting) return
    setDeletingBusy(true); setError('')
    try {
      await api.delete(`/workspaces/${activeWorkspaceId}/documents/${deleting.id}`)
      setDocuments(items => items.filter(item => item.id !== deleting.id))
      setSuccess('Document and indexed content deleted.')
      setDeleting(null)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setDeletingBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Documents" description={`Upload and manage sources in ${activeWorkspace?.name || 'your workspace'}.`}
        action={<><input ref={input} type="file" className="hidden" accept=".pdf,.txt,.docx" onChange={upload} /><button className="btn-primary" disabled={busy} onClick={() => input.current?.click()}>{busy ? <Loader2 className="animate-spin" size={17} /> : <UploadCloud size={17} />}Upload document</button></>} />
      <InlineError message={error} retry={() => { void load() }} />
      <Toast message={success} dismiss={() => setSuccess('')} />
      <PromptModal open={!!renaming} title="Rename document" label="Document name" initialValue={renaming?.name || ''} onCancel={() => setRenaming(null)} onSubmit={rename} />
      <ConfirmModal open={!!deleting} title="Delete document?" description={`This permanently deletes ${deleting?.name || 'this document'} and all of its indexed chunks. This cannot be undone.`} confirmLabel="Delete document" destructive busy={deletingBusy} onCancel={() => setDeleting(null)} onConfirm={() => void remove()} />
      <div className="panel overflow-hidden">
        <div className="border-b px-5 py-5 sm:px-6">
          <h2 className="font-semibold">Knowledge sources</h2>
          <p className="mt-1 text-xs text-slate-500">PDF, TXT, or DOCX · 25 MB maximum</p>
        </div>
        <div className="border-b bg-slate-50/60 p-4 sm:p-6">
          <div
            onDragEnter={event => { event.preventDefault(); setDragActive(true) }}
            onDragOver={event => event.preventDefault()}
            onDragLeave={() => setDragActive(false)}
            onDrop={drop}
            onClick={() => !busy && input.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={event => { if ((event.key === 'Enter' || event.key === ' ') && !busy) input.current?.click() }}
            className={`cursor-pointer rounded-2xl border-2 border-dashed px-5 py-8 text-center transition ${dragActive ? 'scale-[1.01] border-brand-500 bg-brand-50 shadow-lg shadow-brand-100/50' : 'border-slate-200 bg-white hover:border-brand-300 hover:bg-brand-50/30'} ${busy ? 'pointer-events-none' : ''}`}
          >
            {busy ? (
              <div className="mx-auto max-w-md">
                <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Loader2 className="animate-spin" size={21} /></span>
                <p className="mt-3 truncate text-sm font-semibold">Uploading {uploadingName}</p>
                <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-gradient-to-r from-brand-500 to-violet-500 transition-all" style={{ width: `${uploadProgress}%` }} /></div>
                <p className="mt-2 text-xs font-medium text-brand-600">{uploadProgress}% uploaded</p>
              </div>
            ) : (
              <>
                <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><UploadCloud size={21} /></span>
                <p className="mt-3 text-sm font-semibold">Drop a document here, or click to browse</p>
                <p className="mt-1 text-xs text-slate-400">Your document will be securely uploaded and indexed in the background.</p>
              </>
            )}
          </div>
        </div>
        {!!documents.length && (
          <div className="divide-y md:hidden">
            {documents.map(document => (
              <article key={document.id} className="p-5">
                <div className="flex items-start gap-3">
                  <span className={`rounded-xl p-2.5 ${document.status === 'READY' ? 'bg-emerald-50 text-emerald-600' : 'bg-brand-50 text-brand-600'}`}>
                    {document.status === 'READY' ? <FileCheck2 size={18} /> : <FileText size={18} />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{document.name}</p>
                    <p className="mt-1 text-xs text-slate-400">{formatBytes(document.sizeBytes)} · {new Date(document.createdAt).toLocaleDateString()}</p>
                    <div className="mt-3"><StatusBadge status={document.status} /></div>
                  </div>
                  <div className="flex">
                    <button aria-label={`Preview ${document.name}`} disabled={previewingId === document.id} onClick={() => void preview(document)} className="rounded-lg p-2 text-slate-400 hover:bg-brand-50 hover:text-brand-600">{previewingId === document.id ? <Loader2 className="animate-spin" size={16} /> : <Eye size={16} />}</button>
                    <button aria-label={`Rename ${document.name}`} onClick={() => setRenaming(document)} className="rounded-lg p-2 text-slate-400 hover:bg-brand-50 hover:text-brand-600"><Pencil size={15} /></button>
                    {document.status === 'FAILED' && <button aria-label={`Retry ingestion for ${document.name}`} disabled={retryingId === document.id} onClick={() => retry(document.id)} className="rounded-lg p-2 text-brand-600 hover:bg-brand-50 disabled:opacity-50">{retryingId === document.id ? <Loader2 className="animate-spin" size={16} /> : <RotateCcw size={16} />}</button>}
                    <button aria-label={`Delete ${document.name}`} onClick={() => setDeleting(document)} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={16} /></button>
                  </div>
                </div>
                {document.status === 'PROCESSING' && <p className="mt-3 text-xs text-amber-600">Extracting, chunking, and indexing…</p>}
                {document.status === 'UPLOADED' && <p className="mt-3 text-xs text-slate-500">Queued for processing…</p>}
                {document.errorMessage && <p className="mt-3 flex items-start gap-1.5 rounded-lg bg-red-50 p-2.5 text-xs leading-5 text-red-600"><AlertCircle className="mt-0.5 shrink-0" size={12} />{document.errorMessage}</p>}
              </article>
            ))}
          </div>
        )}
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full min-w-[760px] text-left">
            <thead className="bg-slate-50/80 text-xs uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-3 font-medium">Name</th><th className="px-5 py-3 font-medium">Size</th><th className="px-5 py-3 font-medium">Uploaded</th><th className="px-5 py-3 font-medium">Status</th><th className="px-5 py-3" /></tr></thead>
            <tbody className="divide-y">
              {documents.map(document => (
                <tr key={document.id} className="text-sm transition hover:bg-slate-50/70">
                  <td className="px-5 py-4 sm:px-6"><div className="flex items-start gap-3"><span className={`rounded-xl p-2.5 ${document.status === 'READY' ? 'bg-emerald-50 text-emerald-600' : 'bg-brand-50 text-brand-600'}`}>{document.status === 'READY' ? <FileCheck2 size={18} /> : <FileText size={18} />}</span><div><div className="max-w-xs truncate font-semibold text-slate-800">{document.name}</div>{document.status === 'PROCESSING' && <div className="mt-1 text-xs text-amber-600">Extracting, chunking, and indexing…</div>}{document.status === 'UPLOADED' && <div className="mt-1 text-xs text-slate-500">Queued for processing…</div>}{document.errorMessage && <div className="mt-1 flex max-w-md items-start gap-1.5 text-xs leading-5 text-red-600"><AlertCircle className="mt-0.5 shrink-0" size={12} /><span>{document.errorMessage}</span></div>}</div></div></td>
                  <td className="px-5 py-4 text-slate-500">{formatBytes(document.sizeBytes)}</td>
                  <td className="px-5 py-4 text-slate-500">{new Date(document.createdAt).toLocaleDateString()}</td>
                  <td className="px-5 py-4"><StatusBadge status={document.status} /></td>
                  <td className="px-5 py-4 text-right"><div className="flex justify-end gap-1"><button title="Preview document" aria-label={`Preview ${document.name}`} disabled={previewingId === document.id} onClick={() => void preview(document)} className="rounded-lg p-2 text-slate-400 hover:bg-brand-50 hover:text-brand-600">{previewingId === document.id ? <Loader2 className="animate-spin" size={16} /> : <Eye size={16} />}</button><button title="Rename document" aria-label={`Rename ${document.name}`} onClick={() => setRenaming(document)} className="rounded-lg p-2 text-slate-400 hover:bg-brand-50 hover:text-brand-600"><Pencil size={15} /></button>{document.status === 'FAILED' && <button title="Retry ingestion" aria-label={`Retry ingestion for ${document.name}`} disabled={retryingId === document.id} onClick={() => retry(document.id)} className="rounded-lg p-2 text-brand-600 hover:bg-brand-50 disabled:opacity-50">{retryingId === document.id ? <Loader2 className="animate-spin" size={16} /> : <RotateCcw size={16} />}</button>}<button title="Delete document" aria-label={`Delete ${document.name}`} onClick={() => setDeleting(document)} className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={16} /></button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loading && !documents.length && <div className="space-y-4 px-5 py-8">{[1, 2, 3].map(item => <div key={item} className="flex items-center gap-3"><span className="skeleton h-10 w-10 dark:bg-slate-800" /><div className="flex-1"><div className="skeleton h-3 w-1/3 dark:bg-slate-800" /><div className="skeleton mt-2 h-2.5 w-1/5 dark:bg-slate-800" /></div><span className="skeleton h-6 w-20 dark:bg-slate-800" /></div>)}</div>}
        {!loading && !documents.length && <div className="px-5 py-16 text-center"><span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-slate-50 text-slate-300"><FileText size={26} /></span><p className="mt-4 text-sm font-semibold">Your knowledge base is empty</p><p className="mt-1 text-sm text-slate-400">Upload your first document to start asking grounded questions.</p><button onClick={() => input.current?.click()} className="btn-secondary mt-5"><UploadCloud size={16} />Choose a document</button></div>}
      </div>
    </>
  )
}

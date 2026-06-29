import { Check, ChevronDown, Copy, FileText } from 'lucide-react'
import { useState } from 'react'
import { Citation } from '../types'

export default function CitationCard({ citation, index }: { citation: Citation, index: number }) {
  const [expanded, setExpanded] = useState(false)
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    const location = citation.pageNumber ? `, page ${citation.pageNumber}` : ''
    await navigator.clipboard.writeText(
      `[Source ${index + 1}] ${citation.documentName}${location}: ${citation.excerpt}`,
    )
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
  }

  return (
    <article className="group rounded-xl border border-slate-200/80 bg-white p-3.5 shadow-sm transition hover:border-brand-200 hover:shadow-md dark:border-slate-700 dark:bg-slate-900">
      <div className="flex items-start gap-3">
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300"><FileText size={15} /></span>
        <button onClick={() => setExpanded(value => !value)} className="min-w-0 flex-1 text-left">
          <p className="truncate text-xs font-semibold text-slate-800 dark:text-slate-100">Source {index + 1} · {citation.documentName}</p>
          <p className="mt-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-400">{citation.pageNumber ? `Page ${citation.pageNumber} · ` : ''}Chunk {citation.chunkIndex + 1}</p>
        </button>
        <span className="shrink-0 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">{Math.round(citation.score * 100)}%</span>
      </div>
      <p className={`mt-3 border-t pt-2.5 text-[11px] leading-5 text-slate-500 dark:border-slate-700 dark:text-slate-400 ${expanded ? '' : 'line-clamp-2'}`}>{citation.excerpt}</p>
      <div className="mt-2 flex items-center justify-between">
        <button onClick={() => setExpanded(value => !value)} className="inline-flex items-center gap-1 text-[10px] font-semibold text-brand-600 dark:text-brand-300">
          {expanded ? 'Collapse' : 'Preview'} <ChevronDown size={12} className={`transition ${expanded ? 'rotate-180' : ''}`} />
        </button>
        <button onClick={() => void copy()} className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-[10px] font-semibold text-slate-400 hover:bg-slate-50 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-200">
          {copied ? <Check size={11} /> : <Copy size={11} />}{copied ? 'Copied' : 'Copy citation'}
        </button>
      </div>
    </article>
  )
}

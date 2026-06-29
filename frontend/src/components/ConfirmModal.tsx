import { AlertTriangle, X } from 'lucide-react'

interface Props {
  open: boolean
  title: string
  description: string
  confirmLabel?: string
  destructive?: boolean
  busy?: boolean
  onCancel: () => void
  onConfirm: () => void
}

export default function ConfirmModal({ open, title, description, confirmLabel = 'Confirm', destructive = false, busy = false, onCancel, onConfirm }: Props) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-[90] grid place-items-center bg-slate-950/50 p-4 backdrop-blur-sm" onMouseDown={onCancel}>
      <section className="w-full max-w-md rounded-2xl border bg-white p-5 shadow-2xl dark:border-slate-700 dark:bg-slate-900" role="dialog" aria-modal="true" aria-labelledby="confirm-title" onMouseDown={event => event.stopPropagation()}>
        <div className="flex items-start gap-4">
          <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${destructive ? 'bg-red-50 text-red-600 dark:bg-red-500/10' : 'bg-amber-50 text-amber-600 dark:bg-amber-500/10'}`}><AlertTriangle size={19} /></span>
          <div className="min-w-0 flex-1"><h2 id="confirm-title" className="font-semibold text-slate-950 dark:text-white">{title}</h2><p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">{description}</p></div>
          <button aria-label="Close dialog" onClick={onCancel} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"><X size={17} /></button>
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <button className="btn-secondary dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200" onClick={onCancel} disabled={busy}>Cancel</button>
          <button className={destructive ? 'inline-flex items-center rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50' : 'btn-primary'} onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button>
        </div>
      </section>
    </div>
  )
}

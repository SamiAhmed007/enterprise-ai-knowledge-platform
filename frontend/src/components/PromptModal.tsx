import { FormEvent, useEffect, useState } from 'react'
import { X } from 'lucide-react'

interface Props {
  open: boolean
  title: string
  label: string
  initialValue: string
  submitLabel?: string
  onCancel: () => void
  onSubmit: (value: string) => Promise<void> | void
}

export default function PromptModal({ open, title, label, initialValue, submitLabel = 'Save', onCancel, onSubmit }: Props) {
  const [value, setValue] = useState(initialValue)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) setValue(initialValue) }, [initialValue, open])
  if (!open) return null
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!value.trim()) return
    setBusy(true)
    try { await onSubmit(value.trim()) } finally { setBusy(false) }
  }
  return (
    <div className="fixed inset-0 z-[90] grid place-items-center bg-slate-950/50 p-4 backdrop-blur-sm" onMouseDown={onCancel}>
      <form onSubmit={submit} className="w-full max-w-md rounded-2xl border bg-white p-5 shadow-2xl dark:border-slate-700 dark:bg-slate-900" role="dialog" aria-modal="true" onMouseDown={event => event.stopPropagation()}>
        <div className="flex items-center justify-between"><h2 className="font-semibold text-slate-950 dark:text-white">{title}</h2><button type="button" aria-label="Close dialog" onClick={onCancel} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"><X size={17} /></button></div>
        <label className="label mt-5 dark:text-slate-300">{label}</label>
        <input autoFocus className="input dark:border-slate-700 dark:bg-slate-950 dark:text-white" value={value} maxLength={500} onChange={event => setValue(event.target.value)} />
        <div className="mt-5 flex justify-end gap-2"><button type="button" className="btn-secondary dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200" onClick={onCancel}>Cancel</button><button className="btn-primary" disabled={busy || !value.trim()}>{busy ? 'Saving…' : submitLabel}</button></div>
      </form>
    </div>
  )
}

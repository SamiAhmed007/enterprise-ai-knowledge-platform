import { CheckCircle2, X } from 'lucide-react'

export default function Toast({ message, dismiss }: { message: string, dismiss: () => void }) {
  if (!message) return null
  return (
    <div className="fixed bottom-5 right-5 z-[70] flex max-w-sm items-center gap-3 rounded-2xl border border-emerald-200 bg-white px-4 py-3 text-sm text-slate-700 shadow-xl" role="status">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-emerald-50 text-emerald-600">
        <CheckCircle2 size={17} />
      </span>
      <span className="flex-1 font-medium">{message}</span>
      <button onClick={dismiss} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600" aria-label="Dismiss notification">
        <X size={15} />
      </button>
    </div>
  )
}

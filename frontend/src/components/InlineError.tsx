import { AlertCircle, RefreshCw } from 'lucide-react'

export default function InlineError({ message, retry }: { message: string, retry?: () => void }) {
  if (!message) return null
  return (
    <div className="mb-5 flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
      <AlertCircle className="shrink-0" size={17} />
      <span className="flex-1">{message}</span>
      {retry && <button className="inline-flex items-center gap-1 font-semibold hover:text-red-900" onClick={retry}><RefreshCw size={13} />Retry</button>}
    </div>
  )
}


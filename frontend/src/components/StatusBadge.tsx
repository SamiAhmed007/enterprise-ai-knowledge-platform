import { AlertCircle, CheckCircle2, Clock3, Loader2 } from 'lucide-react'
import { DocumentStatus } from '../types'

const styles: Record<DocumentStatus, string> = {
  UPLOADED: 'border-sky-200 bg-sky-50 text-sky-700',
  PROCESSING: 'border-amber-200 bg-amber-50 text-amber-700',
  READY: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  FAILED: 'border-red-200 bg-red-50 text-red-700',
}

const icons = {
  UPLOADED: Clock3,
  PROCESSING: Loader2,
  READY: CheckCircle2,
  FAILED: AlertCircle,
}

export default function StatusBadge({ status }: { status: DocumentStatus }) {
  const Icon = icons[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold ${styles[status]}`}>
      <Icon size={12} className={status === 'PROCESSING' ? 'animate-spin' : ''} />
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  )
}

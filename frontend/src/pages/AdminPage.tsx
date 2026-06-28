import { useCallback, useEffect, useState } from 'react'
import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  Clock3,
  FilePlus2,
  Files,
  LoaderCircle,
  MessageSquareText,
  RefreshCw,
  Sparkles,
  UserPlus2,
  Users,
} from 'lucide-react'
import InlineError from '../components/InlineError'
import PageHeader from '../components/PageHeader'
import { api, errorMessage } from '../lib/api'
import {
  ActivityType,
  AdminAnalytics,
  DocumentStatus,
} from '../types'

const number = new Intl.NumberFormat()
const compactNumber = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })
const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

const activityIcons: Record<ActivityType, typeof Users> = {
  USER_REGISTERED: UserPlus2,
  WORKSPACE_CREATED: Building2,
  DOCUMENT_UPLOADED: FilePlus2,
  CHAT_STARTED: MessageSquareText,
}

const statusStyle: Record<DocumentStatus, { label: string; icon: typeof Files; color: string; bar: string }> = {
  UPLOADED: { label: 'Uploaded', icon: FilePlus2, color: 'text-sky-700 bg-sky-50', bar: 'bg-sky-500' },
  PROCESSING: { label: 'Processing', icon: LoaderCircle, color: 'text-amber-700 bg-amber-50', bar: 'bg-amber-500' },
  READY: { label: 'Ready', icon: CheckCircle2, color: 'text-emerald-700 bg-emerald-50', bar: 'bg-emerald-500' },
  FAILED: { label: 'Failed', icon: AlertTriangle, color: 'text-red-700 bg-red-50', bar: 'bg-red-500' },
}

function EmptyState({ children }: { children: string }) {
  return <div className="px-5 py-10 text-center text-sm text-slate-400">{children}</div>
}

export default function AdminPage() {
  const [analytics, setAnalytics] = useState<AdminAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await api.get<AdminAnalytics>('/admin/analytics')
      setAnalytics(response.data)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const cards = [
    { label: 'Total users', value: analytics?.totalUsers, icon: Users },
    { label: 'Workspaces', value: analytics?.totalWorkspaces, icon: Building2 },
    { label: 'Documents', value: analytics?.totalDocuments, icon: Files },
    { label: 'Chat sessions', value: analytics?.totalChatSessions, icon: MessageSquareText },
    { label: 'Approx. tokens', value: analytics?.tokenUsage.totalTokens, icon: Sparkles, compact: true },
  ]

  return (
    <>
      <PageHeader
        title="Analytics"
        description="Platform health, content activity, and ingestion operations."
        action={(
          <button
            onClick={() => void load()}
            disabled={loading}
            className="flex items-center gap-2 rounded-xl border bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-60"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
        )}
      />
      <InlineError message={error} retry={() => void load()} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {cards.map(({ label, value, icon: Icon, compact }) => (
          <div className="panel flex items-center gap-4 p-5" key={label}>
            <div className="rounded-xl bg-brand-50 p-3 text-brand-600"><Icon size={20} /></div>
            <div className="min-w-0">
              <p className="truncate text-2xl font-bold">
                {value === undefined ? '—' : compact ? compactNumber.format(value) : number.format(value)}
              </p>
              <p className="text-xs text-slate-500">{label}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_1.45fr]">
        <section className="panel p-5">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">Documents by status</h2>
              <p className="mt-1 text-xs text-slate-400">Current ingestion pipeline distribution</p>
            </div>
            <Files size={19} className="text-slate-400" />
          </div>
          <div className="space-y-4">
            {(Object.keys(statusStyle) as DocumentStatus[]).map(status => {
              const details = statusStyle[status]
              const Icon = details.icon
              const count = analytics?.documentsByStatus[status] ?? 0
              const percentage = analytics?.totalDocuments
                ? Math.round((count / analytics.totalDocuments) * 100)
                : 0
              return (
                <div key={status}>
                  <div className="mb-2 flex items-center gap-2">
                    <span className={`rounded-lg p-1.5 ${details.color}`}><Icon size={15} /></span>
                    <span className="text-sm font-medium">{details.label}</span>
                    <span className="ml-auto text-sm font-semibold">{number.format(count)}</span>
                    <span className="w-9 text-right text-xs text-slate-400">{percentage}%</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                    <div className={`h-full rounded-full ${details.bar}`} style={{ width: `${percentage}%` }} />
                  </div>
                </div>
              )
            })}
          </div>
          <div className="mt-6 rounded-xl bg-slate-50 p-4">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-500">Embedding input</span>
              <span className="font-semibold">{compactNumber.format(analytics?.tokenUsage.embeddingTokens ?? 0)}</span>
            </div>
            <div className="mt-2 flex items-center justify-between text-sm">
              <span className="text-slate-500">Chat text estimate</span>
              <span className="font-semibold">{compactNumber.format(analytics?.tokenUsage.chatTokens ?? 0)}</span>
            </div>
            <p className="mt-3 text-[11px] leading-4 text-slate-400">
              Token figures are approximate: indexed chunk estimates plus chat character counts divided by four.
            </p>
          </div>
        </section>

        <section className="panel overflow-hidden">
          <div className="border-b px-5 py-4">
            <h2 className="font-semibold">Recent user activity</h2>
            <p className="mt-1 text-xs text-slate-400">Latest registrations, workspaces, uploads, and conversations</p>
          </div>
          <div className="max-h-[500px] divide-y overflow-y-auto">
            {!loading && !analytics?.recentActivity.length && <EmptyState>No activity recorded yet.</EmptyState>}
            {analytics?.recentActivity.map((activity, index) => {
              const Icon = activityIcons[activity.type]
              return (
                <div key={`${activity.type}-${activity.occurredAt}-${index}`} className="flex items-start gap-3 px-5 py-4">
                  <div className="mt-0.5 rounded-lg bg-slate-100 p-2 text-slate-500"><Icon size={16} /></div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{activity.description}</p>
                    <p className="mt-0.5 truncate text-xs text-slate-400">
                      {activity.actorName} · {activity.actorEmail}
                    </p>
                  </div>
                  <time className="shrink-0 text-right text-[11px] text-slate-400">
                    {dateTime.format(new Date(activity.occurredAt))}
                  </time>
                </div>
              )
            })}
          </div>
        </section>
      </div>

      <section className="panel mt-6 overflow-hidden">
        <div className="flex items-center justify-between border-b px-5 py-4">
          <div>
            <h2 className="font-semibold">Failed ingestion jobs</h2>
            <p className="mt-1 text-xs text-slate-400">Most recent documents requiring attention</p>
          </div>
          <span className="rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-700">
            {analytics?.documentsByStatus.FAILED ?? 0} failed
          </span>
        </div>
        {!loading && !analytics?.failedIngestions.length
          ? <EmptyState>No failed ingestion jobs.</EmptyState>
          : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-400">
                  <tr>
                    <th className="px-5 py-3 font-medium">Document</th>
                    <th className="px-5 py-3 font-medium">Workspace</th>
                    <th className="px-5 py-3 font-medium">Owner</th>
                    <th className="px-5 py-3 font-medium">Error</th>
                    <th className="px-5 py-3 font-medium">Failed</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {analytics?.failedIngestions.map(job => (
                    <tr key={job.documentId} className="align-top">
                      <td className="max-w-[220px] px-5 py-4 font-medium"><p className="truncate">{job.documentName}</p></td>
                      <td className="px-5 py-4 text-slate-600">{job.workspaceName}</td>
                      <td className="px-5 py-4 text-slate-600">{job.ownerEmail}</td>
                      <td className="max-w-md px-5 py-4 text-xs leading-5 text-red-600">
                        <p className="line-clamp-2">{job.errorMessage || 'Ingestion failed without an error message.'}</p>
                      </td>
                      <td className="whitespace-nowrap px-5 py-4 text-xs text-slate-400">
                        <span className="inline-flex items-center gap-1.5"><Clock3 size={13} />{dateTime.format(new Date(job.failedAt))}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </section>
    </>
  )
}

import { FormEvent, ReactNode, useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { BookOpen, ChevronDown, FileText, LayoutDashboard, LogOut, Menu, MessageSquareText, Plus, Settings2, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useWorkspace } from '../context/WorkspaceContext'
import { errorMessage } from '../lib/api'
import Toast from './Toast'

const links = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/documents', label: 'Documents', icon: FileText },
  { to: '/chat', label: 'Ask knowledge', icon: MessageSquareText },
]

export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const { workspaces, activeWorkspaceId, selectWorkspace, createWorkspace, loading, error } = useWorkspace()
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [workspaceName, setWorkspaceName] = useState('')
  const [workspaceError, setWorkspaceError] = useState('')
  const [success, setSuccess] = useState('')
  const allLinks = user?.role === 'ADMIN'
    ? [...links, { to: '/admin', label: 'Administration', icon: Settings2 }]
    : links

  const create = async (event: FormEvent) => {
    event.preventDefault()
    if (!workspaceName.trim()) return
    try {
      await createWorkspace(workspaceName.trim())
      setWorkspaceName('')
      setCreating(false)
      setWorkspaceError('')
      setSuccess(`${workspaceName.trim()} workspace created.`)
    } catch (err) {
      setWorkspaceError(errorMessage(err))
    }
  }

  useEffect(() => {
    if (!success) return
    const timer = window.setTimeout(() => setSuccess(''), 4000)
    return () => window.clearTimeout(timer)
  }, [success])

  return (
    <div className="min-h-screen bg-canvas">
      <header className="fixed inset-x-0 top-0 z-30 flex h-16 items-center border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur-xl lg:hidden">
        <button aria-label="Open navigation" className="rounded-xl border bg-white p-2.5 text-slate-600 shadow-sm" onClick={() => setOpen(true)}><Menu size={19} /></button>
        <div className="ml-3 flex items-center gap-2.5 font-semibold"><span className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-violet-500 text-white shadow-sm"><BookOpen size={16} /></span>Nexus</div>
        <div className="ml-auto max-w-[42%] truncate rounded-full bg-slate-100 px-3 py-1.5 text-[11px] font-semibold text-slate-600">{workspaces.find(item => item.id === activeWorkspaceId)?.name}</div>
      </header>
      <Toast message={success} dismiss={() => setSuccess('')} />
      {open && <div className="fixed inset-0 z-40 bg-slate-950/40 lg:hidden" onClick={() => setOpen(false)} />}
      <aside className={`fixed inset-y-0 left-0 z-50 flex w-[280px] flex-col border-r border-white/[.06] bg-[#0b1020] px-4 py-5 text-white shadow-2xl transition-transform lg:translate-x-0 lg:shadow-none ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="mb-8 flex items-center gap-3 px-2">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 shadow-lg shadow-brand-950"><BookOpen size={20} /></div>
          <div><div className="font-semibold tracking-tight">Nexus</div><div className="text-[11px] text-slate-500">Enterprise knowledge</div></div>
          <button className="ml-auto lg:hidden" onClick={() => setOpen(false)}><X size={19} /></button>
        </div>
        <div className="mb-7 rounded-2xl border border-white/[.08] bg-white/[.045] p-3">
          <div className="mb-2 flex items-center justify-between px-1">
            <span className="text-[10px] font-semibold uppercase tracking-[.16em] text-slate-500">Workspace</span>
            <button aria-label="Create workspace" title="Create workspace" onClick={() => setCreating(value => !value)} className="rounded-lg p-1 text-slate-400 transition hover:bg-white/10 hover:text-white"><Plus size={14} /></button>
          </div>
          <div className="relative">
            <select aria-label="Active workspace" value={activeWorkspaceId || ''} disabled={loading || !workspaces.length} onChange={event => selectWorkspace(event.target.value)} className="w-full appearance-none rounded-xl border border-white/[.08] bg-slate-900/80 px-3 py-2.5 pr-8 text-xs font-medium text-white outline-none transition hover:border-white/15">
              {workspaces.map(workspace => <option key={workspace.id} value={workspace.id}>{workspace.name}{user?.role === 'ADMIN' ? ` · ${workspace.ownerEmail}` : ''}</option>)}
            </select>
            <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500" size={14} />
          </div>
          {creating && <form onSubmit={create} className="mt-2 space-y-2"><input autoFocus value={workspaceName} maxLength={120} onChange={event => setWorkspaceName(event.target.value)} placeholder="Workspace name" className="w-full rounded-lg border border-white/10 bg-slate-900 px-2.5 py-2 text-xs outline-none placeholder:text-slate-600" /><div className="flex gap-2"><button className="flex-1 rounded-lg bg-brand-500 px-2 py-1.5 text-xs font-semibold">Create</button><button type="button" onClick={() => setCreating(false)} className="rounded-lg px-2 py-1.5 text-xs text-slate-400">Cancel</button></div></form>}
          {(workspaceError || error) && <p className="mt-2 px-1 text-[11px] leading-4 text-red-300">{workspaceError || error}</p>}
        </div>
        <p className="mb-2 px-3 text-[10px] font-semibold uppercase tracking-[.16em] text-slate-600">Navigation</p>
        <nav className="space-y-1.5">
          {allLinks.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} onClick={() => setOpen(false)}
              className={({ isActive }) => `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${isActive ? 'bg-brand-500/15 text-white ring-1 ring-inset ring-brand-400/15' : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'}`}>
              <Icon size={17} className="transition group-hover:text-brand-300" />{label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto rounded-2xl border border-white/[.08] bg-white/[.045] p-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-violet-500 text-sm font-bold">{user?.name.slice(0, 1).toUpperCase()}</div>
            <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{user?.name}</div><div className="truncate text-[11px] text-slate-500">{user?.email}</div></div>
            <button title="Sign out" onClick={logout} className="rounded-lg p-1.5 text-slate-500 transition hover:bg-white/10 hover:text-white"><LogOut size={16} /></button>
          </div>
        </div>
      </aside>
      <main className="min-h-screen lg:pl-[280px]">
        <div className="mx-auto max-w-[1480px] px-4 pb-12 pt-20 sm:px-7 lg:px-9 lg:pt-8 xl:px-12">{children}</div>
      </main>
    </div>
  )
}

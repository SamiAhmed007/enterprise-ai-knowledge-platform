import { FormEvent, ReactNode, useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { BookOpen, FileText, LayoutDashboard, LogOut, Menu, MessageSquareText, Plus, Settings2, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useWorkspace } from '../context/WorkspaceContext'
import { errorMessage } from '../lib/api'
import Toast from './Toast'

const links = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
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
      <header className="fixed inset-x-0 top-0 z-30 flex h-16 items-center border-b bg-white/90 px-4 backdrop-blur lg:hidden">
        <button aria-label="Open navigation" className="rounded-xl border bg-white p-2" onClick={() => setOpen(true)}><Menu size={20} /></button>
        <div className="ml-3 flex items-center gap-2 font-semibold"><span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white"><BookOpen size={16} /></span>Nexus</div>
        <div className="ml-auto max-w-[42%] truncate text-xs font-medium text-slate-500">{workspaces.find(item => item.id === activeWorkspaceId)?.name}</div>
      </header>
      <Toast message={success} dismiss={() => setSuccess('')} />
      {open && <div className="fixed inset-0 z-40 bg-slate-950/40 lg:hidden" onClick={() => setOpen(false)} />}
      <aside className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-ink px-4 py-5 text-white transition-transform lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="mb-9 flex items-center gap-3 px-2">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-500"><BookOpen size={21} /></div>
          <div><div className="font-semibold">Nexus</div><div className="text-xs text-slate-400">Enterprise Knowledge</div></div>
          <button className="ml-auto lg:hidden" onClick={() => setOpen(false)}><X size={19} /></button>
        </div>
        <div className="mb-5 rounded-xl border border-white/10 bg-white/5 p-2.5">
          <div className="mb-1.5 flex items-center justify-between px-1">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Workspace</span>
            <button aria-label="Create workspace" title="Create workspace" onClick={() => setCreating(value => !value)} className="rounded p-1 text-slate-400 hover:bg-white/10 hover:text-white"><Plus size={14} /></button>
          </div>
          <select aria-label="Active workspace" value={activeWorkspaceId || ''} disabled={loading || !workspaces.length} onChange={event => selectWorkspace(event.target.value)} className="w-full rounded-lg border border-white/10 bg-slate-800 px-2.5 py-2 text-xs text-white outline-none">
            {workspaces.map(workspace => <option key={workspace.id} value={workspace.id}>{workspace.name}{user?.role === 'ADMIN' ? ` · ${workspace.ownerEmail}` : ''}</option>)}
          </select>
          {creating && <form onSubmit={create} className="mt-2 space-y-2"><input autoFocus value={workspaceName} maxLength={120} onChange={event => setWorkspaceName(event.target.value)} placeholder="Workspace name" className="w-full rounded-lg border border-white/10 bg-slate-900 px-2.5 py-2 text-xs outline-none placeholder:text-slate-600" /><div className="flex gap-2"><button className="flex-1 rounded-lg bg-brand-500 px-2 py-1.5 text-xs font-semibold">Create</button><button type="button" onClick={() => setCreating(false)} className="rounded-lg px-2 py-1.5 text-xs text-slate-400">Cancel</button></div></form>}
          {(workspaceError || error) && <p className="mt-2 px-1 text-[11px] leading-4 text-red-300">{workspaceError || error}</p>}
        </div>
        <nav className="space-y-1">
          {allLinks.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} onClick={() => setOpen(false)}
              className={({ isActive }) => `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-white'}`}>
              <Icon size={18} />{label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto rounded-xl border border-white/10 bg-white/5 p-3">
          <div className="flex items-center gap-3">
            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-500 text-sm font-bold">{user?.name.slice(0, 1).toUpperCase()}</div>
            <div className="min-w-0 flex-1"><div className="truncate text-sm font-medium">{user?.name}</div><div className="truncate text-xs text-slate-400">{user?.email}</div></div>
            <button title="Sign out" onClick={logout} className="text-slate-400 hover:text-white"><LogOut size={17} /></button>
          </div>
        </div>
      </aside>
      <main className="min-h-screen lg:pl-64">
        <div className="mx-auto max-w-7xl px-5 pb-12 pt-20 sm:px-8 lg:px-10 lg:pt-9">{children}</div>
      </main>
    </div>
  )
}

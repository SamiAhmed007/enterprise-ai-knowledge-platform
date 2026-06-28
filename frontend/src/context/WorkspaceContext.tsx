import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Workspace } from '../types'
import { api, errorMessage } from '../lib/api'
import { useAuth } from './AuthContext'

interface WorkspaceContextValue {
  workspaces: Workspace[]
  activeWorkspace: Workspace | null
  activeWorkspaceId: string | null
  loading: boolean
  error: string
  selectWorkspace: (id: string) => void
  createWorkspace: (name: string) => Promise<void>
  refreshWorkspaces: () => Promise<void>
}

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null)

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const storageKey = user ? `kp_workspace_${user.id}` : 'kp_workspace'

  const refreshWorkspaces = useCallback(async () => {
    if (!user) return
    setLoading(true)
    try {
      const items = (await api.get<Workspace[]>('/workspaces')).data
      setWorkspaces(items)
      setActiveWorkspaceId(current => {
        const stored = localStorage.getItem(storageKey)
        const preferred = current || stored
        return items.some(item => item.id === preferred) ? preferred : items[0]?.id || null
      })
      setError('')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [storageKey, user])

  useEffect(() => { void refreshWorkspaces() }, [refreshWorkspaces])
  useEffect(() => {
    if (activeWorkspaceId) localStorage.setItem(storageKey, activeWorkspaceId)
  }, [activeWorkspaceId, storageKey])

  const value = useMemo<WorkspaceContextValue>(() => ({
    workspaces,
    activeWorkspaceId,
    activeWorkspace: workspaces.find(item => item.id === activeWorkspaceId) || null,
    loading,
    error,
    selectWorkspace: setActiveWorkspaceId,
    createWorkspace: async (name) => {
      const created = (await api.post<Workspace>('/workspaces', { name })).data
      setWorkspaces(items => [...items, created])
      setActiveWorkspaceId(created.id)
      setError('')
    },
    refreshWorkspaces,
  }), [activeWorkspaceId, error, loading, refreshWorkspaces, workspaces])

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext)
  if (!context) throw new Error('useWorkspace must be used inside WorkspaceProvider')
  return context
}

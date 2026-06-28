export type Role = 'ADMIN' | 'USER'

export interface User {
  id: string
  name: string
  email: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Workspace {
  id: string
  name: string
  ownerId: string
  ownerName: string
  ownerEmail: string
  createdAt: string
  updatedAt: string
}

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'

export interface Document {
  id: string
  name: string
  contentType: string | null
  sizeBytes: number
  status: DocumentStatus
  errorMessage: string | null
  createdAt: string
  processedAt: string | null
  workspaceId: string
  workspaceName: string
  ownerName: string
  ownerEmail: string
}

export interface Citation {
  documentId: string
  documentName: string
  chunkIndex: number
  pageNumber: number | null
  excerpt: string
  score: number
  vectorScore: number
  keywordScore: number
  retrievalMethod: 'HYBRID'
}

export interface ChatMessage {
  id?: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: Citation[]
  createdAt?: string
  streaming?: boolean
}

export interface SessionSummary {
  id: string
  workspaceId: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface SessionDetail extends SessionSummary {
  messages: ChatMessage[]
}

export interface AskResponse {
  sessionId: string
  answer: string
  citations: Citation[]
}

export interface DashboardStats {
  users: number
  workspaces: number
  documents: number
  readyDocuments: number
  chatSessions: number
}

export type ActivityType =
  | 'USER_REGISTERED'
  | 'WORKSPACE_CREATED'
  | 'DOCUMENT_UPLOADED'
  | 'CHAT_STARTED'

export interface RecentActivity {
  type: ActivityType
  description: string
  actorName: string
  actorEmail: string
  occurredAt: string
}

export interface FailedIngestion {
  documentId: string
  documentName: string
  workspaceName: string
  ownerEmail: string
  errorMessage: string | null
  failedAt: string
}

export interface TokenUsage {
  embeddingTokens: number
  chatTokens: number
  totalTokens: number
  approximate: boolean
}

export interface AdminAnalytics {
  totalUsers: number
  totalWorkspaces: number
  totalDocuments: number
  documentsByStatus: Record<DocumentStatus, number>
  totalChatSessions: number
  recentActivity: RecentActivity[]
  failedIngestions: FailedIngestion[]
  tokenUsage: TokenUsage
}

export interface AdminUser extends User {
  createdAt: string
}

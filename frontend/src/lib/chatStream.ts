import { API_BASE_URL } from './api'
import { Citation } from '../types'

interface StreamRequest {
  sessionId: string | null
  question: string
}

interface StreamHandlers {
  onSources: (citations: Citation[]) => void
  onDelta: (content: string) => void
  onDone: (sessionId: string, citations: Citation[]) => void
}

export class ChatStreamError extends Error {
  constructor(message: string, readonly fallbackAllowed: boolean, readonly receivedDelta: boolean) {
    super(message)
    this.name = 'ChatStreamError'
  }
}

export async function streamChat(
  workspaceId: string,
  request: StreamRequest,
  signal: AbortSignal,
  handlers: StreamHandlers,
): Promise<void> {
  const token = localStorage.getItem('kp_token')
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/workspaces/${workspaceId}/chats/stream`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(request),
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ChatStreamError('Could not connect to the streaming endpoint', true, false)
  }

  if (response.status === 401) {
    localStorage.removeItem('kp_token')
    localStorage.removeItem('kp_user')
    window.location.assign('/login')
    throw new ChatStreamError('Your session has expired', false, false)
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string, correlationId?: string } | null
    const fallbackAllowed = [404, 405, 406, 415, 501].includes(response.status)
    const message = body?.message || `Streaming request failed (${response.status})`
    throw new ChatStreamError(
      body?.correlationId ? `${message} · Support ID: ${body.correlationId}` : message,
      fallbackAllowed,
      false,
    )
  }
  if (!response.body) {
    throw new ChatStreamError('Streaming is not supported by this browser', true, false)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let receivedDelta = false
  let completed = false

  const dispatchBlock = (block: string) => {
    const event = parseEvent(block)
    if (!event) return
    if (event.name === 'sources') {
      handlers.onSources((JSON.parse(event.data) as { citations: Citation[] }).citations)
    } else if (event.name === 'delta') {
      const content = (JSON.parse(event.data) as { content: string }).content
      if (content) {
        receivedDelta = true
        handlers.onDelta(content)
      }
    } else if (event.name === 'done') {
      const result = JSON.parse(event.data) as { sessionId: string, citations: Citation[] }
      completed = true
      handlers.onDone(result.sessionId, result.citations)
    } else if (event.name === 'error') {
      const result = JSON.parse(event.data) as { message?: string, correlationId?: string }
      const message = result.message || 'The streamed response failed'
      throw new ChatStreamError(
        result.correlationId ? `${message} · Support ID: ${result.correlationId}` : message,
        false,
        receivedDelta,
      )
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer = (buffer + decoder.decode(value || new Uint8Array(), { stream: !done }))
        .replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        dispatchBlock(block)
        boundary = buffer.indexOf('\n\n')
      }
      if (done) break
    }
    if (buffer.trim()) dispatchBlock(buffer)
  } catch (error) {
    if (isAbortError(error) || error instanceof ChatStreamError) throw error
    throw new ChatStreamError('The response stream ended unexpectedly', !receivedDelta, receivedDelta)
  } finally {
    reader.releaseLock()
  }

  if (!completed) {
    throw new ChatStreamError('The response stream ended before completion', !receivedDelta, receivedDelta)
  }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function parseEvent(block: string): { name: string, data: string } | null {
  let name = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  return data.length ? { name, data: data.join('\n') } : null
}

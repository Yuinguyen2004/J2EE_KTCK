import { useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import { customerPortalService, type ChatConversation, type StaffChatConversationSummary } from '../services/customerPortalService'
import type { User } from '../services/authService'
import type { AppPage } from '../utils/navigation'
import '../styles/customer-portal.css'

interface StaffChatInboxProps {
  onLogout?: () => void
  onNavigate?: (page: AppPage) => void
  user?: User | null
}

export default function StaffChatInbox({
  onLogout = () => {},
  onNavigate = () => {},
  user,
}: StaffChatInboxProps) {
  const [conversations, setConversations] = useState<StaffChatConversationSummary[]>([])
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null)
  const [selectedConversation, setSelectedConversation] = useState<ChatConversation | null>(null)
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadInbox = async (conversationId?: string | null) => {
    const nextConversations = await customerPortalService.getStaffConversations()
    setConversations(nextConversations)

    const targetConversationId = conversationId
      || selectedConversationId
      || nextConversations[0]?.id
      || null

    setSelectedConversationId(targetConversationId)

    if (targetConversationId) {
      const detail = await customerPortalService.getStaffConversation(targetConversationId)
      setSelectedConversation(detail)
      return
    }

    setSelectedConversation(null)
  }

  useEffect(() => {
    let cancelled = false

    void customerPortalService.getStaffConversations()
      .then(async (nextConversations) => {
        if (cancelled) {
          return
        }

        setConversations(nextConversations)
        const targetConversationId = nextConversations[0]?.id || null
        setSelectedConversationId(targetConversationId)
        if (!targetConversationId) {
          setSelectedConversation(null)
          return
        }

        const detail = await customerPortalService.getStaffConversation(targetConversationId)
        if (!cancelled) {
          setSelectedConversation(detail)
        }
      })
      .catch((loadError: unknown) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Could not load customer chat inbox.')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    const intervalId = window.setInterval(() => {
      void customerPortalService.getStaffConversations()
        .then(async (nextConversations) => {
          if (cancelled) {
            return
          }

          setConversations(nextConversations)
          const targetConversationId = selectedConversationId || nextConversations[0]?.id || null
          setSelectedConversationId(targetConversationId)
          if (!targetConversationId) {
            setSelectedConversation(null)
            return
          }

          const detail = await customerPortalService.getStaffConversation(targetConversationId)
          if (!cancelled) {
            setSelectedConversation(detail)
          }
        })
        .catch(() => {})
    }, 10000)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [selectedConversationId])

  const handleSend = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!selectedConversationId || !draft.trim()) {
      return
    }

    try {
      await customerPortalService.sendStaffMessage(selectedConversationId, draft.trim())
      setDraft('')
      setError('')
      await loadInbox(selectedConversationId)
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : 'Could not send the reply.')
    }
  }

  return (
    <MainLayout
      currentPage="staff-chat"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'Staff'}
      userRole={user?.role || 'staff'}
    >
      <div className="portal-stack">
        <section className="portal-panel portal-inbox-layout">
          <aside className="portal-inbox-list">
            <div className="portal-section-header">
              <div>
                <p className="portal-eyebrow">Customer Chat</p>
                <h1>Shop Inbox</h1>
              </div>
            </div>

            {loading ? (
              <div className="portal-empty-state">Loading inbox...</div>
            ) : conversations.length === 0 ? (
              <div className="portal-empty-state">No customer conversations yet.</div>
            ) : (
              conversations.map((conversation) => (
                <button
                  key={conversation.id}
                  type="button"
                  className={`portal-inbox-item ${selectedConversationId === conversation.id ? 'is-active' : ''}`}
                  onClick={() => {
                    setSelectedConversationId(conversation.id)
                    void loadInbox(conversation.id).catch(() => {})
                  }}
                >
                  <strong>{conversation.customerName}</strong>
                  <span>{conversation.customerEmail}</span>
                  <p>{conversation.latestMessagePreview || 'No message preview available.'}</p>
                </button>
              ))
            )}
          </aside>

          <div className="portal-chat-column">
            {selectedConversation ? (
              <>
                <div className="portal-chat-thread">
                  {selectedConversation.messages.map((message) => (
                    <article
                      key={message.id}
                      className={`portal-chat-bubble ${message.senderRole === 'STAFF' ? 'is-self' : 'is-staff'}`}
                    >
                      <header>
                        <strong>{message.senderName}</strong>
                        <span>{new Date(message.sentAt).toLocaleString()}</span>
                      </header>
                      <p>{message.content}</p>
                    </article>
                  ))}
                </div>

                <form className="portal-chat-form" onSubmit={handleSend}>
                  <textarea
                    rows={4}
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    placeholder="Reply to the customer..."
                  />
                  <div className="portal-actions">
                    <button type="submit" className="portal-button portal-button-primary">Send Reply</button>
                  </div>
                </form>
              </>
            ) : (
              <div className="portal-empty-state">Select a conversation to read and reply.</div>
            )}
          </div>
        </section>

        {error && <div className="portal-error">{error}</div>}
      </div>
    </MainLayout>
  )
}

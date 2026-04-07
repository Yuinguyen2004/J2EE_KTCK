import { useEffect, useState } from 'react'
import MainLayout from '../components/MainLayout'
import { customerPortalService, type ChatConversation } from '../services/customerPortalService'
import type { User } from '../services/authService'
import type { AppPage } from '../utils/navigation'
import '../styles/customer-portal.css'

interface CustomerChatProps {
  onLogout?: () => void
  onNavigate?: (page: AppPage) => void
  user?: User | null
}

export default function CustomerChat({
  onLogout = () => {},
  onNavigate = () => {},
  user,
}: CustomerChatProps) {
  const [conversation, setConversation] = useState<ChatConversation | null>(null)
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    void customerPortalService.getConversation()
      .then((nextConversation) => {
        if (!cancelled) {
          setConversation(nextConversation)
        }
      })
      .catch((loadError: unknown) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Could not load chat.')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    const intervalId = window.setInterval(() => {
      void customerPortalService.getConversation()
        .then((nextConversation) => {
          if (!cancelled) {
            setConversation(nextConversation)
          }
        })
        .catch(() => {})
    }, 10000)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [])

  const handleSend = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!draft.trim()) {
      return
    }

    try {
      await customerPortalService.sendMessage(draft.trim())
      setDraft('')
      setError('')
      const nextConversation = await customerPortalService.getConversation()
      setConversation(nextConversation)
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : 'Could not send your message.')
    }
  }

  return (
    <MainLayout
      currentPage="customer-chat"
      onNavigate={onNavigate}
      onLogout={onLogout}
      user={user}
      userName={user?.fullName || 'Customer'}
      userRole={user?.role || 'customer'}
    >
      <div className="portal-stack">
        <section className="portal-panel portal-chat-panel">
          <div className="portal-section-header">
            <div>
              <p className="portal-eyebrow">Chat</p>
              <h1>Message the shop</h1>
              <p className="portal-muted">Any staff member can pick up the conversation and reply here.</p>
            </div>
          </div>

          {loading ? (
            <div className="portal-empty-state">Loading conversation...</div>
          ) : (
            <>
              <div className="portal-chat-thread">
                {conversation?.messages.length ? conversation.messages.map((message) => (
                  <article
                    key={message.id}
                    className={`portal-chat-bubble ${message.senderRole === 'CUSTOMER' ? 'is-self' : 'is-staff'}`}
                  >
                    <header>
                      <strong>{message.senderName}</strong>
                      <span>{new Date(message.sentAt).toLocaleString()}</span>
                    </header>
                    <p>{message.content}</p>
                  </article>
                )) : (
                  <div className="portal-empty-state">No messages yet. Start the conversation with the shop.</div>
                )}
              </div>

              <form className="portal-chat-form" onSubmit={handleSend}>
                <textarea
                  rows={4}
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="Ask about table timing, group size, or anything else before you visit."
                />
                <div className="portal-actions">
                  <button type="submit" className="portal-button portal-button-primary">Send Message</button>
                </div>
              </form>
            </>
          )}

          {error && <div className="portal-error">{error}</div>}
        </section>
      </div>
    </MainLayout>
  )
}

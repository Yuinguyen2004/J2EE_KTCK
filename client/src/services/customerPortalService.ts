import api from './api'
import { readPageItems, toNumber, type PageResponse } from './server'

type ServerTableStatus = 'AVAILABLE' | 'IN_USE' | 'PAUSED' | 'RESERVED' | 'MAINTENANCE'
type ServerReservationStatus = 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED'
type ServerChatSenderRole = 'CUSTOMER' | 'STAFF'

interface ServerCustomerTable {
  id: number
  name: string
  tableTypeId: number
  tableTypeName: string
  status: ServerTableStatus
  pricePerHour: number | string | null
}

interface ServerPricingPreview {
  tableTypeId: number
  tableTypeName: string
  durationMinutes: number
  membershipDiscountPercent: number | string
  membershipTierName: string | null
  grossAmount: number | string
  discountAmount: number | string
  estimatedTotal: number | string
}

interface ServerMenuItem {
  id: number
  name: string
  description: string | null
  price: number | string
  imageUrl: string | null
}

interface ServerReservation {
  id: number
  tableId: number | null
  tableName: string | null
  tableStatus: ServerTableStatus | null
  status: ServerReservationStatus
  reservedFrom: string
  reservedTo: string
  partySize: number
  notes: string | null
}

interface ServerChatMessage {
  id: number
  conversationId: number
  senderRole: ServerChatSenderRole
  senderName: string
  content: string
  sentAt: string
}

interface ServerChatConversation {
  id: number | null
  customerId: number
  customerName: string
  customerEmail: string
  lastMessageAt: string | null
  messages: ServerChatMessage[]
}

interface ServerStaffConversationSummary {
  id: number
  customerId: number
  customerName: string
  customerEmail: string
  lastMessageAt: string
  latestMessagePreview: string | null
}

export type CustomerTableStatus = 'available' | 'playing' | 'reserved' | 'maintenance'
export type CustomerReservationStatus = ServerReservationStatus
export type ChatSenderRole = ServerChatSenderRole

export interface CustomerTable {
  id: string
  name: string
  tableTypeId: string
  tableTypeName: string
  status: CustomerTableStatus
  pricePerHour?: number
}

export interface PricingPreview {
  tableTypeId: string
  tableTypeName: string
  durationMinutes: number
  membershipDiscountPercent: number
  membershipTierName?: string
  grossAmount: number
  discountAmount: number
  estimatedTotal: number
}

export interface CustomerMenuItem {
  id: string
  name: string
  description?: string
  price: number
  imageUrl?: string
}

export interface CustomerReservation {
  id: string
  tableId?: string
  tableName?: string
  tableStatus?: CustomerTableStatus
  status: CustomerReservationStatus
  reservedFrom: string
  reservedTo: string
  partySize: number
  notes?: string
}

export interface ChatMessage {
  id: string
  conversationId: string
  senderRole: ChatSenderRole
  senderName: string
  content: string
  sentAt: string
}

export interface ChatConversation {
  id?: string
  customerId: string
  customerName: string
  customerEmail: string
  lastMessageAt?: string
  messages: ChatMessage[]
}

export interface StaffChatConversationSummary {
  id: string
  customerId: string
  customerName: string
  customerEmail: string
  lastMessageAt: string
  latestMessagePreview?: string
}

export interface ReservationDraft {
  reservedFrom: string
  reservedTo: string
  partySize: number
  notes?: string
}

const PAGE_SIZE = 100

const toClientTableStatus = (status: ServerTableStatus | null | undefined): CustomerTableStatus | undefined => {
  if (!status) {
    return undefined
  }

  switch (status) {
    case 'IN_USE':
    case 'PAUSED':
      return 'playing'
    case 'RESERVED':
      return 'reserved'
    case 'MAINTENANCE':
      return 'maintenance'
    default:
      return 'available'
  }
}

const mapChatMessage = (message: ServerChatMessage): ChatMessage => ({
  id: String(message.id),
  conversationId: String(message.conversationId),
  senderRole: message.senderRole,
  senderName: message.senderName,
  content: message.content,
  sentAt: message.sentAt,
})

export const customerPortalService = {
  async getTables(): Promise<CustomerTable[]> {
    const { data } = await api.get<PageResponse<ServerCustomerTable>>('/customer/tables', {
      params: { size: PAGE_SIZE },
    })

    return readPageItems(data).map((table) => ({
      id: String(table.id),
      name: table.name,
      tableTypeId: String(table.tableTypeId),
      tableTypeName: table.tableTypeName,
      status: toClientTableStatus(table.status) ?? 'available',
      pricePerHour: table.pricePerHour == null ? undefined : toNumber(table.pricePerHour),
    }))
  },

  async getPricingPreview(tableTypeId: string, durationMinutes: number): Promise<PricingPreview> {
    const { data } = await api.get<ServerPricingPreview>('/customer/tables/pricing-preview', {
      params: {
        tableTypeId,
        durationMinutes,
      },
    })

    return {
      tableTypeId: String(data.tableTypeId),
      tableTypeName: data.tableTypeName,
      durationMinutes: data.durationMinutes,
      membershipDiscountPercent: toNumber(data.membershipDiscountPercent),
      membershipTierName: data.membershipTierName || undefined,
      grossAmount: toNumber(data.grossAmount),
      discountAmount: toNumber(data.discountAmount),
      estimatedTotal: toNumber(data.estimatedTotal),
    }
  },

  async getMenuItems(): Promise<CustomerMenuItem[]> {
    const { data } = await api.get<PageResponse<ServerMenuItem>>('/customer/menu-items', {
      params: { size: PAGE_SIZE },
    })

    return readPageItems(data).map((item) => ({
      id: String(item.id),
      name: item.name,
      description: item.description || undefined,
      price: toNumber(item.price),
      imageUrl: item.imageUrl || undefined,
    }))
  },

  async getReservations(): Promise<CustomerReservation[]> {
    const { data } = await api.get<PageResponse<ServerReservation>>('/customer/reservations', {
      params: {
        size: PAGE_SIZE,
        sortBy: 'reservedFrom',
        direction: 'desc',
      },
    })

    return readPageItems(data).map((reservation) => ({
      id: String(reservation.id),
      tableId: reservation.tableId == null ? undefined : String(reservation.tableId),
      tableName: reservation.tableName || undefined,
      tableStatus: toClientTableStatus(reservation.tableStatus),
      status: reservation.status,
      reservedFrom: reservation.reservedFrom,
      reservedTo: reservation.reservedTo,
      partySize: reservation.partySize,
      notes: reservation.notes || undefined,
    }))
  },

  async createReservation(draft: ReservationDraft): Promise<CustomerReservation> {
    const { data } = await api.post<ServerReservation>('/customer/reservations', {
      reservedFrom: draft.reservedFrom,
      reservedTo: draft.reservedTo,
      partySize: draft.partySize,
      notes: draft.notes?.trim() || null,
    })

    return {
      id: String(data.id),
      tableId: data.tableId == null ? undefined : String(data.tableId),
      tableName: data.tableName || undefined,
      tableStatus: toClientTableStatus(data.tableStatus),
      status: data.status,
      reservedFrom: data.reservedFrom,
      reservedTo: data.reservedTo,
      partySize: data.partySize,
      notes: data.notes || undefined,
    }
  },

  async updateReservation(id: string, draft: ReservationDraft): Promise<CustomerReservation> {
    const { data } = await api.put<ServerReservation>(`/customer/reservations/${id}`, {
      reservedFrom: draft.reservedFrom,
      reservedTo: draft.reservedTo,
      partySize: draft.partySize,
      notes: draft.notes?.trim() || null,
    })

    return {
      id: String(data.id),
      tableId: data.tableId == null ? undefined : String(data.tableId),
      tableName: data.tableName || undefined,
      tableStatus: toClientTableStatus(data.tableStatus),
      status: data.status,
      reservedFrom: data.reservedFrom,
      reservedTo: data.reservedTo,
      partySize: data.partySize,
      notes: data.notes || undefined,
    }
  },

  async cancelReservation(id: string): Promise<void> {
    await api.delete(`/customer/reservations/${id}`)
  },

  async getConversation(): Promise<ChatConversation> {
    const { data } = await api.get<ServerChatConversation>('/customer/chat')
    return {
      id: data.id == null ? undefined : String(data.id),
      customerId: String(data.customerId),
      customerName: data.customerName,
      customerEmail: data.customerEmail,
      lastMessageAt: data.lastMessageAt || undefined,
      messages: data.messages.map(mapChatMessage),
    }
  },

  async sendMessage(content: string): Promise<ChatMessage> {
    const { data } = await api.post<ServerChatMessage>('/customer/chat/messages', {
      content,
    })

    return mapChatMessage(data)
  },

  async getStaffConversations(): Promise<StaffChatConversationSummary[]> {
    const { data } = await api.get<ServerStaffConversationSummary[]>('/staff/chat/conversations')
    return data.map((conversation) => ({
      id: String(conversation.id),
      customerId: String(conversation.customerId),
      customerName: conversation.customerName,
      customerEmail: conversation.customerEmail,
      lastMessageAt: conversation.lastMessageAt,
      latestMessagePreview: conversation.latestMessagePreview || undefined,
    }))
  },

  async getStaffConversation(conversationId: string): Promise<ChatConversation> {
    const { data } = await api.get<ServerChatConversation>(`/staff/chat/conversations/${conversationId}`)
    return {
      id: data.id == null ? undefined : String(data.id),
      customerId: String(data.customerId),
      customerName: data.customerName,
      customerEmail: data.customerEmail,
      lastMessageAt: data.lastMessageAt || undefined,
      messages: data.messages.map(mapChatMessage),
    }
  },

  async sendStaffMessage(conversationId: string, content: string): Promise<ChatMessage> {
    const { data } = await api.post<ServerChatMessage>(`/staff/chat/conversations/${conversationId}/messages`, {
      content,
    })

    return mapChatMessage(data)
  },
}

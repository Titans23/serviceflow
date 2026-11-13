export type ReturnRequest = {
  publicId: string
  orderNo: string
  productName: string
  amount: number
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED'
  reason: string
  reviewNote: string | null
  receiptNote: string | null
  createdAt: string
  reviewedAt: string | null
  receivedAt: string | null
  refundStatus: string | null
}

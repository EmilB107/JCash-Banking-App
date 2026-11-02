/** Body of POST /api/transfers (com.jcash.app.transfers.dto.TransferRequest). */
export interface TransferRequest {
  /** Email or mobile number (no spaces) of the recipient. */
  recipient: string;
  amount: number;
}

/** Mirrors com.jcash.app.transfers.dto.TransferResponse. */
export interface TransferResponse {
  senderAccountId: string;
  recipientAccountId: string;
  recipientEmail: string;
  amount: number;
  currency: string;
  senderBalanceAfter: number;
  recipientBalanceAfter: number;
}

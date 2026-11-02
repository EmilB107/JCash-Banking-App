/** Mirrors com.jcash.app.transactions.dto.TransactionEntryResponse. */
export interface TransactionEntry {
  id: string;
  account: string;
  transactionType: 'CASH_IN' | 'TRANSFER';
  /** CREDIT = money in, DEBIT = money out. */
  direction: 'CREDIT' | 'DEBIT';
  amount: number;
  currency: string;
  balanceAfter: number;
  /** Human-readable label from the backend, e.g. "Cash in" or "Transfer to ...". */
  details: string;
  /** Shared by the two entries (sender and recipient) of one transfer; null for cash-in. */
  transferReference: string | null;
  createdAt: string;
}

/** Mirrors com.jcash.app.transactions.dto.TransactionHistoryPage. `page` is zero-based. */
export interface TransactionHistoryPage {
  content: TransactionEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

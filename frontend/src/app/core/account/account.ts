/**
 * Mirrors com.jcash.app.accounts.dto.AccountResponse.
 *
 * `balance` is a Java BigDecimal sent as a JSON number. That's safe to display,
 * but never do money math on it in the browser: the backend owns the balance.
 */
export interface Account {
  id: string;
  currency: string;
  balance: number;
  createdAt: string;
  updatedAt: string;
}

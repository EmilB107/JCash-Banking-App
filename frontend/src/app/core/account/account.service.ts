import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Account } from './account';

/** The signed-in user's wallet account. Errors surface as HttpErrorResponse; convert with toApiError. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  getMyAccount(): Observable<Account> {
    return this.http.get<Account>('/api/accounts/me');
  }

  /** Adds demo funds. Responds with the account after the credit, so the new balance comes from the server. */
  cashIn(amount: number): Observable<Account> {
    return this.http.post<Account>('/api/accounts/me/cash-in', { amount });
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TransactionHistoryPage } from './transaction';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);

  /** Newest first. `page` is zero-based; the backend caps `size`. */
  getMyHistory(page = 0, size = 20): Observable<TransactionHistoryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<TransactionHistoryPage>('/api/transactions/me', { params });
  }
}

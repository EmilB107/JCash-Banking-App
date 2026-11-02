import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { TransactionEntry, TransactionHistoryPage } from '../../core/transactions/transaction';
import { TransactionService } from '../../core/transactions/transaction.service';
import { History, PAGE_SIZE } from './history';

function entry(overrides: Partial<TransactionEntry> = {}): TransactionEntry {
  return {
    id: 'e1',
    account: 'a1',
    transactionType: 'TRANSFER',
    direction: 'DEBIT',
    amount: 200,
    currency: 'PHP',
    balanceAfter: 1300,
    details: 'Transfer to Juan',
    transferReference: 'ref-123',
    createdAt: '2026-11-01T01:00:00Z',
    ...overrides,
  };
}

function page(
  content: TransactionEntry[],
  number: number,
  totalElements: number,
): TransactionHistoryPage {
  return {
    content,
    page: number,
    size: PAGE_SIZE,
    totalElements,
    totalPages: Math.ceil(totalElements / PAGE_SIZE),
  };
}

describe('History', () => {
  const getMyHistory = vi.fn();

  async function open(url: string) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'history', component: History }]),
        { provide: TransactionService, useValue: { getMyHistory } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.detectChanges();
    return { harness, el: harness.routeNativeElement! };
  }

  beforeEach(() => {
    localStorage.clear();
    getMyHistory.mockReset();
  });

  it('loads the page from the URL and links to its neighbours', async () => {
    getMyHistory.mockReturnValue(of(page([entry()], 1, 45)));
    const { el } = await open('/history?page=2');

    expect(getMyHistory).toHaveBeenCalledWith(1, PAGE_SIZE); // API pages start at 0
    expect(el.textContent).toContain('Page 2 of 3 · 45 transactions');
    expect(el.querySelector('a[href="/history"]')!.textContent).toContain('Previous');
    expect(el.querySelector('a[href="/history?page=3"]')!.textContent).toContain('Next');
  });

  it('treats an invalid page number as page 1', async () => {
    getMyHistory.mockReturnValue(of(page([entry()], 0, 1)));
    await open('/history?page=abc');

    expect(getMyHistory).toHaveBeenCalledWith(0, PAGE_SIZE);
  });

  it('expands a transaction to show its details', async () => {
    getMyHistory.mockReturnValue(of(page([entry()], 0, 1)));
    const { harness, el } = await open('/history');
    const row = el.querySelector<HTMLButtonElement>('button[aria-expanded]')!;

    expect(row.getAttribute('aria-expanded')).toBe('false');
    row.click();
    harness.detectChanges();

    expect(row.getAttribute('aria-expanded')).toBe('true');
    expect(el.textContent).toContain('Transfer · Money out');
    expect(el.textContent).toContain('ref-123');
  });

  it('shows an empty state for a new wallet', async () => {
    getMyHistory.mockReturnValue(of(page([], 0, 0)));
    const { el } = await open('/history');

    expect(el.textContent).toContain('No transactions yet.');
    expect(el.querySelector('nav[aria-label=Pagination]')).toBeNull();
  });

  it('offers a way back from a page past the end', async () => {
    getMyHistory.mockReturnValue(of(page([], 98, 5)));
    const { el } = await open('/history?page=99');

    expect(el.textContent).toContain("There's nothing on this page.");
  });

  it('shows an error and retries the same page', async () => {
    getMyHistory.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const { harness, el } = await open('/history?page=2');
    expect(el.querySelector('[role=alert]')!.textContent).toContain('Cannot reach the server');

    getMyHistory.mockReturnValue(of(page([entry()], 1, 45)));
    el.querySelector<HTMLButtonElement>('[role=alert] button')!.click();
    harness.detectChanges();

    expect(getMyHistory).toHaveBeenLastCalledWith(1, PAGE_SIZE);
    expect(el.textContent).toContain('Transfer to Juan');
  });
});

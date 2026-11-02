import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Account } from '../../core/account/account';
import { AccountService } from '../../core/account/account.service';
import { TransactionEntry, TransactionHistoryPage } from '../../core/transactions/transaction';
import { TransactionService } from '../../core/transactions/transaction.service';
import { Dashboard } from './dashboard';

const ACCOUNT: Account = {
  id: 'a1',
  currency: 'PHP',
  balance: 1500,
  createdAt: '2026-10-31T00:00:00Z',
  updatedAt: '2026-10-31T00:00:00Z',
};

function entry(overrides: Partial<TransactionEntry>): TransactionEntry {
  return {
    id: 'e1',
    account: 'a1',
    transactionType: 'CASH_IN',
    direction: 'CREDIT',
    amount: 1500,
    currency: 'PHP',
    balanceAfter: 1500,
    details: 'Cash in',
    transferReference: null,
    createdAt: '2026-11-01T01:00:00Z',
    ...overrides,
  };
}

function page(content: TransactionEntry[]): TransactionHistoryPage {
  return { content, page: 0, size: 5, totalElements: content.length, totalPages: 1 };
}

describe('Dashboard', () => {
  const getMyAccount = vi.fn();
  const getMyHistory = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: { getMyAccount } },
        { provide: TransactionService, useValue: { getMyHistory } },
      ],
    });
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
    getMyAccount.mockReturnValue(of(ACCOUNT));
  });

  it('shows the balance and recent activity', () => {
    getMyHistory.mockReturnValue(
      of(
        page([
          entry({
            id: 'e2',
            transactionType: 'TRANSFER',
            direction: 'DEBIT',
            amount: 200,
            balanceAfter: 1300,
            details: 'Transfer to Juan',
          }),
          entry({}),
        ]),
      ),
    );
    const { el } = setup();

    expect(getMyHistory).toHaveBeenCalledWith(0, 5);
    expect(el.querySelector('[data-testid=balance]')!.textContent).toContain('₱1,500.00');
    expect(el.textContent).toContain('Transfer to Juan');
    expect(el.textContent).toContain('−₱200.00');
    expect(el.textContent).toContain('+₱1,500.00');
  });

  it('shows an empty state for a new wallet', () => {
    getMyHistory.mockReturnValue(of(page([])));
    const { el } = setup();

    expect(el.textContent).toContain('No transactions yet.');
  });

  it('shows a retryable error when loading fails', () => {
    getMyHistory.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const { el } = setup();

    expect(el.querySelector('[role=alert]')!.textContent).toContain('Cannot reach the server');
    expect(el.textContent).toContain('Try again');
  });

  it('links to the cash-in and send money pages', () => {
    getMyHistory.mockReturnValue(of(page([])));
    const { el } = setup();

    expect(el.querySelector('a[href="/cash-in"]')!.textContent).toContain('Cash in');
    expect(el.querySelector('a[href="/send"]')!.textContent).toContain('Send money');
  });

  it('hides and shows the balance, including balances in recent activity', () => {
    getMyHistory.mockReturnValue(of(page([entry({})])));
    const { fixture, el } = setup();
    const balance = () => el.querySelector('[data-testid=balance]')!.textContent!;
    const toggle = () => {
      el.querySelector<HTMLButtonElement>('app-balance-toggle button')!.click();
      fixture.detectChanges();
    };

    toggle();
    expect(balance()).toContain('₱••••••');
    expect(el.textContent).not.toContain('Balance ₱1,500.00');
    expect(el.querySelector('[aria-label="Show balance"]')).not.toBeNull();

    toggle();
    expect(balance()).toContain('₱1,500.00');
  });
});

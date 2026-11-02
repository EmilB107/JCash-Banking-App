import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Account } from '../../core/account/account';
import { AccountService } from '../../core/account/account.service';
import { CashIn } from './cash-in';

const ACCOUNT: Account = {
  id: 'a1',
  currency: 'PHP',
  balance: 2000,
  createdAt: '2026-10-31T00:00:00Z',
  updatedAt: '2026-10-31T00:00:00Z',
};

describe('CashIn', () => {
  const cashIn = vi.fn();
  const getMyAccount = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [CashIn],
      providers: [{ provide: AccountService, useValue: { cashIn, getMyAccount } }],
    });
    const fixture = TestBed.createComponent(CashIn);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const typeAmount = (value: string) => {
      const input = el.querySelector<HTMLInputElement>('#cash-in-amount')!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { fixture, el, typeAmount, submit };
  }

  beforeEach(() => {
    vi.resetAllMocks();
    getMyAccount.mockReturnValue(of({ ...ACCOUNT, balance: 0 }));
  });

  it('shows a zero balance as ₱0.00', () => {
    const { el } = setup();
    expect(el.querySelector('[data-testid=balance]')!.textContent).toContain('₱0.00');
  });

  it("cashes in, confirms, and shows the server's new balance", () => {
    cashIn.mockReturnValue(of(ACCOUNT));
    const { el, submit } = setup();

    [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === '₱500')!.click();
    submit();

    expect(cashIn).toHaveBeenCalledWith(500);
    expect(el.querySelector('[role=status]')!.textContent).toContain('Added ₱500.00');
    expect(el.querySelector('[data-testid=balance]')!.textContent).toContain('₱2,000.00');
  });

  it('rejects invalid amounts without calling the API', () => {
    const { el, typeAmount, submit } = setup();

    for (const [value, message] of [
      ['', 'Enter an amount.'],
      ['12.345', 'up to 2 decimal places'],
      ['0', 'Enter at least ₱0.01.'],
      ['100001', 'The most you can enter is ₱100,000.'],
    ]) {
      typeAmount(value);
      submit();
      expect(el.textContent).toContain(message);
    }
    expect(cashIn).not.toHaveBeenCalled();
  });
});

import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TransferResponse } from '../../core/transfers/transfer';
import { TransferService } from '../../core/transfers/transfer.service';
import { AccountService } from '../../core/account/account.service';
import { SendMoney } from './send-money';

const RESULT: TransferResponse = {
  senderAccountId: 'a1',
  recipientAccountId: 'a2',
  recipientEmail: 'juan@example.com',
  amount: 250,
  currency: 'PHP',
  senderBalanceAfter: 1250,
  recipientBalanceAfter: 250,
};

function apiError(status: number, error: string, message = error) {
  return throwError(() => new HttpErrorResponse({ status, error: { error, message } }));
}

describe('SendMoney', () => {
  const send = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      imports: [SendMoney],
      providers: [
        { provide: TransferService, useValue: { send } },
        { provide: AccountService, useValue: { getMyAccount: () => of({ balance: 1500 }) } },
      ],
    });
    const fixture = TestBed.createComponent(SendMoney);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const review = (recipient: string, amount: string) => {
      type('send-recipient', recipient);
      type('send-amount', amount);
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    const click = (label: string) => {
      [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === label)!.click();
      fixture.detectChanges();
    };
    return { fixture, el, review, click };
  }

  beforeEach(() => send.mockReset());

  it('reviews, then sends a mobile number without spaces and shows the new balance', () => {
    send.mockReturnValue(of(RESULT));
    const { el, review, click } = setup();
    expect(el.querySelector('[data-testid=balance]')!.textContent).toContain('₱1,500.00');

    review('09171234567', '250');
    expect(send).not.toHaveBeenCalled();
    expect(el.textContent).toContain('0917 123 4567');
    expect(el.textContent).toContain('₱250.00');

    click('Send');
    expect(send).toHaveBeenCalledWith(
      { recipient: '09171234567', amount: 250 },
      expect.any(String),
    );
    expect(el.querySelector('[role=status]')!.textContent).toContain(
      'Sent ₱250.00 to juan@example.com',
    );
    expect(el.querySelector('[data-testid=balance]')!.textContent).toContain('₱1,250.00');
  });

  it('blocks amounts above the balance before calling the API', () => {
    const { el, review } = setup();
    review('juan@example.com', '2000');

    expect(el.textContent).toContain("You don't have enough balance");
    expect(el.textContent).not.toContain('Edit');
  });

  it('reuses the idempotency key when retrying the same transfer, and not after edits', () => {
    send.mockReturnValue(
      apiError(409, 'LOCK_CONFLICT', 'Account is busy. Please retry the transfer'),
    );
    const { el, review, click } = setup();

    review('juan@example.com', '250');
    click('Send');
    expect(el.querySelector('[role=alert]')!.textContent).toContain('Account is busy');

    review('juan@example.com', '250');
    click('Send');
    review('juan@example.com', '300');
    click('Send');

    const keys = send.mock.calls.map(([, key]) => key);
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it('explains an unknown recipient', () => {
    send.mockReturnValue(apiError(404, 'RESOURCE_NOT_FOUND', 'Recipient account not found: x'));
    const { el, review, click } = setup();

    review('nobody@example.com', '10');
    click('Send');

    expect(el.querySelector('[role=alert]')!.textContent).toContain(
      'No JCash account uses that email or mobile number.',
    );
  });
});

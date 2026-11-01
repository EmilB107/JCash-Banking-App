import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

interface Feature {
  title: string;
  description: string;
  /** For "How it's built": the Java/Spring technique behind it, so reviewers can find the code. */
  tech?: string;
}

@Component({
  selector: 'app-landing',
  imports: [RouterLink],
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly auth = inject(AuthService);
  protected readonly repoUrl = 'https://github.com/EmilB107/JCash-Banking-App';
  protected readonly year = new Date().getFullYear();

  // Only list what the backend actually implements, so the page never over-promises.
  protected readonly userFeatures: Feature[] = [
    {
      title: 'Send money',
      description:
        'Transfer to anyone with a JCash account using just their email or mobile number.',
    },
    {
      title: 'Cash in demo funds',
      description:
        'Every wallet starts at ₱0.00. Top it up with simulated money to try out transfers.',
    },
    {
      title: 'Track every peso',
      description:
        'A paged history of every cash-in and transfer, showing who sent or received it.',
    },
  ];

  protected readonly engineering: Feature[] = [
    {
      title: 'Exact money math',
      description:
        'Balances and amounts never use floating point, so ₱0.10 + ₱0.20 is always exactly ₱0.30.',
      tech: 'BigDecimal',
    },
    {
      title: 'All-or-nothing, retry-safe transfers',
      description:
        'A transfer runs in one database transaction, and its idempotency key means a retried request never sends money twice.',
      tech: '@Transactional · Idempotency-Key',
    },
    {
      title: 'Safe under concurrent transfers',
      description:
        'Accounts are locked in a consistent order, with optimistic locking as a backstop, so simultaneous transfers cannot overspend or deadlock.',
      tech: 'PESSIMISTIC_WRITE · @Version',
    },
    {
      title: 'Double-entry ledger',
      description:
        'Every transfer writes a matching debit and credit linked by a shared reference, so the books always balance.',
      tech: 'Ledger entries',
    },
    {
      title: 'Secure sessions',
      description:
        'Hashed PINs, server-side sessions, CSRF protection, and a 5-minute lockout after three wrong PINs.',
      tech: 'Spring Security · BCrypt',
    },
    {
      title: 'Tested and documented',
      description:
        'Unit, HTTP, and PostgreSQL integration tests (including concurrency), versioned database migrations, and interactive API docs.',
      tech: 'JUnit · Flyway · OpenAPI',
    },
  ];

  protected readonly stack = [
    'Angular',
    'TypeScript',
    'Tailwind CSS',
    'Spring Boot',
    'Java 21',
    'Spring Security',
    'PostgreSQL',
    'Flyway',
    'JUnit & Mockito',
    'Vitest',
  ];
}

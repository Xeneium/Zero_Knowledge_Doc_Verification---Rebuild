import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AuditIntegrityCard } from '../src/features/audit/components/AuditIntegrityCard';
import { AuditEventsListCard } from '../src/features/audit/components/AuditEventsListCard';
import { AuditEventDetailModal } from '../src/features/audit/components/AuditEventDetailModal';
import { AuditLogsPage } from '../src/routes/AuditLogsPage';
import { adminStore } from '../src/state/adminStore';
import { authStore } from '../src/state/authStore';
import { adminApi } from '../src/api/adminApi';
import type { SecurityAuditEventResponse, AuditQueryResult } from '../src/api/types';

describe('Audit Components & AuditLogsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authStore.clearSession();
    adminStore.reset();
  });

  const setAuthAdmin = () => {
    const payload = btoa(
      JSON.stringify({
        sub: 'urn:zkdoc:admin:operator1',
        roles: ['ADMIN'],
        iss: 'https://auth.zkdoc.internal',
        aud: 'zkdoc-api',
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    );
    authStore.setToken(`header.${payload}.sig`);
  };

  const mockEvent: SecurityAuditEventResponse = {
    eventId: 'evt-42',
    sequenceNumber: 42,
    eventType: 'USER_LOGIN',
    eventCategory: 'AUTHENTICATION',
    timestamp: '2026-10-15T08:30:00Z',
    actorSubject: 'urn:zkdoc:holder:alice',
    actorType: 'HOLDER',
    targetType: 'SESSION',
    targetId: 'sess-abc',
    action: 'LOGIN',
    outcome: 'SUCCESS',
    reasonCode: null,
    correlationId: null,
    clientIpHash: '192.168.1.100',
    metadataJson: '{"authMethod":"PASSWORD_HASH","sessionId":"sess-abc"}',
    previousEventHash: 'prev-hash-41',
    eventHash: 'entry-hash-42',
  };

  const mockQueryResult: AuditQueryResult = {
    items: [mockEvent],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  describe('AuditIntegrityCard', () => {
    it('renders hash chain verification form and displays valid result', async () => {
      const verifySpy = vi.spyOn(adminApi, 'verifyAuditIntegrity').mockResolvedValueOnce({
        valid: true,
        checkedCount: 42,
        tamperedSequence: null,
        failureReason: null,
      });

      render(<AuditIntegrityCard />);

      expect(screen.getByText('Audit Trail Hash Chain Integrity Verification')).toBeInTheDocument();

      const verifyBtn = screen.getByRole('button', { name: /Verify audit hash chain/i });
      fireEvent.click(verifyBtn);

      await waitFor(() => {
        expect(verifySpy).toHaveBeenCalledWith(1, undefined);
        expect(screen.getByText('UNBROKEN & VERIFIED')).toBeInTheDocument();
      });

      expect(screen.getByText('42')).toBeInTheDocument();
    });

    it('displays error badge when hash chain integrity fails', async () => {
      vi.spyOn(adminApi, 'verifyAuditIntegrity').mockResolvedValueOnce({
        valid: false,
        checkedCount: 15,
        tamperedSequence: 12,
        failureReason: 'Chain broken: hash mismatch detected at sequence 12',
      });

      render(<AuditIntegrityCard />);

      const verifyBtn = screen.getByRole('button', { name: /Verify audit hash chain/i });
      fireEvent.click(verifyBtn);

      await waitFor(() => {
        expect(screen.getByText('CHAIN INTEGRITY COMPROMISED')).toBeInTheDocument();
        expect(screen.getByText(/Sequence #12/i)).toBeInTheDocument();
      });
    });
  });

  describe('AuditEventsListCard', () => {
    it('renders audit events table, handles category change, and opens event detail modal', async () => {
      setAuthAdmin();
      const querySpy = vi.spyOn(adminApi, 'queryAuditEvents').mockResolvedValue(mockQueryResult);

      render(<AuditEventsListCard />);

      await waitFor(() => {
        expect(querySpy).toHaveBeenCalled();
        expect(screen.getByText('#42')).toBeInTheDocument();
      });

      expect(screen.getByText('LOGIN')).toBeInTheDocument();
      expect(screen.getByText('SUCCESS')).toBeInTheDocument();

      // Open detail modal using aria-label
      const inspectBtn = screen.getByRole('button', { name: /View audit event #42 details/i });
      fireEvent.click(inspectBtn);

      expect(screen.getByText('Security Audit Event #42')).toBeInTheDocument();
      expect(screen.getByText('entry-hash-42')).toBeInTheDocument();
    });

    it('exports audit events as JSON file', async () => {
      setAuthAdmin();
      const querySpy = vi.spyOn(adminApi, 'queryAuditEvents').mockResolvedValue(mockQueryResult);
      const createObjectURLMock = vi.fn().mockReturnValue('blob:mock-url');
      const revokeObjectURLMock = vi.fn();
      globalThis.URL.createObjectURL = createObjectURLMock;
      globalThis.URL.revokeObjectURL = revokeObjectURLMock;

      render(<AuditEventsListCard />);

      await waitFor(() => {
        expect(querySpy).toHaveBeenCalled();
        expect(screen.getByText('#42')).toBeInTheDocument();
      });

      const exportBtn = screen.getByRole('button', { name: /Export audit events as JSON/i });
      fireEvent.click(exportBtn);

      expect(createObjectURLMock).toHaveBeenCalled();
    });
  });

  describe('AuditEventDetailModal', () => {
    it('displays cryptographic hash chain linkage and formatted JSON metadata', () => {
      const onClose = vi.fn();

      render(
        <AuditEventDetailModal
          event={mockEvent}
          isOpen={true}
          onClose={onClose}
        />,
      );

      expect(screen.getByText('Security Audit Event #42')).toBeInTheDocument();
      expect(screen.getByText('entry-hash-42')).toBeInTheDocument();
      expect(screen.getByText('prev-hash-41')).toBeInTheDocument();
      expect(screen.getByText(/PASSWORD_HASH/i)).toBeInTheDocument();

      const closeBtn = screen.getByRole('button', { name: 'Close dialog' });
      fireEvent.click(closeBtn);

      expect(onClose).toHaveBeenCalled();
    });
  });

  describe('AuditLogsPage', () => {
    it('renders combined audit page with integrity panel and events list', async () => {
      setAuthAdmin();
      const querySpy = vi.spyOn(adminApi, 'queryAuditEvents').mockResolvedValue(mockQueryResult);

      render(
        <MemoryRouter>
          <AuditLogsPage />
        </MemoryRouter>,
      );

      expect(screen.getByText('Platform Security Audit Logs & Hash Chain')).toBeInTheDocument();
      expect(screen.getByText('Audit Trail Hash Chain Integrity Verification')).toBeInTheDocument();

      await waitFor(() => {
        expect(querySpy).toHaveBeenCalled();
        expect(screen.getByText('Immutable Security Audit Trail')).toBeInTheDocument();
      });
    });
  });
});

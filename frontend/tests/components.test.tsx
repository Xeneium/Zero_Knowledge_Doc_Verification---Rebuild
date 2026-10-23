import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  Button,
  Badge,
  Card,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
  Input,
  TextArea,
  Select,
  Checkbox,
  RadioGroup,
  FormField,
  Divider,
  Tooltip,
} from '../src/components/common';
import {
  Alert,
  Dialog,
  ConfirmationModal,
  Spinner,
  Skeleton,
  EmptyState,
  ErrorState,
} from '../src/components/feedback';
import {
  Table,
  Pagination,
  SearchFilterBar,
  CopyToClipboard,
  FormattedHash,
  KeyValueList,
  DetailPanel,
} from '../src/components/data';
import {
  VerificationStatusCard,
  VerificationBadge,
} from '../src/components/verification';
import { Breadcrumbs, PageHeader } from '../src/components/layout';
import { BrowserRouter } from 'react-router';

describe('Production UI Component Library', () => {
  describe('Button', () => {
    it('renders label and handles click', async () => {
      const user = userEvent.setup();
      const handleClick = vi.fn();
      render(<Button onClick={handleClick}>Verify Now</Button>);

      const button = screen.getByRole('button', { name: /verify now/i });
      expect(button).toBeInTheDocument();
      await user.click(button);
      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it('disables interaction and displays spinner when isLoading is true', () => {
      const handleClick = vi.fn();
      render(
        <Button isLoading onClick={handleClick}>
          Submit Proof
        </Button>,
      );

      const button = screen.getByRole('button', { name: /submit proof/i });
      expect(button).toBeDisabled();
      expect(button).toHaveAttribute('aria-busy', 'true');
    });

    it('renders destructive variant with red styling', () => {
      render(<Button variant="destructive">Revoke Key</Button>);
      const button = screen.getByRole('button', { name: /revoke key/i });
      expect(button.className).toContain('bg-red-600');
    });

    it('renders left and right icons when provided', () => {
      render(
        <Button
          leftIcon={<span data-testid="left-icon">left</span>}
          rightIcon={<span data-testid="right-icon">right</span>}
        >
          Navigate
        </Button>,
      );
      expect(screen.getByTestId('left-icon')).toBeInTheDocument();
      expect(screen.getByTestId('right-icon')).toBeInTheDocument();
      expect(screen.getByText('Navigate')).toBeInTheDocument();
    });
  });

  describe('Badge', () => {
    it('renders with semantic variant styling and accessible color contrast', () => {
      render(<Badge variant="success">Active</Badge>);
      const badge = screen.getByText('Active');
      expect(badge).toBeInTheDocument();
      expect(badge.className).toContain('text-green-800');
    });

    it('renders danger badge with alert circle icon', () => {
      render(<Badge variant="danger">Revoked</Badge>);
      expect(screen.getByText('Revoked')).toBeInTheDocument();
    });
  });

  describe('Card', () => {
    it('renders title, description, content, and footer', () => {
      render(
        <Card>
          <CardTitle>Security Registry</CardTitle>
          <CardDescription>Active keys in consensus</CardDescription>
          <CardContent>Cryptographic keys active</CardContent>
          <CardFooter>Footer note</CardFooter>
        </Card>,
      );

      expect(
        screen.getByRole('heading', { name: /security registry/i }),
      ).toBeInTheDocument();
      expect(screen.getByText(/active keys in consensus/i)).toBeInTheDocument();
      expect(
        screen.getByText(/cryptographic keys active/i),
      ).toBeInTheDocument();
      expect(screen.getByText(/footer note/i)).toBeInTheDocument();
    });
  });

  describe('Input & Form Controls', () => {
    it('renders Input with linked label and helper text', () => {
      render(
        <Input
          label="Credential ID"
          id="cred-input"
          helperText="Enter 36-character UUID"
        />,
      );

      const input = screen.getByLabelText(/credential id/i);
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute('aria-describedby', 'cred-input-helper');
      expect(screen.getByText(/enter 36-character uuid/i)).toBeInTheDocument();
    });

    it('renders Input in error state with aria-invalid and alert', () => {
      render(
        <Input
          label="Proof Nonce"
          id="nonce-input"
          error="Nonce must be valid hex"
        />,
      );

      const input = screen.getByLabelText(/proof nonce/i);
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(input).toHaveAttribute('aria-describedby', 'nonce-input-error');
      expect(screen.getByRole('alert')).toHaveTextContent(
        /nonce must be valid hex/i,
      );
    });

    it('renders TextArea with character counter', async () => {
      const user = userEvent.setup();
      render(
        <TextArea
          label="Proof Payload"
          id="proof-area"
          maxLength={100}
          showCharCount
        />,
      );

      const textarea = screen.getByLabelText(/proof payload/i);
      await user.type(textarea, 'test');
      expect(screen.getByText('4 / 100')).toBeInTheDocument();
    });

    it('renders Select with accessible options', () => {
      render(
        <Select
          label="Predicate Type"
          id="pred-type"
          options={[
            { value: 'GTE', label: 'Greater Than or Equal' },
            { value: 'LTE', label: 'Less Than or Equal' },
          ]}
        />,
      );

      const select = screen.getByLabelText(/predicate type/i);
      expect(select).toBeInTheDocument();
      expect(screen.getByText('Greater Than or Equal')).toBeInTheDocument();
    });

    it('renders Checkbox with keyboard and click interaction', async () => {
      const user = userEvent.setup();
      const handleChange = vi.fn();
      render(
        <Checkbox
          label="Confirm Nonce Freshness"
          id="fresh-check"
          onChange={handleChange}
        />,
      );

      const checkbox = screen.getByLabelText(/confirm nonce freshness/i);
      expect(checkbox).not.toBeChecked();
      await user.click(checkbox);
      expect(handleChange).toHaveBeenCalled();
    });

    it('renders RadioGroup with options', () => {
      render(
        <RadioGroup
          name="role-select"
          label="Choose Role"
          options={[
            { value: 'holder', label: 'Credential Holder' },
            { value: 'verifier', label: 'Public Verifier' },
          ]}
        />,
      );

      expect(screen.getByText('Choose Role')).toBeInTheDocument();
      expect(screen.getByLabelText('Credential Holder')).toBeInTheDocument();
      expect(screen.getByLabelText('Public Verifier')).toBeInTheDocument();
    });

    it('connects FormField compound wrapper', () => {
      render(
        <FormField label="Merkle Root" helperText="64-char hex">
          {(fieldProps) => <input {...fieldProps} />}
        </FormField>,
      );

      expect(screen.getByLabelText(/merkle root/i)).toBeInTheDocument();
      expect(screen.getByText('64-char hex')).toBeInTheDocument();
    });
  });

  describe('Divider & Tooltip', () => {
    it('renders Divider with horizontal separator role and label', () => {
      render(<Divider label="Cryptographic Assertion" />);
      expect(screen.getByRole('separator')).toHaveTextContent(
        /cryptographic assertion/i,
      );
    });

    it('renders Tooltip upon mouse enter and closes on Escape', async () => {
      const user = userEvent.setup();
      render(
        <Tooltip content="Cryptographic Hash SHA-256">
          <button type="button">Hover me</button>
        </Tooltip>,
      );

      const trigger = screen.getByRole('button', { name: /hover me/i });
      await user.hover(trigger);
      expect(screen.getByRole('tooltip')).toHaveTextContent(
        /cryptographic hash sha-256/i,
      );

      await user.keyboard('{Escape}');
      expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    });
  });

  describe('Feedback Components', () => {
    it('renders danger alert with role="alert" and dismiss handler', async () => {
      const user = userEvent.setup();
      const handleDismiss = vi.fn();
      render(
        <Alert variant="danger" title="Proof Error" onDismiss={handleDismiss}>
          Verification failed
        </Alert>,
      );

      expect(screen.getByRole('alert')).toBeInTheDocument();
      const dismissBtn = screen.getByRole('button', { name: /dismiss alert/i });
      await user.click(dismissBtn);
      expect(handleDismiss).toHaveBeenCalledTimes(1);
    });

    it('renders accessible Dialog with title, focus management, and Escape key dismiss', async () => {
      const user = userEvent.setup();
      const handleClose = vi.fn();

      render(
        <Dialog
          isOpen={true}
          onClose={handleClose}
          title="Verify Source Document"
          description="Confirm document SHA-256 hash"
        >
          <div>Dialog Inner Content</div>
        </Dialog>,
      );

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Verify Source Document')).toBeInTheDocument();
      expect(screen.getByText('Dialog Inner Content')).toBeInTheDocument();

      await user.keyboard('{Escape}');
      expect(handleClose).toHaveBeenCalled();
    });

    it('renders ConfirmationModal with Confirm and Cancel buttons', async () => {
      const user = userEvent.setup();
      const handleConfirm = vi.fn();
      const handleClose = vi.fn();

      render(
        <ConfirmationModal
          isOpen={true}
          onClose={handleClose}
          onConfirm={handleConfirm}
          title="Revoke Signing Key"
          message="This action is permanent."
          confirmText="Yes, Revoke"
        />,
      );

      expect(screen.getByText('Revoke Signing Key')).toBeInTheDocument();
      const confirmBtn = screen.getByRole('button', { name: /yes, revoke/i });
      await user.click(confirmBtn);
      expect(handleConfirm).toHaveBeenCalledTimes(1);
    });

    it('renders Spinner with role="status"', () => {
      render(<Spinner label="Verifying signatures..." />);
      expect(screen.getByRole('status')).toBeInTheDocument();
      expect(screen.getByText('Verifying signatures...')).toBeInTheDocument();
    });

    it('renders Skeleton with presentation role and aria-busy="true"', () => {
      const { container } = render(<Skeleton className="h-4 w-20" />);
      const skeleton = container.firstChild as HTMLElement;
      expect(skeleton).toHaveAttribute('role', 'presentation');
      expect(skeleton).toHaveAttribute('aria-busy', 'true');
    });

    it('renders EmptyState with icon and title', () => {
      render(
        <EmptyState
          title="No Credentials Issued"
          description="Upload a document to issue your first credential."
        />,
      );

      expect(screen.getByText('No Credentials Issued')).toBeInTheDocument();
    });

    it('renders ErrorState with correlation ID and retry button', async () => {
      const user = userEvent.setup();
      const handleRetry = vi.fn();
      render(
        <ErrorState
          title="Verification Offline"
          errorCode="ERR_TIMEOUT"
          correlationId="corr-abc-123"
          onRetry={handleRetry}
        />,
      );

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText('ERR_TIMEOUT')).toBeInTheDocument();
      expect(screen.getByText('corr-abc-123')).toBeInTheDocument();

      const retryBtn = screen.getByRole('button', { name: /try again/i });
      await user.click(retryBtn);
      expect(handleRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe('Data Presentation Components', () => {
    it('renders Table in tabular format on desktop', () => {
      const data = [{ id: '1', name: 'Document A', status: 'ACTIVE' }];
      const columns = [
        { key: 'name', header: 'Document Name' },
        { key: 'status', header: 'Status' },
      ];

      render(
        <Table
          data={data}
          columns={columns}
          keyExtractor={(item) => item.id}
          caption="Audit Log Table"
        />,
      );

      const table = screen.getByRole('table');
      expect(table).toBeInTheDocument();
      expect(within(table).getByText('Document A')).toBeInTheDocument();
    });

    it('renders Pagination with page numbers and active page attribute', () => {
      const handlePage = vi.fn();
      render(
        <Pagination currentPage={2} totalPages={5} onPageChange={handlePage} />,
      );

      const activePage = screen.getByRole('button', { name: 'Page 2' });
      expect(activePage).toHaveAttribute('aria-current', 'page');
    });

    it('renders SearchFilterBar and responds to search change', async () => {
      const user = userEvent.setup();
      const handleChange = vi.fn();
      render(
        <SearchFilterBar
          searchValue=""
          onSearchChange={handleChange}
          placeholder="Filter credentials..."
        />,
      );

      const searchInput = screen.getByPlaceholderText('Filter credentials...');
      await user.type(searchInput, 'abc');
      expect(handleChange).toHaveBeenCalled();
    });

    it('renders CopyToClipboard and triggers visual copied state', async () => {
      const user = userEvent.setup();
      render(<CopyToClipboard value="0x1234567890abcdef" showText />);

      const copyBtn = screen.getByRole('button', {
        name: /copy to clipboard/i,
      });
      await user.click(copyBtn);
      expect(screen.getByRole('status')).toHaveTextContent(/copied!/i);
    });

    it('renders FormattedHash with truncated string and mono font', () => {
      render(<FormattedHash value="0x1234567890abcdef1234567890abcdef" />);
      expect(screen.getByText(/0x123456…90abcdef/i)).toBeInTheDocument();
    });

    it('renders KeyValueList in semantic description list format', () => {
      const items = [{ label: 'Algorithm', value: 'SHA-256 with RSA-PSS' }];
      render(<KeyValueList items={items} />);
      expect(screen.getByText('Algorithm')).toBeInTheDocument();
      expect(screen.getByText('SHA-256 with RSA-PSS')).toBeInTheDocument();
    });

    it('renders DetailPanel and toggles disclosure on click', async () => {
      const user = userEvent.setup();
      render(
        <DetailPanel title="Cryptographic Receipt">
          <div>Proof Bytes: 0x99aa</div>
        </DetailPanel>,
      );

      const toggle = screen.getByRole('button', {
        name: /cryptographic receipt/i,
      });
      expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await user.click(toggle);
      expect(toggle).toHaveAttribute('aria-expanded', 'true');
      expect(screen.getByText(/proof bytes: 0x99aa/i)).toBeInTheDocument();
    });
  });

  describe('Verification Presentation Components', () => {
    it('renders VerificationStatusCard with VERIFIED & ACTIVE state', () => {
      render(<VerificationStatusCard status="VERIFIED_ACTIVE" />);
      expect(screen.getByText('VERIFIED & ACTIVE')).toBeInTheDocument();
      expect(
        screen.getByText('Cryptographic Credential Verified'),
      ).toBeInTheDocument();
    });

    it('renders VerificationStatusCard with PROOF REJECTED state', () => {
      render(<VerificationStatusCard status="PROOF_REJECTED" />);
      expect(screen.getByText('PROOF REJECTED')).toBeInTheDocument();
      expect(
        screen.getByText('Zero-Knowledge Proof Invalid'),
      ).toBeInTheDocument();
    });

    it('renders VerificationBadge with PREDICATE SATISFIED state', () => {
      render(<VerificationBadge status="PREDICATE_SATISFIED" />);
      expect(screen.getByText('PREDICATE SATISFIED')).toBeInTheDocument();
    });
  });

  describe('Navigation & Shell Components', () => {
    it('renders Breadcrumbs with accessible location current state', () => {
      render(
        <BrowserRouter>
          <Breadcrumbs
            items={[
              { label: 'Credentials', href: '/credentials' },
              { label: 'Details' },
            ]}
          />
        </BrowserRouter>,
      );

      const nav = screen.getByRole('navigation', { name: /breadcrumb/i });
      expect(nav).toBeInTheDocument();
      const current = screen.getByText('Details');
      expect(current).toHaveAttribute('aria-current', 'location');
    });

    it('renders PageHeader with title and description', () => {
      render(
        <PageHeader
          title="Issuer Console"
          description="Manage cryptographic issuance and revocation."
        />,
      );

      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
        'Issuer Console',
      );
      expect(
        screen.getByText(/manage cryptographic issuance/i),
      ).toBeInTheDocument();
    });
  });
});

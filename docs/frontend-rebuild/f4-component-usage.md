# Frontend Rebuild: Component Usage Guide

**Project:** Zero-Knowledge Document Verification Platform  
**Target Audience:** Frontend Engineers, UI Developers & QA  
**Document ID:** ZKDOC-FE-F4-002  
**Status:** Approved Component Manual  

---

## 1. Component Philosophy & Conventions

All UI components in the platform follow strict design system guidelines:
1. **Accessibility by Default:** Every interactive component guarantees minimum 44x44px touch targets, visible focus outlines, and programmatic ARIA bindings.
2. **Cryptographic Truth:** Status components display exact verified states. They never assume validity from the mere presence of data.
3. **Color Independence:** Statuses and alerts combine high-contrast colors with dedicated SVG icons and explicit text.
4. **Composition over Overrides:** Components use slot-based composition (`CardHeader`, `CardTitle`, `CardContent`, etc.) rather than deep prop trees.

---

## 2. Foundation Components (`src/components/common/`)

### 2.1 `Button`
Versatile button supporting standard variants, loading states, and accessible touch targets.

```tsx
import { Button } from '../components/common';

// Primary Action
<Button variant="primary" onClick={handleVerify}>
  Verify Credential
</Button>

// Loading State (disables interaction & shows spinner with aria-busy="true")
<Button variant="primary" isLoading={isSubmitting}>
  Computing Zero-Knowledge Proof...
</Button>

// Destructive Action (Key rotation / Credential Revocation)
<Button variant="destructive" onClick={handleRevoke}>
  Revoke Credential
</Button>
```

**Props:**
- `variant`: `'primary' | 'secondary' | 'outline' | 'destructive' | 'ghost'` (default: `'primary'`)
- `size`: `'sm' | 'md' | 'lg'` (default: `'md'`)
- `isLoading`: `boolean` (default: `false`)
- All standard `React.ButtonHTMLAttributes<HTMLButtonElement>`

---

### 2.2 `Input` & `TextArea`
Accessible form inputs with linked labels, helper text, and validation error states.

```tsx
import { Input, TextArea } from '../components/common';

// Standard Input with Helper Text
<Input
  id="credential-id"
  label="Credential Identifier"
  placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
  helperText="Enter canonical UUID matching issued credential"
  required
/>

// Input with Validation Error
<Input
  id="nonce-hex"
  label="Proof Nonce (Hex)"
  error="Nonce must be a 64-character hexadecimal string"
  value={nonce}
  onChange={(e) => setNonce(e.target.value)}
/>

// TextArea with Character Counter
<TextArea
  id="proof-payload"
  label="Raw Proof JSON"
  maxLength={5000}
  showCharCount
  placeholder="Paste raw cryptographic proof payload..."
/>
```

---

### 2.3 `Select`, `Checkbox` & `RadioGroup`

```tsx
import { Select, Checkbox, RadioGroup } from '../components/common';

// Dropdown Select
<Select
  id="predicate-op"
  label="Comparison Operator"
  options={[
    { value: 'GTE', label: 'Greater Than or Equal (>=)' },
    { value: 'LTE', label: 'Less Than or Equal (<=)' },
    { value: 'EQ', label: 'Exact Match (==)' },
  ]}
  value={operator}
  onChange={(e) => setOperator(e.target.value)}
/>

// Checkbox
<Checkbox
  id="terms-check"
  label="I understand Zero-Knowledge proofs do not disclose underlying values"
  checked={agreed}
  onChange={(e) => setAgreed(e.target.checked)}
/>

// RadioGroup
<RadioGroup
  name="role-mode"
  label="Verification Mode"
  options={[
    { value: 'ONLINE', label: 'Live Consensus Verification', helperText: 'Validates against active registry' },
    { value: 'OFFLINE', label: 'Offline Key Verification', helperText: 'Validates using cached public keys' },
  ]}
  value={mode}
  onChange={setMode}
/>
```

---

### 2.4 `Badge`
Accessible status indicator pairing semantic color with SVG iconography.

```tsx
import { Badge } from '../components/common';

<Badge variant="success">Active</Badge>
<Badge variant="warning">Expiring Soon</Badge>
<Badge variant="danger">Revoked</Badge>
<Badge variant="info">Holder Session</Badge>
<Badge variant="neutral">Anonymous</Badge>
```

---

### 2.5 `Card`
Composability container for dashboard panels, proof displays, and lists.

```tsx
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from '../components/common';

<Card>
  <CardHeader>
    <CardTitle>Tamper-Evident SHA-256 Digest</CardTitle>
    <CardDescription>Document hash calculated client-side before submission</CardDescription>
  </CardHeader>
  <CardContent>
    <code>e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855</code>
  </CardContent>
  <CardFooter>
    <Button variant="outline" size="sm">Recalculate Digest</Button>
  </CardFooter>
</Card>
```

---

## 3. Feedback Components (`src/components/feedback/`)

### 3.1 `Alert`
Inline feedback with `role="alert"` (for danger) or `role="status"` (for info/success).

```tsx
import { Alert } from '../components/feedback';

<Alert variant="danger" title="Signature Mismatch" onDismiss={() => setVisible(false)}>
  The document digest does not match the signature issued under the registered key.
</Alert>
```

---

### 3.2 `Dialog` & `ConfirmationModal`
Modal dialogs with focus trapping, `Escape` key dismiss, and backdrop dimming.

```tsx
import { Dialog, ConfirmationModal } from '../components/feedback';

// General Dialog
<Dialog
  isOpen={isOpen}
  onClose={() => setIsOpen(false)}
  title="Inspect Verification Receipt"
  description="Technical cryptographic receipt and consensus confirmation"
  footer={<Button onClick={() => setIsOpen(false)}>Close</Button>}
>
  <div>Receipt payload content...</div>
</Dialog>

// Confirmation Modal for Destructive Actions
<ConfirmationModal
  isOpen={isRevokeOpen}
  onClose={() => setIsRevokeOpen(false)}
  onConfirm={confirmRevocation}
  title="Revoke Credential"
  message="Are you sure you want to revoke this credential? This action creates an immutable cryptographic audit record and cannot be undone."
  variant="danger"
  confirmText="Yes, Revoke Credential"
/>
```

---

### 3.3 `ErrorState` & `EmptyState`

```tsx
import { ErrorState, EmptyState } from '../components/feedback';

// Error with Correlation ID for Auditing
<ErrorState
  title="Backend Connection Timeout"
  message="Unable to verify key registry status."
  errorCode="ERR_REGISTRY_UNREACHABLE"
  correlationId="550e8400-e29b-41d4-a716-446655440000"
  onRetry={refetch}
/>

// Empty Collection
<EmptyState
  title="No Credentials in Wallet"
  description="You have not requested or received any cryptographic credentials yet."
  action={<Button variant="primary">Request Credential</Button>}
/>
```

---

### 3.4 `Toast` Notification System

```tsx
import { useToast } from '../components/feedback';

function MyComponent() {
  const { addToast } = useToast();

  const handleCopy = () => {
    addToast({
      title: 'Hash Copied',
      message: 'SHA-256 digest copied to clipboard.',
      variant: 'success',
      duration: 3000,
    });
  };

  return <button onClick={handleCopy}>Copy</button>;
}
```

---

## 4. Data Presentation Components (`src/components/data/`)

### 4.1 `Table`
Responsive table with automatic stacked-card transformation on viewports `< 768px`.

```tsx
import { Table } from '../components/data';

const columns = [
  { key: 'credentialId', header: 'Credential ID' },
  { key: 'issuer', header: 'Issuer Authority' },
  { key: 'status', header: 'Status', render: (row) => <Badge variant="success">{row.status}</Badge> },
];

<Table
  data={credentials}
  columns={columns}
  keyExtractor={(row) => row.id}
  caption="Holder Credential Table"
  emptyMessage="No credentials available."
/>
```

---

### 4.2 `FormattedHash` & `CopyToClipboard`
Technical string presentation with truncation and accessible copying.

```tsx
import { FormattedHash, CopyToClipboard } from '../components/data';

// Displays "0x1a2b…3c4d" with full string in tooltip and embedded copy button
<FormattedHash value="0x1a2b3c4d5e6f78901234567890abcdef1234567890abcdef" />

// Standalone Copy Button
<CopyToClipboard value={fullProofBytes} label="Copy proof payload" showText />
```

---

### 4.3 `Pagination` & `SearchFilterBar`

```tsx
import { Pagination, SearchFilterBar } from '../components/data';

<SearchFilterBar
  searchValue={search}
  onSearchChange={setSearch}
  placeholder="Filter audit logs by correlation ID..."
/>

<Pagination
  currentPage={currentPage}
  totalPages={totalPages}
  totalItems={totalItems}
  pageSize={pageSize}
  onPageChange={handlePageChange}
/>
```

---

## 5. Verification Presentation Components (`src/components/verification/`)

### 5.1 `VerificationStatusCard`
Standardized cryptographic result presentation.

```tsx
import { VerificationStatusCard } from '../components/verification';

// Valid Active Credential
<VerificationStatusCard
  status="VERIFIED_ACTIVE"
  metadata={
    <KeyValueList
      items={[
        { label: 'Issuer Authority', value: 'National Ministry of Higher Education' },
        { label: 'Public Key ID', value: 'key-2026-v1', isCode: true },
        { label: 'Verified At', value: new Date().toISOString() },
      ]}
    />
  }
/>

// ZK Predicate Verified
<VerificationStatusCard
  status="PREDICATE_SATISFIED"
  customTitle="Income >= $50,000 Verified"
  customDescription="Zero-knowledge proof satisfies the income threshold requirement without revealing exact salary."
/>

// Rejected Proof
<VerificationStatusCard status="PROOF_REJECTED" />
```

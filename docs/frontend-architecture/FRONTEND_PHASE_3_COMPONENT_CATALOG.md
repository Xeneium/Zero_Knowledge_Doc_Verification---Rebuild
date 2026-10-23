# Phase 3 — Component Catalog & Interface Reference
## Zero-Knowledge Document Verification Platform

---

## 1. Overview & Catalog Organization

This catalog provides the complete engineering specifications for all foundational design-system components implemented and validated during **Phase 3: Frontend Architecture & Design System**.

Every component documented here has been inspected, typed with strict TypeScript contracts, integrated with design tokens in `tokens.css`, and verified through automated tests in [`frontend/tests/components.test.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/tests/components.test.tsx).

---

## 2. Common UI Primitives (`src/components/common/`)

### 2.1 `Button`
- **Location:** [`frontend/src/components/common/Button.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Button.tsx)
- **Purpose:** Primary interactive trigger for user actions, form submissions, and workflow navigation.
- **Props Interface:**
  ```typescript
  export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: 'primary' | 'secondary' | 'outline' | 'destructive' | 'ghost';
    size?: 'sm' | 'md' | 'lg';
    isLoading?: boolean;
    leftIcon?: React.ReactNode;
    rightIcon?: React.ReactNode;
  }
  ```
- **Variants:**
  - `primary`: Solid Indigo (`bg-indigo-600 hover:bg-indigo-700 text-white`) for main calls-to-action.
  - `secondary`: Light slate background with subtle border for secondary options.
  - `outline`: Bordered transparent button with hover background.
  - `destructive`: Red background (`bg-red-600`) for irreversible actions (key rotation, suspension).
  - `ghost`: Transparent button with hover highlight for icon buttons and navigation links.
- **Supported States:** `idle`, `hover`, `active`, `focus-visible`, `disabled`, `isLoading` (disables button, displays animated SVG spinner, sets `aria-busy="true"`).
- **Accessibility:**
  - Semantic `<button type="button">` default (prevents accidental form submission).
  - High-visibility focus ring: `focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2`.
  - Minimum touch bounding box: `sm` (36px), `md` (44px), `lg` (48px).
- **Test Suite:** Covered in `tests/components.test.tsx` (tests for clicks, disabled loading state, destructive variant, and left/right icon rendering).

---

### 2.2 `Input`
- **Location:** [`frontend/src/components/common/Input.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Input.tsx)
- **Purpose:** Accessible single-line text, password, and number data entry.
- **Props Interface:**
  ```typescript
  export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
    label?: string;
    helperText?: string;
    error?: string;
    leftIcon?: React.ReactNode;
    rightIcon?: React.ReactNode;
  }
  ```
- **Supported States:** `idle`, `focus-visible`, `disabled`, `error` (sets `aria-invalid="true"`, renders error message with `role="alert"`).
- **Accessibility:** Automatically links label to input ID; links helper text and errors via `aria-describedby`.

---

### 2.3 `TextArea`
- **Location:** [`frontend/src/components/common/TextArea.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/TextArea.tsx)
- **Purpose:** Multi-line text input for cryptographic tokens, PEM certificates, and JSON payloads.
- **Props Interface:**
  ```typescript
  export interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
    label?: string;
    helperText?: string;
    error?: string;
    showCharCount?: boolean;
  }
  ```
- **Features:** Monospace font styling for cryptographic payloads; live character counter.

---

### 2.4 `FormField`
- **Location:** [`frontend/src/components/common/FormField.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/FormField.tsx)
- **Purpose:** Higher-order layout wrapper ensuring programmatic ARIA linkage between labels, inputs, and validation error messages.
- **Props Interface:**
  ```typescript
  export interface FormFieldProps {
    id?: string;
    label?: string;
    helperText?: string;
    error?: string;
    required?: boolean;
    optional?: boolean;
    children: (props: {
      id: string;
      'aria-invalid'?: 'true';
      'aria-describedby'?: string;
      'aria-required'?: 'true';
      required?: boolean;
    }) => React.ReactNode;
  }
  ```
- **Accessibility:** Uses React `useId()` for deterministic DOM ID generation, guarantees `aria-describedby` linkage to helper or error spans.

---

### 2.5 `Badge`
- **Location:** [`frontend/src/components/common/Badge.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Badge.tsx)
- **Purpose:** Compact visual metadata and status tag.
- **Props Interface:**
  ```typescript
  export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
    variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
    icon?: React.ReactNode;
    showDefaultIcon?: boolean;
  }
  ```
- **Accessibility:** Multi-sensory outcome signaling: renders distinct default SVG icons (`CheckCircle2`, `AlertTriangle`, `AlertCircle`, `Info`, `Circle`) so that status is never communicated via color alone.

---

### 2.6 `Card` (Compound Component Family)
- **Location:** [`frontend/src/components/common/Card.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/common/Card.tsx)
- **Components:** `Card`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter`.
- **Purpose:** Primary content grouping surface with standardized elevation borders and dark-mode adaptations.

---

## 3. Feedback & Overlay Primitives (`src/components/feedback/`)

### 3.1 `Alert`
- **Location:** [`frontend/src/components/feedback/Alert.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Alert.tsx)
- **Purpose:** High-visibility contextual notices, warning banners, and error summaries.
- **Props Interface:**
  ```typescript
  export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
    variant?: 'info' | 'success' | 'warning' | 'danger';
    title?: string;
    onDismiss?: () => void;
    icon?: React.ReactNode;
  }
  ```
- **Accessibility:** Sets `role="alert"` for `danger` variants (assertive screen reader announcement) and `role="status"` for other variants (polite announcement).

---

### 3.2 `Dialog`
- **Location:** [`frontend/src/components/feedback/Dialog.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Dialog.tsx)
- **Purpose:** Accessible modal dialog window for confirmations, PEM key viewers, and wizards.
- **Props Interface:**
  ```typescript
  export interface DialogProps {
    isOpen: boolean;
    onClose: () => void;
    title: React.ReactNode;
    description?: React.ReactNode;
    children: React.ReactNode;
    footer?: React.ReactNode;
    maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
    className?: string;
  }
  ```
- **Accessibility & Focus Management:**
  - `role="dialog"` and `aria-modal="true"`.
  - Traps `Tab` and `Shift+Tab` focus cycles inside the modal container.
  - Automatically captures the triggering active element upon open and restores focus upon close.
  - Listens for `Escape` key to dismiss.
  - Locks background document body scroll (`overflow: hidden`).

---

### 3.3 `ConfirmationModal`
- **Location:** [`frontend/src/components/feedback/ConfirmationModal.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/ConfirmationModal.tsx)
- **Purpose:** Standardized confirmation dialog for high-stakes actions (credential suspension, key revocation).
- **Variants:** `danger` (destructive button), `warning`, `primary`.

---

### 3.4 `Spinner` & `Skeleton`
- **Location:** [`frontend/src/components/feedback/Spinner.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Spinner.tsx), [`Skeleton.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/feedback/Skeleton.tsx)
- **Purpose:** Indeterminate progress indicator and content placeholder during asynchronous data loading.
- **Accessibility:** `role="status"` with hidden `<span className="sr-only">Loading...</span>`.

---

## 4. Cryptographic & Data Primitives (`src/components/data/`)

### 4.1 `FormattedHash`
- **Location:** [`frontend/src/components/data/FormattedHash.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/data/FormattedHash.tsx)
- **Purpose:** Progressive disclosure display for long cryptographic digests (SHA-256, RSA fingerprints, Merkle roots).
- **Props Interface:**
  ```typescript
  export interface FormattedHashProps {
    value: string;
    truncate?: boolean;
    prefixLength?: number;
    suffixLength?: number;
    copyable?: boolean;
    className?: string;
  }
  ```
- **Features:** Truncates string (e.g. `0xe3b0…1c14`) while maintaining full string in `title` and `aria-label`. Integrates one-click `CopyToClipboard` button with visual confirmation.

---

### 4.2 `CopyToClipboard`
- **Location:** [`frontend/src/components/data/CopyToClipboard.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/data/CopyToClipboard.tsx)
- **Purpose:** Accessible clipboard copy button with transient "Copied" checkmark feedback.

---

### 4.3 `Table`, `Pagination`, `SearchFilterBar`
- **Location:** [`frontend/src/components/data/Table.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/data/Table.tsx)
- **Purpose:** Responsive tabular presentation for audit logs, credential lists, and key registries.
- **Features:** Sortable columns, accessible page navigation, real-time client-side text filtering.

---

## 5. Domain Verification Components (`src/components/verification/`)

### 5.1 `VerificationStatusCard`
- **Location:** [`frontend/src/components/verification/VerificationStatusCard.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/verification/VerificationStatusCard.tsx)
- **Purpose:** Centralized verification result card presenting verified outcomes with progressive disclosure of technical proof details.
- **Supported States:**
  - `VERIFIED_ACTIVE`: Emerald green checkmark, confirms signature validity and active registry status.
  - `CREDENTIAL_REVOKED`: Red alert octagon, confirms signature but indicates formal issuer revocation.
  - `CREDENTIAL_EXPIRED`: Amber clock icon, indicates expired validity timestamp.
  - `PREDICATE_SATISFIED`: Emerald shield check, confirms zero-knowledge proof without disclosing private claim values.
  - `PROOF_REJECTED`: Red X-circle, indicates failed mathematical proof verification.
  - `NOT_RECOGNIZED`: Neutral question icon, indicates unregistered digest or capability token.
  - `UNAVAILABLE`: Warning alert, indicates backend network or verification service failure.
- **Accessibility:** Enforces `role="region"` with `aria-label="Verification Result: {status}"`.

---

## 6. Shared Layout Elements (`src/components/layout/`)

### 6.1 `PageHeader`
- **Location:** [`frontend/src/components/layout/PageHeader.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/PageHeader.tsx)
- **Purpose:** Consistent top-of-page heading with breadcrumb trail, status badge, description, and primary action buttons.

### 6.2 `SkipLink`
- **Location:** [`frontend/src/components/layout/SkipLink.tsx`](file:///c:/Sabareesh/Easwari%20College/Semester%203/OOPs%20with%20Java/Project/Start%20over/Zero%20Knowledge%20Document%20Verification/frontend/src/components/layout/SkipLink.tsx)
- **Purpose:** Keyboard accessibility skip navigation allowing screen-reader and keyboard users to bypass top navigation directly to `<main id="main-content">`.

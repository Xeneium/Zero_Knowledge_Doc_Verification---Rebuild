import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { PublicVerificationPage } from '../src/features/verification/pages/PublicVerificationPage';

const token = 'A'.repeat(43);

function verificationResponse(
  signatureValid: boolean,
  documentActive: boolean,
) {
  return new Response(JSON.stringify({ signatureValid, documentActive }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderPage(key?: string) {
  return render(
    <MemoryRouter>
      <PublicVerificationPage key={key} />
    </MemoryRouter>,
  );
}

describe('PublicVerificationPage', () => {
  it('renders both manual and camera verification methods', () => {
    renderPage();
    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Public document verification',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Verification URL or reference'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Start camera' }),
    ).toBeInTheDocument();
  });

  it('validates an empty reference accessibly', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: 'Verify document' }));

    expect(
      screen.getByText('Enter a verification reference.'),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Verification URL or reference'),
    ).toHaveAttribute('aria-invalid', 'true');
    expect(
      screen.getByLabelText('Verification URL or reference'),
    ).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('rejects an arbitrary URL without opening it or contacting the API', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      'https://untrusted.example/collect',
    );
    await user.click(screen.getByRole('button', { name: 'Verify document' }));

    expect(
      screen.getByText('This QR code is not a supported verification link.'),
    ).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows verified only after the backend confirms both outcomes', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(verificationResponse(true, true));
    const user = userEvent.setup();
    renderPage();

    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      token,
    );
    await user.click(screen.getByRole('button', { name: 'Verify document' }));

    expect(
      await screen.findByRole('heading', { name: 'Document verified' }),
    ).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      `/api/public/verify/${token}`,
      expect.objectContaining({ method: 'GET' }),
    );
    expect(screen.getByLabelText('Verification URL or reference')).toHaveValue(
      '',
    );
  });

  it('distinguishes inactive and invalid backend results', async () => {
    const user = userEvent.setup();
    vi.mocked(fetch).mockResolvedValueOnce(verificationResponse(true, false));
    const { rerender } = renderPage();

    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      token,
    );
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    expect(
      await screen.findByRole('heading', { name: 'Document is inactive' }),
    ).toBeInTheDocument();

    rerender(
      <MemoryRouter>
        <PublicVerificationPage key="invalid" />
      </MemoryRouter>,
    );
    vi.mocked(fetch).mockResolvedValueOnce(verificationResponse(false, true));
    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      token,
    );
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    expect(
      await screen.findByRole('heading', {
        name: 'Document signature is invalid',
      }),
    ).toBeInTheDocument();
  });

  it('does not turn a network failure into an invalid-document result', async () => {
    vi.mocked(fetch).mockRejectedValueOnce(
      new TypeError('secret network detail'),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      token,
    );
    await user.click(screen.getByRole('button', { name: 'Verify document' }));

    expect(
      await screen.findByText(/verification service is unavailable/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Document signature is invalid'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/secret network detail/i),
    ).not.toBeInTheDocument();
  });

  it('reduces not-found and server errors to safe public messages', async () => {
    const user = userEvent.setup();
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ message: 'Bearer private-token-value' }),
          {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
          },
        ),
      );
    renderPage();

    const input = screen.getByLabelText('Verification URL or reference');
    await user.type(input, token);
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    expect(
      await screen.findByText(/reference was not recognized/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Verify another' }));
    await user.type(input, token);
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    expect(
      await screen.findByText(/could not establish a trustworthy/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/private-token-value/i)).not.toBeInTheDocument();
  });

  it('does not allow a stale request to replace a newer result', async () => {
    let resolveFirst: ((response: Response) => void) | undefined;
    vi.mocked(fetch)
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(verificationResponse(true, true));
    const user = userEvent.setup();
    renderPage();

    const input = screen.getByLabelText('Verification URL or reference');
    await user.type(input, token);
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    await user.click(screen.getByRole('button', { name: 'Verify another' }));
    await user.type(input, 'B'.repeat(43));
    await user.click(screen.getByRole('button', { name: 'Verify document' }));
    expect(
      await screen.findByRole('heading', { name: 'Document verified' }),
    ).toBeInTheDocument();

    await act(async () => {
      resolveFirst?.(verificationResponse(false, false));
      await Promise.resolve();
    });
    expect(
      screen.getByRole('heading', { name: 'Document verified' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Document signature is invalid' }),
    ).not.toBeInTheDocument();
  });

  it('prevents duplicate submissions while verification is pending', async () => {
    let resolveRequest: ((response: Response) => void) | undefined;
    vi.mocked(fetch).mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.type(
      screen.getByLabelText('Verification URL or reference'),
      token,
    );
    const button = screen.getByRole('button', { name: 'Verify document' });
    await user.dblClick(button);

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Verifying…' })).toBeDisabled();
    resolveRequest?.(verificationResponse(true, true));
    expect(
      await screen.findByRole('heading', { name: 'Document verified' }),
    ).toBeInTheDocument();
  });
});

import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QrScanner } from '../src/features/verification/components/QrScanner';
import type {
  ScannerSession,
  ScannerStarter,
} from '../src/features/verification/scanner/startQrScanner';

describe('QrScanner', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn() },
    });
  });

  it('starts only after an explicit action and returns one scan', async () => {
    const user = userEvent.setup();
    const stop = vi.fn();
    let deliver: ((value: string) => void) | undefined;
    const starter: ScannerStarter = vi.fn((_video, onResult) => {
      deliver = onResult;
      return Promise.resolve({ stop });
    });
    const onScan = vi.fn();
    render(<QrScanner onScan={onScan} scannerStarter={starter} />);

    expect(starter).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Start camera' }));
    expect(starter).toHaveBeenCalledTimes(1);
    expect(
      await screen.findByText('Looking for a supported QR code…'),
    ).toBeInTheDocument();

    act(() => deliver?.('scanned-value'));
    expect(onScan).toHaveBeenCalledWith('scanned-value');
    expect(stop).toHaveBeenCalledTimes(1);
  });

  it('reports camera permission denial without exposing an exception', async () => {
    const user = userEvent.setup();
    const starter: ScannerStarter = vi
      .fn()
      .mockRejectedValue(new DOMException('private detail', 'NotAllowedError'));
    render(<QrScanner onScan={vi.fn()} scannerStarter={starter} />);

    await user.click(screen.getByRole('button', { name: 'Start camera' }));

    expect(
      await screen.findByText(/camera permission was denied/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/private detail/i)).not.toBeInTheDocument();
  });

  it('stops the scanner when unmounted', async () => {
    const user = userEvent.setup();
    const stop = vi.fn();
    const starter: ScannerStarter = vi.fn().mockResolvedValue({ stop });
    const { unmount } = render(
      <QrScanner onScan={vi.fn()} scannerStarter={starter} />,
    );

    await user.click(screen.getByRole('button', { name: 'Start camera' }));
    await screen.findByText('Looking for a supported QR code…');
    unmount();

    expect(stop).toHaveBeenCalledTimes(1);
  });

  it('stops a scanner that finishes starting after the user cancels', async () => {
    const user = userEvent.setup();
    const stop = vi.fn();
    let resolveStart: ((session: { stop: () => void }) => void) | undefined;
    const starter: ScannerStarter = vi.fn(
      () =>
        new Promise<ScannerSession>((resolve) => {
          resolveStart = resolve;
        }),
    );
    render(<QrScanner onScan={vi.fn()} scannerStarter={starter} />);

    await user.click(screen.getByRole('button', { name: 'Start camera' }));
    await user.click(screen.getByRole('button', { name: 'Stop camera' }));
    await act(async () => {
      resolveStart?.({ stop });
      await Promise.resolve();
    });

    expect(stop).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Camera is off')).toBeInTheDocument();
  });

  it('provides a manual fallback when camera APIs are unsupported', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: undefined,
    });
    const user = userEvent.setup();
    const starter: ScannerStarter = vi.fn();
    render(<QrScanner onScan={vi.fn()} scannerStarter={starter} />);

    await user.click(screen.getByRole('button', { name: 'Start camera' }));

    expect(
      await screen.findByText(/camera scanning is not supported/i),
    ).toBeInTheDocument();
    expect(starter).not.toHaveBeenCalled();
  });
});

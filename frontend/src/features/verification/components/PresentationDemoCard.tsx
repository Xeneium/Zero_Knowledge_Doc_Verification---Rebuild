import { QrCode, Sparkles } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import {
  PRESENTATION_DEMO_TOKEN,
  PRESENTATION_DEMO_URL,
} from '../demo/presentationDemo';

// A fixed QR matrix for PRESENTATION_DEMO_URL. It is generated once from the
// same QR format used by the backend and keeps the presentation example
// independent of a database or network request.
const presentationQrModules = [
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
  '000011111110001000100100000111111011111110000',
  '000010000010010001000101000100111010000010000',
  '000010111010100100010010000100011010111010000',
  '000010111010010011001110011100110010111010000',
  '000010111010010011001101000100010010111010000',
  '000010000010001110111001011100111010000010000',
  '000011111110101010101010101010101011111110000',
  '000000000000100100010101110001010000000000000',
  '000011101111100010001100110101011110001000000',
  '000000000000100111011100101011001011001110000',
  '000000000011110110111110011000001010110010000',
  '000011001100010011101100010111111101110110000',
  '000011101010010100111100110001011011000000000',
  '000011101001001100110110110111101011001010000',
  '000011000010010001000000110001101000010010000',
  '000010110000011100011111110111000101100100000',
  '000000101111000010000100010111001011000010000',
  '000001010101011111011110010010001011001010000',
  '000000111011100110101010001000100010101110000',
  '000010111100010011100111011011111100000100000',
  '000000010110110100111000110011000011000110000',
  '000000000000000100111001110111001110001010000',
  '000001001111111001000100001001001101010110000',
  '000001000000101100010101011001010011000100000',
  '000011110111010010001000000111000111000110000',
  '000000110001110111001110001110101110010010000',
  '000010110110010110100110101000100011010110000',
  '000001001001001011110100011001101000100100000',
  '000010110011100100110001010111111111100000000',
  '000000000000100100111000111110011000110110000',
  '000011111110111001000010011001011010111110000',
  '000010000010111100010110010101111000100010000',
  '000010111010111010011000010111101111100010000',
  '000010111010010111001101100010100001101000000',
  '000010111010110110100000001000100101101110000',
  '000010000010110011111100011101111101000100000',
  '000011111110101100111110010010011010000110000',
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
  '000000000000000000000000000000000000000000000',
] as const;

export function PresentationDemoCard({ onVerify }: { onVerify: () => void }) {
  return (
    <Card className="mt-5 p-5 sm:p-6">
      <div className="flex items-start gap-3">
        <span className="bg-info-50 text-info-700 inline-flex size-10 shrink-0 items-center justify-center rounded-lg">
          <Sparkles className="size-5" aria-hidden="true" />
        </span>
        <div>
          <h2 className="text-ink-900 font-semibold">Presentation example</h2>
          <p className="text-ink-600 mt-1 text-sm leading-6">
            One local-only sample shows how an active, valid document result
            will look. It does not create a document or call the backend.
          </p>
        </div>
      </div>

      <div className="mt-5 flex flex-col gap-5 sm:flex-row sm:items-center">
        <div className="border-ink-200 shrink-0 rounded-lg border bg-white p-2 shadow-[var(--shadow-sm)]">
          <svg
            aria-label="QR code for the presentation verification example"
            className="size-32"
            role="img"
            viewBox="0 0 45 45"
          >
            <title>Presentation verification QR code</title>
            <rect width="45" height="45" fill="white" />
            {presentationQrModules.flatMap((row, y) =>
              [...row].flatMap((module, x) =>
                module === '1' ? (
                  <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" />
                ) : (
                  []
                ),
              ),
            )}
          </svg>
        </div>

        <div className="min-w-0">
          <div className="text-ink-800 flex items-center gap-2 text-sm font-semibold">
            <QrCode className="size-4" aria-hidden="true" /> Static QR and
            verification reference
          </div>
          <p className="text-ink-600 mt-2 font-mono text-xs leading-5 break-all">
            {PRESENTATION_DEMO_TOKEN}
          </p>
          <p className="text-ink-500 mt-2 text-xs leading-5 break-all">
            Encodes: {PRESENTATION_DEMO_URL}
          </p>
          <Button
            type="button"
            variant="secondary"
            className="mt-4"
            onClick={onVerify}
          >
            Verify presentation example
          </Button>
        </div>
      </div>
    </Card>
  );
}

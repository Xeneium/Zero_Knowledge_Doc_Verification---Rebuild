import type { IScannerControls } from '@zxing/browser';

export interface ScannerSession {
  stop: () => void;
}

export type ScannerStarter = (
  video: HTMLVideoElement,
  onResult: (value: string) => void,
) => Promise<ScannerSession>;

function stopVideo(video: HTMLVideoElement): void {
  if (video.srcObject instanceof MediaStream) {
    for (const track of video.srcObject.getTracks()) track.stop();
  }
  video.srcObject = null;
}

export const startQrScanner: ScannerStarter = async (video, onResult) => {
  const { BrowserQRCodeReader } = await import('@zxing/browser');
  const reader = new BrowserQRCodeReader(undefined, {
    delayBetweenScanAttempts: 250,
  });
  let delivered = false;
  const scanner: { controls?: IScannerControls } = {};

  scanner.controls = await reader.decodeFromConstraints(
    { audio: false, video: { facingMode: { ideal: 'environment' } } },
    video,
    (result) => {
      if (!result || delivered) return;
      delivered = true;
      const value = result.getText();
      scanner.controls?.stop();
      stopVideo(video);
      onResult(value);
    },
  );

  return {
    stop: () => {
      scanner.controls?.stop();
      stopVideo(video);
    },
  };
};

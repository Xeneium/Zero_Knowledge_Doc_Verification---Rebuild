import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  BrowserQRCodeReader,
  BrowserCodeReader,
  type IScannerControls,
} from '@zxing/browser';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '../../../components/common/Card';
import { Button } from '../../../components/common/Button';
import { Alert } from '../../../components/feedback/Alert';
import { Spinner } from '../../../components/feedback/Spinner';
import { parseQrPayload } from '../../../lib/utils/qrPayload';
import { Camera, CameraOff, Upload, RefreshCw } from 'lucide-react';

interface QrScannerCardProps {
  onScanSuccess: (rawPayload: string) => void;
  disabled?: boolean;
}

type CameraState =
  | 'idle'
  | 'requesting'
  | 'scanning'
  | 'permission_denied'
  | 'unavailable'
  | 'error';

export const QrScannerCard: React.FC<QrScannerCardProps> = ({
  onScanSuccess,
  disabled = false,
}) => {
  const [cameraState, setCameraState] = useState<CameraState>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [videoDevices, setVideoDevices] = useState<MediaDeviceInfo[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | undefined>(
    undefined,
  );
  const [fileDecoding, setFileDecoding] = useState(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const controlsRef = useRef<IScannerControls | null>(null);
  const readerRef = useRef<BrowserQRCodeReader | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const stopCamera = useCallback(() => {
    if (controlsRef.current) {
      controlsRef.current.stop();
      controlsRef.current = null;
    }
    if (videoRef.current) {
      if (videoRef.current.srcObject instanceof MediaStream) {
        videoRef.current.srcObject.getTracks().forEach((track) => track.stop());
      }
      BrowserCodeReader.cleanVideoSource(videoRef.current);
      videoRef.current.srcObject = null;
    }
    setCameraState('idle');
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, [stopCamera]);

  const startCamera = async (deviceId?: string) => {
    setErrorMessage(null);
    setCameraState('requesting');

    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        setCameraState('unavailable');
        setErrorMessage(
          'Camera access is not supported by your browser or environment. Please use image upload or manual token entry.',
        );
        return;
      }

      // Check available devices
      try {
        const devices = await BrowserCodeReader.listVideoInputDevices();
        setVideoDevices(devices);
      } catch {
        // Non-critical if device enumeration fails
      }

      if (!readerRef.current) {
        readerRef.current = new BrowserQRCodeReader();
      }

      const videoElement = videoRef.current;
      if (!videoElement) {
        throw new Error('Video preview element not available');
      }

      const controls = await readerRef.current.decodeFromVideoDevice(
        deviceId,
        videoElement,
        (result, error) => {
          if (result) {
            const text = result.getText();
            const parseResult = parseQrPayload(text);
            if (parseResult.success) {
              stopCamera();
              onScanSuccess(text);
            } else {
              setErrorMessage(parseResult.error);
            }
          }
          if (error && error.name !== 'NotFoundException') {
            // Ignore normal frame-level NotFoundExceptions while scanning
          }
        },
      );

      controlsRef.current = controls;
      setCameraState('scanning');
    } catch (err) {
      const error = err as Error;
      if (
        error.name === 'NotAllowedError' ||
        error.name === 'PermissionDeniedError'
      ) {
        setCameraState('permission_denied');
        setErrorMessage(
          'Camera permission was denied. Please allow camera access in your browser settings to scan QR codes, or upload an image.',
        );
      } else if (
        error.name === 'NotFoundError' ||
        error.name === 'DevicesNotFoundError'
      ) {
        setCameraState('unavailable');
        setErrorMessage(
          'No camera device was detected on your system. Use image upload or manual entry instead.',
        );
      } else {
        setCameraState('error');
        setErrorMessage(
          `Unable to access camera: ${error.message || 'Unknown error'}. Secure HTTPS context is required outside localhost.`,
        );
      }
      stopCamera();
    }
  };

  const handleDeviceChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newId = e.target.value;
    setSelectedDeviceId(newId);
    stopCamera();
    void startCamera(newId);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || fileDecoding) return;

    setErrorMessage(null);
    setFileDecoding(true);
    let imgUrl: string | null = null;

    try {
      if (!readerRef.current) {
        readerRef.current = new BrowserQRCodeReader();
      }

      imgUrl = URL.createObjectURL(file);
      const img = new Image();
      img.src = imgUrl;

      await new Promise<void>((resolve, reject) => {
        img.onload = () => resolve();
        img.onerror = () => reject(new Error('Failed to load image file'));
      });

      const result = await readerRef.current.decodeFromImageElement(img);

      if (result) {
        const text = result.getText();
        const parseResult = parseQrPayload(text);
        if (parseResult.success) {
          onScanSuccess(text);
        } else {
          setErrorMessage(parseResult.error);
        }
      }
    } catch (err) {
      const error = err as Error;
      setErrorMessage(
        `Failed to decode QR code from image: ${error.message || 'No valid QR code found in selected image'}.`,
      );
    } finally {
      if (imgUrl) {
        URL.revokeObjectURL(imgUrl);
      }
      setFileDecoding(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg text-slate-900">
          <Camera className="h-5 w-5 text-emerald-600" aria-hidden="true" />
          Camera &amp; QR Code Scanner
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-600">
          Scan an issued document or verification capability QR code. Video
          frames are processed entirely in browser memory and are never uploaded
          or logged.
        </p>

        {errorMessage && (
          <Alert variant="danger" title="Scanner Notice">
            {errorMessage}
          </Alert>
        )}

        {/* Video Preview Box */}
        <div className="relative mx-auto flex aspect-square max-h-80 w-full max-w-sm items-center justify-center overflow-hidden rounded-xl bg-slate-900 shadow-inner">
          <video
            ref={videoRef}
            className={`h-full w-full object-cover ${cameraState === 'scanning' ? 'block' : 'hidden'}`}
            playsInline
            muted
            aria-label="Live Camera QR Scanner Preview"
          />

          {cameraState === 'scanning' && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div className="h-48 w-48 rounded-lg border-2 border-emerald-400 bg-emerald-400/10 shadow-[0_0_15px_rgba(52,211,153,0.5)]" />
            </div>
          )}

          {cameraState === 'idle' && (
            <div className="flex flex-col items-center justify-center p-6 text-center text-slate-400">
              <Camera
                className="mb-3 h-12 w-12 text-slate-600"
                aria-hidden="true"
              />
              <p className="text-xs">Camera is currently inactive</p>
            </div>
          )}

          {cameraState === 'requesting' && (
            <div className="flex flex-col items-center justify-center p-6 text-center text-slate-300">
              <Spinner size="lg" className="mb-3 text-emerald-400" />
              <p className="text-xs">Requesting camera permissions...</p>
            </div>
          )}

          {(cameraState === 'permission_denied' ||
            cameraState === 'unavailable' ||
            cameraState === 'error') && (
            <div className="flex flex-col items-center justify-center p-6 text-center text-slate-400">
              <CameraOff
                className="mb-3 h-12 w-12 text-rose-500"
                aria-hidden="true"
              />
              <p className="text-xs text-rose-400">Camera Unavailable</p>
            </div>
          )}
        </div>

        {/* Device Switcher (when multiple cameras detected) */}
        {videoDevices.length > 1 && cameraState === 'scanning' && (
          <div className="flex items-center gap-2">
            <label
              htmlFor="camera-select"
              className="text-xs font-medium text-slate-700"
            >
              Camera:
            </label>
            <select
              id="camera-select"
              value={selectedDeviceId || ''}
              onChange={handleDeviceChange}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-800 shadow-sm focus:border-emerald-500 focus:outline-none"
            >
              {videoDevices.map((dev, idx) => (
                <option key={dev.deviceId || idx} value={dev.deviceId}>
                  {dev.label || `Camera ${idx + 1}`}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* Camera Control Actions */}
        <div className="flex flex-wrap items-center gap-3">
          {cameraState !== 'scanning' ? (
            <Button
              type="button"
              variant="primary"
              disabled={disabled || cameraState === 'requesting'}
              onClick={() => {
                void startCamera(selectedDeviceId);
              }}
              className="flex items-center gap-2"
            >
              <Camera className="h-4 w-4" aria-hidden="true" />
              Start Camera Scanner
            </Button>
          ) : (
            <Button
              type="button"
              variant="destructive"
              onClick={stopCamera}
              className="flex items-center gap-2"
            >
              <CameraOff className="h-4 w-4" aria-hidden="true" />
              Stop Camera
            </Button>
          )}

          {/* Image Upload Fallback */}
          <input
            type="file"
            ref={fileInputRef}
            onChange={(e) => {
              void handleFileUpload(e);
            }}
            accept="image/png, image/jpeg, image/webp"
            className="hidden"
            aria-label="Upload QR Code Image File"
          />
          <Button
            type="button"
            variant="outline"
            disabled={disabled || fileDecoding}
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-2"
          >
            {fileDecoding ? (
              <RefreshCw className="h-4 w-4 animate-spin text-slate-500" />
            ) : (
              <Upload className="h-4 w-4 text-slate-500" aria-hidden="true" />
            )}
            {fileDecoding ? 'Decoding Image...' : 'Upload QR Image'}
          </Button>
        </div>

        <p className="text-xs text-slate-500">
          Note: Camera access requires HTTPS in production. If camera permission
          is blocked by system policy, select <strong>Upload QR Image</strong>{' '}
          to decode from an image file.
        </p>
      </CardContent>
    </Card>
  );
};

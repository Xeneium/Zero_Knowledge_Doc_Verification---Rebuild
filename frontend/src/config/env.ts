/**
 * Frontend environment configuration.
 * Strictly adheres to F2 security boundaries: zero secrets or sensitive keys.
 */
export interface AppEnvironment {
  readonly apiUrl: string;
  readonly appName: string;
  readonly isProduction: boolean;
  readonly isDevelopment: boolean;
}

export const env: AppEnvironment = {
  apiUrl: import.meta.env.VITE_API_URL || '/api',
  appName:
    import.meta.env.VITE_APP_NAME || 'Zero-Knowledge Document Verification',
  isProduction: import.meta.env.PROD,
  isDevelopment: import.meta.env.DEV,
};

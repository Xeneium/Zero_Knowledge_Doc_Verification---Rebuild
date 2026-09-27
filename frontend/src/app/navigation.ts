import {
  FileCheck2,
  Files,
  KeyRound,
  LayoutDashboard,
  ScanLine,
  ScrollText,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';

export interface NavigationItem {
  label: string;
  path: string;
  icon: LucideIcon;
}

export const navigationItems: NavigationItem[] = [
  { label: 'Overview', path: '/', icon: LayoutDashboard },
  { label: 'Public verification', path: '/verify', icon: ScanLine },
  { label: 'Credentials', path: '/credentials', icon: Files },
  { label: 'Verification', path: '/verification', icon: FileCheck2 },
  {
    label: 'Predicate verification',
    path: '/predicate-verification',
    icon: ShieldCheck,
  },
  { label: 'Audit logs', path: '/audit-logs', icon: ScrollText },
  { label: 'Platform operations', path: '/administration', icon: KeyRound },
];

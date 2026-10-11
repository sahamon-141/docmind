import type { DocumentStatus } from '../../api/types';
import { StatusDot } from '../ui/Misc';

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  switch (status) {
    case 'INDEXED':
      return <StatusDot tone="positive" label="Indexed" />;
    case 'FAILED':
      return <StatusDot tone="negative" label="Failed" />;
    default:
      return <StatusDot tone="warning" label="Processing" pulse />;
  }
}

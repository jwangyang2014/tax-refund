export const STATUS_ORDER = ['RECEIVED', 'PROCESSING', 'APPROVED', 'SENT', 'AVAILABLE'] as const;

export function nextStatus(curr: string): string {
  const index = STATUS_ORDER.indexOf(curr as (typeof STATUS_ORDER)[number]);
  if (index < 0) return STATUS_ORDER[0];
  return STATUS_ORDER[(index + 1) % STATUS_ORDER.length];
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return 'N/A';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return String(iso);
  }
}

export function getStatusStepIndex(status: string): number {
  const idx = STATUS_ORDER.indexOf(status as (typeof STATUS_ORDER)[number]);
  return idx < 0 ? -1 : idx;
}

export function getStatusTone(status: string): { label: string; color: string; bg: string } {
  switch (status) {
    case 'AVAILABLE':
      return { label: 'Refund available', color: '#0f5132', bg: '#d1e7dd' };
    case 'REJECTED':
      return { label: 'Needs attention', color: '#842029', bg: '#f8d7da' };
    case 'NOT_FOUND':
      return { label: 'Not found', color: '#664d03', bg: '#fff3cd' };
    case 'PROCESSING':
    case 'APPROVED':
    case 'SENT':
    case 'RECEIVED':
    default:
      return { label: 'In progress', color: '#055160', bg: '#cff4fc' };
  }
}

export function getStatusGuidance(status: string): {
  title: string;
  whatItMeans: string;
  actions: string[];
  quickQuestions: string[];
} {
  switch (status) {
    case 'RECEIVED':
      return {
        title: 'We received your refund request',
        whatItMeans:
          'Your return/refund request has been received and is waiting in the processing pipeline. This is normal early in the flow.',
        actions: [
          'Check back later for the next status update',
          'Use the assistant to ask for an ETA estimate',
          'Make sure your filing details are complete in your profile'
        ],
        quickQuestions: [
          'When will my refund be available?',
          'What happens after RECEIVED?',
          'Why is my refund still in received status?'
        ]
      };
    case 'PROCESSING':
      return {
        title: 'Your refund is being processed',
        whatItMeans:
          'Your refund is under review/processing. During this stage, timing can vary due to tax-season volume and upstream processing delays.',
        actions: [
          'Check the estimated availability date',
          'Ask the assistant why processing may take longer',
          'Contact support if there is no update after 21 days'
        ],
        quickQuestions: [
          'Why is my refund delayed?',
          'When should I contact support?',
          'What is my estimated refund date?'
        ]
      };
    case 'APPROVED':
      return {
        title: 'Your refund has been approved',
        whatItMeans:
          'Approval means your refund passed processing checks. The next step is disbursement and then availability.',
        actions: [
          'Check for “SENT” and “AVAILABLE” updates',
          'Ask the assistant for estimated availability timing',
          'Keep your bank/deposit details current if applicable'
        ],
        quickQuestions: [
          'How long after approval is refund available?',
          'What does approved mean?',
          'What are the next steps?'
        ]
      };
    case 'SENT':
      return {
        title: 'Your refund has been sent',
        whatItMeans:
          'The refund has been sent to the payment/disbursement channel. Availability may still take a short time depending on the receiving institution.',
        actions: [
          'Check tracking details if available',
          'Refresh status later if not yet marked AVAILABLE',
          'Ask the assistant what “sent” means in plain language'
        ],
        quickQuestions: [
          'Why is it sent but not available yet?',
          'Show tracking details',
          'How long after sent will it be available?'
        ]
      };
    case 'AVAILABLE':
      return {
        title: 'Your refund is available',
        whatItMeans:
          'Your refund is now available. You should be able to access it through the expected payment channel.',
        actions: [
          'Confirm receipt in your payment account',
          'Save a screenshot/export for your records (future enhancement)',
          'Ask the assistant if you have questions about the timeline'
        ],
        quickQuestions: [
          'My refund says available, what should I do next?',
          'Can you summarize my refund timeline?',
          'How long did my refund take?'
        ]
      };
    case 'REJECTED':
      return {
        title: 'Your refund needs attention',
        whatItMeans:
          'The refund request was rejected or could not be completed. Usually this means additional review or a correction is needed.',
        actions: [
          'Contact support for the exact reason',
          'Review your filing details for errors',
          'Ask the assistant what steps to take next'
        ],
        quickQuestions: [
          'Why was my refund rejected?',
          'What should I do next?',
          'Contact support'
        ]
      };
    case 'NOT_FOUND':
      return {
        title: 'No refund status found',
        whatItMeans:
          'We could not find a refund record yet. This can happen if filing is recent or the upstream system has not synced.',
        actions: [
          'Refresh later',
          'Confirm your filing was submitted successfully',
          'Contact support if this persists'
        ],
        quickQuestions: [
          'Why is my refund not found?',
          'When should I check again?',
          'Contact support'
        ]
      };
    default:
      return {
        title: 'Refund status',
        whatItMeans: 'Use the assistant or refresh your refund status for the latest information.',
        actions: ['Refresh status', 'Ask a question'],
        quickQuestions: ['What is my refund status?', 'When will my refund be available?']
      };
  }
}

export function getFreshnessLabel(lastUpdatedAt?: string | null): string {
  if (!lastUpdatedAt) return 'Unknown freshness';
  const diffMs = Date.now() - new Date(lastUpdatedAt).getTime();
  if (Number.isNaN(diffMs)) return 'Unknown freshness';

  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'Updated just now';
  if (mins < 60) return `Updated ${mins} min ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `Updated ${hrs} hr ago`;
  const days = Math.floor(hrs / 24);
  return `Updated ${days} day${days > 1 ? 's' : ''} ago`;
}
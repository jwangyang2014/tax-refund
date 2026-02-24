import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';

vi.mock('../api/refundApi', () => ({
  getLatestRefund: vi.fn(),
  simulateRefundUpdate: vi.fn()
}));

vi.mock('../api/assistantApi', () => ({
  askAssistant: vi.fn()
}));

import { getLatestRefund, simulateRefundUpdate } from '../api/refundApi';
import { askAssistant } from '../api/assistantApi';
import DashboardPage from '../pages/DashboardPage';
import type { RefundStatusResponse } from '../api/types';

const mockGetLatestRefund = vi.mocked(getLatestRefund);
const mockSimulateRefundUpdate = vi.mocked(simulateRefundUpdate);
const mockAskAssistant = vi.mocked(askAssistant);

type RefundStatus = RefundStatusResponse['status'];

function makeRefund(status: RefundStatus, overrides?: Partial<RefundStatusResponse>): RefundStatusResponse {
  return {
    taxYear: 2025,
    status,
    lastUpdatedAt: new Date().toISOString(),
    expectedAmount: 1,
    trackingId: 'T1',
    availableAtEstimated: null,
    aiExplanation: null,
    ...overrides
  };
}

async function expectStatus(status: RefundStatus) {
  await waitFor(() => {
    expect(screen.getByTestId('refund-status')).toHaveTextContent(status);
  });
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('loads and displays refund status', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('PROCESSING'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');
    expect(onError).not.toHaveBeenCalled();
  });

  it('shows usability guidance for current status', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('PROCESSING'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');

    expect(screen.getByText('Refund progress')).toBeInTheDocument();
    expect(screen.getByText('What this status means')).toBeInTheDocument();
    expect(screen.getByText(/Your refund is being processed/i)).toBeInTheDocument();
    expect(screen.getByText(/Recommended actions/i)).toBeInTheDocument();
  });

  it('refresh button calls load again', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    const state = { status: 'RECEIVED' as RefundStatus };

    mockGetLatestRefund.mockImplementation(async () => makeRefund(state.status));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    state.status = 'PROCESSING';

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }));

    await expectStatus('PROCESSING');
    expect(mockGetLatestRefund.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('demo advance calls simulate then reload', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    const state = { status: 'RECEIVED' as RefundStatus };

    mockGetLatestRefund.mockImplementation(async () =>
      makeRefund(state.status, { expectedAmount: 10 })
    );

    mockSimulateRefundUpdate.mockImplementation(async (payload) => {
      state.status = payload.status;
    });

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    fireEvent.click(screen.getByRole('button', { name: 'Demo: Advance Status' }));

    await expectStatus('PROCESSING');

    expect(mockSimulateRefundUpdate).toHaveBeenCalledTimes(1);
    expect(mockSimulateRefundUpdate).toHaveBeenCalledWith({
      taxYear: 2025,
      status: 'PROCESSING',
      expectedAmount: 10,
      trackingId: 'T1'
    });
  });

  it('assistant ask button sends question and renders response/actions/citations', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('PROCESSING'));
    mockAskAssistant.mockResolvedValue({
      answerMarkdown: 'Your refund is processing. ETA is estimated.',
      confidence: 'MEDIUM',
      actions: [
        { type: 'REFRESH', label: 'Refresh status' },
        { type: 'CONTACT_SUPPORT', label: 'Contact support if no update in 21 days' }
      ],
      citations: [{ docId: 'PROCESSING_POLICY', quote: 'Processing may take longer during peak season.' }]
    });

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');

    fireEvent.change(screen.getByLabelText('Assistant question'), {
      target: { value: 'Why is my refund delayed?' }
    });
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }));

    await waitFor(() => {
      expect(mockAskAssistant).toHaveBeenCalledWith('Why is my refund delayed?');
    });

    expect(screen.getByText('Assistant response')).toBeInTheDocument();
    expect(screen.getByText('Suggested actions')).toBeInTheDocument();
    expect(screen.getByText('Citations')).toBeInTheDocument();
    expect(screen.getByText(/Processing may take longer during peak season/i)).toBeInTheDocument();
  });

  it('quick question chip triggers assistant request', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('PROCESSING'));
    mockAskAssistant.mockResolvedValue({
      answerMarkdown: 'Mock answer',
      confidence: 'LOW',
      actions: [],
      citations: []
    });

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');

    fireEvent.click(screen.getByRole('button', { name: /Why is my refund delayed\?/i }));

    await waitFor(() => {
      expect(mockAskAssistant).toHaveBeenCalled();
    });
  });

  it('reports error via onError when load fails', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockRejectedValue(new Error('Failed to load'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith('Failed to load');
    });
  });

  it('reports error via onError when simulate fails', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('RECEIVED', { expectedAmount: 10 }));
    mockSimulateRefundUpdate.mockRejectedValue(new Error('Simulation failed'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    fireEvent.click(screen.getByRole('button', { name: 'Demo: Advance Status' }));

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith('Simulation failed');
    });

    await expectStatus('RECEIVED');
  });

  it('reports error via onError when assistant call fails', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('PROCESSING'));
    mockAskAssistant.mockRejectedValue(new Error('Assistant unavailable'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');

    fireEvent.change(screen.getByLabelText('Assistant question'), {
      target: { value: 'When will my refund be available?' }
    });
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }));

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith('Assistant unavailable');
    });
  });

  it('logout calls onLogout', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    mockGetLatestRefund.mockResolvedValue(makeRefund('RECEIVED'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));

    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});
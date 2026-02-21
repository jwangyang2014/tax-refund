import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';

vi.mock('../api/refundApi', () => ({
  getLatestRefund: vi.fn(),
  simulateRefundUpdate: vi.fn()
}));

import { getLatestRefund, simulateRefundUpdate } from '../api/refundApi';
import DashboardPage from '../pages/DashboardPage';
import type { RefundStatusResponse } from '../api/.types';

const mockGetLatestRefund = vi.mocked(getLatestRefund);
const mockSimulateRefundUpdate = vi.mocked(simulateRefundUpdate);

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

    const state = { status: 'PROCESSING' as RefundStatus };

    mockGetLatestRefund.mockImplementation(async () => makeRefund(state.status));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('PROCESSING');
    expect(onError).not.toHaveBeenCalled();
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

  it('logout calls onLogout', async () => {
    const onLogout = vi.fn();
    const onError = vi.fn();

    const state = { status: 'RECEIVED' as RefundStatus };

    mockGetLatestRefund.mockImplementation(async () => makeRefund(state.status));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));

    expect(onLogout).toHaveBeenCalledTimes(1);
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

    const state = { status: 'RECEIVED' as RefundStatus };

    mockGetLatestRefund.mockImplementation(async () =>
      makeRefund(state.status, { expectedAmount: 10 })
    );

    mockSimulateRefundUpdate.mockRejectedValue(new Error('Simulation failed'));

    render(<DashboardPage onLogout={onLogout} onError={onError} />);

    await expectStatus('RECEIVED');

    fireEvent.click(screen.getByRole('button', { name: 'Demo: Advance Status' }));

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith('Simulation failed');
    });

    await expectStatus('RECEIVED');
  });
});
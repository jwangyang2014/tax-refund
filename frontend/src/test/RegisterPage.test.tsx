import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

vi.mock('../api/authApi', () => ({
  register: vi.fn()
}));

import { register } from '../api/authApi';
import RegisterPage from '../pages/RegisterPage';

const mockRegister = register as unknown as ReturnType<typeof vi.fn>;

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls register and onSuccess', async () => {
    const onSuccess = vi.fn();
    const onBack = vi.fn();
    const onError = vi.fn();

    mockRegister.mockResolvedValueOnce(undefined);

    render(<RegisterPage onSuccess={onSuccess} onBack={onBack} onError={onError} />);

    // Your email input has no placeholder/label, so select it by "first textbox"
    const emailInput = screen.getAllByRole('textbox')[0] as HTMLInputElement;

    fireEvent.change(emailInput, { target: { value: 'a@b.com' } });
    fireEvent.change(screen.getByPlaceholderText('password'), { target: { value: 'Password123!' } });

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => expect(mockRegister).toHaveBeenCalledWith('a@b.com', 'Password123!'));

    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
  });

  it('reports error via onError when register fails', async () => {
    const onSuccess = vi.fn();
    const onBack = vi.fn();
    const onError = vi.fn();

    mockRegister.mockRejectedValueOnce(new Error('Email already registered'));

    render(<RegisterPage onSuccess={onSuccess} onBack={onBack} onError={onError} />);

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => expect(onError).toHaveBeenCalledWith('Email already registered'));

    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('back button calls onBack', () => {
    const onSuccess = vi.fn();
    const onBack = vi.fn();
    const onError = vi.fn();

    render(<RegisterPage onSuccess={onSuccess} onBack={onBack} onError={onError} />);

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
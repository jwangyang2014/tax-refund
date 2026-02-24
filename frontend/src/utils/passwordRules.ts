export type PasswordValidationResult = {
  isValid: boolean;
  errors: string[];
};

export function validatePassword(password: string): PasswordValidationResult {
  const errors: string[] = [];

  if (!password || password.length < 10 || password.length > 72) {
    errors.push('Password length must be 10 to 72 characters');
  }

  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSymbol = /[^A-Za-z0-9]/.test(password);

  if (!hasUpper || !hasLower || !hasDigit || !hasSymbol) {
    errors.push('Password must include upper, lower, digit, and symbol');
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}

export function getPasswordChecklist(password: string) {
  return {
    length: password.length >= 10 && password.length <= 72,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    digit: /\d/.test(password),
    symbol: /[^A-Za-z0-9]/.test(password)
  };
}
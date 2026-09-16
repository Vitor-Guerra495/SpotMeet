/**
 * Strong password regex, aligned with the backend validation.
 * At least 6 characters, with at least one uppercase letter, one lowercase letter and one digit.
 */
export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{6,}$/;

export interface PasswordRulesResult {
  hasMinLength: boolean;
  hasUppercase: boolean;
  hasLowercase: boolean;
  hasNumber: boolean;
  valid: boolean;
}

/**
 * Checks each password security requirement individually for the UI checklist.
 */
export function checkPasswordRules(password: string): PasswordRulesResult {
  const hasMinLength = password.length >= 6;
  const hasUppercase = /[A-Z]/.test(password);
  const hasLowercase = /[a-z]/.test(password);
  const hasNumber = /\d/.test(password);
  const valid = hasMinLength && hasUppercase && hasLowercase && hasNumber;

  return {
    hasMinLength,
    hasUppercase,
    hasLowercase,
    hasNumber,
    valid,
  };
}

/**
 * Simplified e-mail format validation.
 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

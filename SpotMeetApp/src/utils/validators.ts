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

/** E-mail format shared with the backend, limited to the stored field size. */
export const EMAIL_REGEX = /^(?=.{1,255}$)(?=[^@]{1,64}@)[A-Za-z0-9]+([._%+-][A-Za-z0-9]+)*@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/;

/**
 * Prepares an e-mail typed or pasted by the user: removes zero-width characters (invisible,
 * but they make a correct-looking address invalid), outer spaces and capital letters.
 */
export function normalizeEmail(email: string): string {
  return email.replace(/[\u200B-\u200D\u2060\uFEFF]/g, '').trim().toLowerCase();
}

export function isValidEmail(email: string): boolean {
  return EMAIL_REGEX.test(normalizeEmail(email));
}

export const OTP_LENGTH = 6;
export const OTP_REGEX = /^[0-9]{6}$/;

/** Keeps only ASCII digits and limits typed or pasted codes. */
export function normalizeOtp(code: string): string {
  return code.replace(/[^0-9]/g, '').slice(0, OTP_LENGTH);
}

export function isValidOtp(code: string): boolean {
  return code.length === OTP_LENGTH && OTP_REGEX.test(code);
}

export const NAME_MIN_LENGTH = 2;
export const NAME_MAX_LENGTH = 100;

export function normalizeName(name: string): string {
  return name.trim();
}

export function isValidName(name: string): boolean {
  const length = normalizeName(name).length;
  return length >= NAME_MIN_LENGTH && length <= NAME_MAX_LENGTH;
}

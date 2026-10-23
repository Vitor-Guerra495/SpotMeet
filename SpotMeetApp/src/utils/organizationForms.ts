export const ACCESS_KEY_PATTERN = /^#[A-Za-z0-9_-]{2,50}$/;
export const COMMITTEE_NAME_PATTERN = /^[\p{L}\p{M}0-9 _-]{2,50}$/u;

export function normalizeOrganizationName(value: string): string {
  return value.replace(/[\t\n\v\f\r \u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+/g, ' ').trim();
}

export function validateOrganizationName(value: string): true | string {
  const name = normalizeOrganizationName(value);
  return !name ? 'Informe o nome da organização.' : name.length > 255 ? 'Use até 255 caracteres.' : true;
}

export function validateAccessKey(value: string): true | string {
  return ACCESS_KEY_PATTERN.test(normalizeAccessKey(value)) || "Use de 2 a 50 letras, números, '_' ou '-'. O # é automático.";
}

export function normalizeAccessKey(value: string): string {
  const key = value.trim().replace(/^#/, '');
  return key ? `#${key}` : '';
}

export function validateCommitteeName(value: string): true | string {
  const name = normalizeOrganizationName(value);
  return (name.length >= 2 && name.length <= 50 && COMMITTEE_NAME_PATTERN.test(name))
    || "Use de 2 a 50 caracteres: letras, números, espaços, '_' ou '-'.";
}

export function matchesSearch(query: string, ...values: string[]): boolean {
  const normalized = normalizeOrganizationName(query).toLocaleLowerCase();
  return values.some(value => value.toLocaleLowerCase().includes(normalized));
}

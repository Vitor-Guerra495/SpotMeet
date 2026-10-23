export function validateModerationReason(value: string): true | string {
  const reason = value.trim();
  return reason.length >= 3 && reason.length <= 500 && !/[\x00-\x08\x0B-\x1F\x7F-\x9F]/.test(reason)
    ? true : 'Informe um motivo de 3 a 500 caracteres, sem caracteres de controle.';
}

export type FieldName = 'username' | 'email' | 'password' | 'confirm';

// Mirrors the backend's RegisterUserRequest constraints so most mistakes are caught before submitting.
export function validateRegistration(values: Record<FieldName, string>): Partial<Record<FieldName, string>> {
  const errors: Partial<Record<FieldName, string>> = {};
  const username = values.username.trim();
  if (username.length < 3 || username.length > 50) errors.username = 'Use 3–50 characters.';
  else if (!/^[A-Za-z0-9._-]+$/.test(username)) errors.username = "Only letters, digits, '.', '_' and '-'.";
  if (!/^\S+@\S+\.\S+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.';
  if (values.password.length < 8) errors.password = 'Use at least 8 characters.';
  else if (values.password.length > 128) errors.password = 'Use at most 128 characters.';
  if (values.confirm !== values.password) errors.confirm = 'Passwords do not match.';
  return errors;
}

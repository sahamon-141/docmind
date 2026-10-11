import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert } from 'lucide-react';
import { errorMessage, isApiError } from '../api/errors';
import { Button } from '../components/ui/Button';
import { Field, PasswordField } from '../components/ui/Field';
import { useAuth } from '../providers/auth-context';
import { useToast } from '../providers/toast-context';
import { validateRegistration, type FieldName } from '../lib/validation';
import { AuthLayout } from './AuthLayout';

export default function RegisterPage() {
  const { register } = useAuth();
  const toast = useToast();
  const [values, setValues] = useState<Record<FieldName, string>>({ username: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});
  const [formError, setFormError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  const set = (name: FieldName) => (e: React.ChangeEvent<HTMLInputElement>) => setValues((v) => ({ ...v, [name]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validateRegistration(values);
    setErrors(found);
    setFormError(undefined);
    if (Object.keys(found).length) return;

    setSubmitting(true);
    try {
      await register({ username: values.username.trim(), email: values.email.trim(), password: values.password });
      toast.success('Account created. Welcome to DocMind.');
    } catch (err) {
      if (isApiError(err) && err.code === 'USERNAME_TAKEN') setErrors({ username: 'This username is already taken.' });
      else if (isApiError(err) && err.code === 'EMAIL_TAKEN') setErrors({ email: 'An account with this email already exists.' });
      else if (isApiError(err) && err.code === 'VALIDATION_FAILED') setErrors(err.fieldErrors as typeof errors);
      else setFormError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Create your account" subtitle="Upload documents and start asking questions in minutes.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {formError && (
          <p role="alert" className="flex items-start gap-2 rounded-md border border-danger/30 bg-danger-soft px-3 py-2.5 text-sm text-danger">
            <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            {formError}
          </p>
        )}
        <Field label="Username" autoComplete="username" autoFocus value={values.username} onChange={set('username')} error={errors.username} />
        <Field label="Email" type="email" autoComplete="email" value={values.email} onChange={set('email')} error={errors.email} />
        <PasswordField
          label="Password"
          autoComplete="new-password"
          value={values.password}
          onChange={set('password')}
          error={errors.password}
          hint="At least 8 characters."
        />
        <PasswordField label="Confirm password" autoComplete="new-password" value={values.confirm} onChange={set('confirm')} error={errors.confirm} />
        <Button type="submit" loading={submitting} className="mt-2 w-full">
          Create account
        </Button>
      </form>
      <p className="mt-6 text-sm text-ink-soft">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-ink underline underline-offset-4">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}

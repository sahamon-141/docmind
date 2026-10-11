import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert } from 'lucide-react';
import { errorMessage, isApiError } from '../api/errors';
import { Button } from '../components/ui/Button';
import { Field, PasswordField } from '../components/ui/Field';
import { useAuth } from '../providers/auth-context';
import { AuthLayout } from './AuthLayout';

export default function LoginPage() {
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [formError, setFormError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found: typeof errors = {};
    if (!username.trim()) found.username = 'Enter your username or email.';
    if (!password) found.password = 'Enter your password.';
    setErrors(found);
    setFormError(undefined);
    if (Object.keys(found).length) return;

    setSubmitting(true);
    try {
      // on success PublicOnly redirects back to the page that required sign-in
      await login({ username: username.trim(), password });
    } catch (err) {
      setFormError(
        isApiError(err) && err.code === 'INVALID_CREDENTIALS' ? 'Incorrect username or password.' : errorMessage(err),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to continue to your documents.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {formError && (
          <p role="alert" className="flex items-start gap-2 rounded-md border border-danger/30 bg-danger-soft px-3 py-2.5 text-sm text-danger">
            <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            {formError}
          </p>
        )}
        <Field
          label="Username or email"
          autoComplete="username"
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          error={errors.username}
        />
        <PasswordField
          label="Password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <Button type="submit" loading={submitting} className="mt-2 w-full">
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-sm text-ink-soft">
        New to DocMind?{' '}
        <Link to="/register" className="font-medium text-ink underline underline-offset-4">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}

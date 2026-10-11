import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { accountApi } from '../api/endpoints';
import { errorMessage, isApiError } from '../api/errors';
import { ThemeToggle } from '../components/layout/ThemeToggle';
import { Button } from '../components/ui/Button';
import { Field, PasswordField } from '../components/ui/Field';
import { formatDate } from '../lib/format';
import { useAuth } from '../providers/auth-context';
import { useToast } from '../providers/toast-context';

export default function SettingsPage() {
  const { user } = useAuth();
  return (
    <div className="flex-1 overflow-y-auto">
      <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-8 sm:py-10">
        <span className="label-mono">
          Settings <span className="text-muted/60">// 03</span>
        </span>
        <h1 className="mt-2 font-serif text-4xl font-normal tracking-[-0.02em]">
          Account &amp; <em className="font-light text-accent-ink">preferences</em>
        </h1>

        <div className="mt-10 divide-y divide-line border-y border-line">
          <SettingsSection title="Profile" description="Your account details.">
            {user && (
              <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
                <dt className="label-mono self-center">Username</dt>
                <dd className="text-ink">{user.username}</dd>
                <dt className="label-mono self-center">Role</dt>
                <dd className="text-ink">{user.role === 'ADMIN' ? 'Administrator' : 'Member'}</dd>
                <dt className="label-mono self-center">Member since</dt>
                <dd className="text-ink">{formatDate(user.createdAt)}</dd>
              </dl>
            )}
          </SettingsSection>
          <SettingsSection title="Email" description="Used for signing in together with your username.">
            <EmailForm />
          </SettingsSection>
          <SettingsSection title="Password" description="Changing it signs you out on every device.">
            <PasswordForm />
          </SettingsSection>
          <SettingsSection title="Appearance" description="Saved in this browser.">
            <ThemeToggle />
          </SettingsSection>
        </div>
      </div>
    </div>
  );
}

function SettingsSection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="grid gap-4 py-7 md:grid-cols-[14rem_1fr]">
      <div>
        <h2 className="font-serif text-lg font-medium">{title}</h2>
        <p className="mt-1 text-sm text-ink-soft">{description}</p>
      </div>
      <div>{children}</div>
    </section>
  );
}

function EmailForm() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [email, setEmail] = useState(user?.email ?? '');
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(undefined);
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) return setError('Enter a valid email address.');
    setSaving(true);
    try {
      const updated = await accountApi.updateProfile(email.trim());
      setUser(updated);
      setEmail(updated.email);
      toast.success('Email updated');
    } catch (err) {
      setError(isApiError(err) ? (err.fieldErrors.email ?? err.message) : errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex max-w-md flex-col gap-3" noValidate>
      <Field label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error} />
      <div>
        <Button type="submit" variant="secondary" loading={saving} disabled={email.trim() === user?.email}>
          Save email
        </Button>
      </div>
    </form>
  );
}

function PasswordForm() {
  const { signOutLocally } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [errors, setErrors] = useState<{ current?: string; next?: string }>({});
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found: typeof errors = {};
    if (!current) found.current = 'Enter your current password.';
    if (next.length < 8) found.next = 'Use at least 8 characters.';
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    try {
      await accountApi.changePassword(current, next);
      toast.success('Password changed. Please sign in again.');
      signOutLocally();
      navigate('/login', { replace: true });
    } catch (err) {
      if (isApiError(err) && err.code === 'INVALID_CURRENT_PASSWORD') setErrors({ current: err.message });
      else if (isApiError(err) && err.fieldErrors.newPassword) setErrors({ next: err.fieldErrors.newPassword });
      else toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex max-w-md flex-col gap-3" noValidate>
      <PasswordField label="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={errors.current} />
      <PasswordField label="New password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} error={errors.next} hint="At least 8 characters." />
      <div>
        <Button type="submit" variant="secondary" loading={saving}>
          Change password
        </Button>
      </div>
    </form>
  );
}

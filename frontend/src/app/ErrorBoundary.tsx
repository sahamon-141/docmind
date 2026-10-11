import { Link, isRouteErrorResponse, useRouteError } from 'react-router';

/** Router-level error element: catches render errors and unknown routes without blanking the app. */
export function RouteError() {
  const error = useRouteError();
  const notFound = isRouteErrorResponse(error) && error.status === 404;
  if (!notFound) console.error(error);
  return <ErrorScreen notFound={notFound} />;
}

export function ErrorScreen({ notFound = false }: { notFound?: boolean }) {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-4 px-6 text-center">
      <span className="label-mono">{notFound ? 'Error // 404' : 'Error // 500'}</span>
      <h1 className="font-serif text-4xl font-normal tracking-tight">
        {notFound ? 'Page not found.' : <>Something <em className="text-accent-ink">went wrong.</em></>}
      </h1>
      <p className="max-w-md text-sm text-ink-soft">
        {notFound ? 'The page you are looking for does not exist.' : 'An unexpected error occurred. Reloading usually helps.'}
      </p>
      <div className="flex gap-3">
        <Link to="/chat" className="inline-flex h-10 items-center rounded-md bg-ink px-4 text-sm font-medium text-paper hover:opacity-85">
          Back to DocMind
        </Link>
        {!notFound && (
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="inline-flex h-10 items-center rounded-md border border-line-strong px-4 text-sm font-medium"
          >
            Reload
          </button>
        )}
      </div>
    </div>
  );
}

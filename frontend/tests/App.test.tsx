import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../src/app/App';
import { AppProviders } from '../src/app/providers';
import { createTestRouter } from '../src/app/router';

function renderApp(path = '/') {
  const testRouter = createTestRouter([path]);
  render(
    <AppProviders>
      <App router={testRouter} />
    </AppProviders>,
  );
  return testRouter;
}

describe('application foundation', () => {
  it('renders the overview with the real health-check contract', async () => {
    renderApp();

    expect(
      screen.getByRole('heading', { level: 1, name: 'Verification workspace' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Backend available')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/health',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('renders every primary navigation destination', async () => {
    renderApp();
    await screen.findByText('Backend available');
    const navigation = screen.getByRole('navigation', {
      name: 'Primary navigation',
    });

    for (const label of [
      'Overview',
      'Public verification',
      'Credentials',
      'Verification',
      'Predicate verification',
      'Audit logs',
      'Platform operations',
    ]) {
      expect(
        within(navigation).getByRole('link', { name: label }),
      ).toBeInTheDocument();
    }
  });

  it('navigates to a feature route and updates the page content', async () => {
    const user = userEvent.setup();
    renderApp();

    await user.click(screen.getByRole('link', { name: 'Credentials' }));

    expect(
      screen.getByRole('heading', { level: 1, name: 'Credentials' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Credentials workflow is not connected yet'),
    ).toBeInTheDocument();
  });

  it('marks the current navigation destination as active', () => {
    renderApp('/predicate-verification');
    const navigation = screen.getByRole('navigation', {
      name: 'Primary navigation',
    });

    expect(
      within(navigation).getByRole('link', { name: 'Predicate verification' }),
    ).toHaveAttribute('aria-current', 'page');
    expect(
      within(navigation).getByRole('link', { name: 'Overview' }),
    ).not.toHaveAttribute('aria-current');
  });

  it.each([
    ['/verify', 'Public document verification'],
    ['/credentials', 'Credentials'],
    ['/verification', 'Verification'],
    ['/predicate-verification', 'Predicate verification'],
    ['/audit-logs', 'Audit logs'],
    ['/administration', 'Platform operations'],
  ])('renders the direct route %s', async (path, heading) => {
    renderApp(path);

    expect(
      await screen.findByRole('heading', { level: 1, name: heading }),
    ).toBeInTheDocument();
  });

  it('renders a useful not-found route with a path home', () => {
    renderApp('/not-a-real-route');

    expect(
      screen.getByRole('heading', {
        name: 'This path is outside the workspace',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Return to overview' }),
    ).toHaveAttribute('href', '/');
  });

  it('opens and closes the mobile navigation drawer', async () => {
    const user = userEvent.setup();
    renderApp();

    const openButton = screen.getByRole('button', { name: 'Open navigation' });
    await user.click(openButton);
    expect(openButton).toHaveAttribute('aria-expanded', 'true');

    const closeButtons = screen.getAllByRole('button', {
      name: 'Close navigation',
    });
    await waitFor(() => expect(closeButtons[1]).toHaveFocus());
    await user.click(closeButtons[1]);
    expect(openButton).toHaveAttribute('aria-expanded', 'false');
    await waitFor(() => expect(openButton).toHaveFocus());
  });
});

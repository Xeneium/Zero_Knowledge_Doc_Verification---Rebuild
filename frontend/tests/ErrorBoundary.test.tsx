import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from '../src/app/ErrorBoundary';

function BrokenComponent(): never {
  throw new Error('Sensitive internal detail');
}

describe('ErrorBoundary', () => {
  it('replaces a failed render with a safe, understandable message', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <BrokenComponent />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'The interface could not be displayed',
    );
    expect(
      screen.queryByText('Sensitive internal detail'),
    ).not.toBeInTheDocument();
  });
});

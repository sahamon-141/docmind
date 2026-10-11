import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Markdown } from './Markdown';

describe('Markdown citations', () => {
  it('turns markers for retrieved sources into buttons and leaves invented ones as text', async () => {
    const onCite = vi.fn();
    render(<Markdown content="Notice is two weeks [1]. Also see [7]." citationCount={2} onCite={onCite} />);

    const marker = screen.getByRole('button', { name: 'Show source 1' });
    await userEvent.click(marker);
    expect(onCite).toHaveBeenCalledWith(1);

    expect(screen.queryByRole('button', { name: 'Show source 7' })).not.toBeInTheDocument();
    expect(screen.getByText(/Also see \[7\]/)).toBeInTheDocument();
  });

  it('does not render raw HTML from the model', () => {
    const { container } = render(<Markdown content={'<img src=x onerror="alert(1)"> hello'} citationCount={0} />);
    expect(container.querySelector('img')).toBeNull();
  });
});

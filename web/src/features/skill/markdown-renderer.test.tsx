/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MARKDOWN_IMAGE_CLASS_NAME, MarkdownRenderer } from './markdown-renderer'

afterEach(() => cleanup())

describe('MARKDOWN_IMAGE_CLASS_NAME', () => {
  it('keeps markdown images at their intrinsic width while remaining responsive', () => {
    const classNames = MARKDOWN_IMAGE_CLASS_NAME.split(' ')

    expect(classNames).toContain('h-auto')
    expect(classNames).toContain('max-w-full')
    expect(classNames).not.toContain('w-full')
  })
})

describe('MarkdownRenderer links', () => {
  it('passes the raw markdown href to the optional link click handler', () => {
    const onLinkClick = vi.fn()

    render(<MarkdownRenderer content="[Usage](docs/usage.md)" onLinkClick={onLinkClick} />)
    fireEvent.click(screen.getByRole('link', { name: 'Usage' }))

    expect(onLinkClick).toHaveBeenCalledTimes(1)
    expect(onLinkClick.mock.calls[0][0]).toBe('docs/usage.md')
  })

  it('keeps links renderable without a click handler', () => {
    render(<MarkdownRenderer content="[Usage](docs/usage.md)" />)

    expect(screen.getByRole('link', { name: 'Usage' }).getAttribute('href')).toBe('docs/usage.md')
  })
})

describe('MarkdownRenderer model response coverage', () => {
  it('renders the supported GFM response elements and highlighted code', () => {
    const content = [
      '# Model result',
      '',
      '**bold** and `inline`',
      '',
      '- item',
      '- [x] verified',
      '',
      '> quoted result',
      '',
      '| Tool | Status |',
      '| --- | --- |',
      '| MCP | ready |',
      '',
      '```json',
      '{"ok": true}',
      '```',
      '',
      '[details](https://example.com/details)',
    ].join('\n')

    const { container } = render(<MarkdownRenderer content={content} />)

    expect(screen.getByRole('heading', { name: 'Model result' })).toBeTruthy()
    expect(screen.getByText('bold').tagName).toBe('STRONG')
    expect(screen.getByText('inline').tagName).toBe('CODE')
    const taskCheckbox = screen.getByRole('checkbox') as HTMLInputElement
    expect(taskCheckbox.checked).toBe(true)
    expect(taskCheckbox.disabled).toBe(true)
    expect(screen.getByRole('table')).toBeTruthy()
    expect(container.querySelector('blockquote')?.textContent).toContain('quoted result')
    expect(container.querySelector('code.language-json')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'details' }).getAttribute('href')).toBe('https://example.com/details')
  })

  it('does not create executable markup from an untrusted model response', () => {
    const { container } = render(
      <MarkdownRenderer content={'<script>alert("xss")</script>\n\n[unsafe](javascript:alert("xss"))'} />,
    )

    expect(container.querySelector('script')).toBeNull()
    const unsafeAnchor = container.querySelector('a')
    expect(unsafeAnchor?.textContent).toBe('unsafe')
    expect(unsafeAnchor?.hasAttribute('href')).toBe(false)
  })
})

import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import App from './App'

describe('App routing', () => {
  beforeEach(() => localStorage.clear())

  it('redirects unknown paths to / (then to login when signed out)', () => {
    render(
      <MemoryRouter initialEntries={['/no/such/page']}>
        <App />
      </MemoryRouter>,
    )
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument()
  })
})

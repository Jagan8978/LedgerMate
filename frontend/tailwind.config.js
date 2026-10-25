/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Credit = green, Debit = red. Both pass WCAG AA contrast on white.
        credit: { DEFAULT: '#15803d', soft: '#dcfce7' },
        debit: { DEFAULT: '#b91c1c', soft: '#fee2e2' },
        brand: { DEFAULT: '#1e3a5f', light: '#2c5282', soft: '#e8eef6' },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

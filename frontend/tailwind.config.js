/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#111827',
        canvas: '#f5f7fb',
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
        },
      },
      boxShadow: {
        panel: '0 1px 2px rgba(15,23,42,.04), 0 8px 30px rgba(15,23,42,.06)',
      },
    },
  },
  plugins: [],
}


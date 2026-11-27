import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        unbound: {
          bg: "#0C0E10",
          oled: "#000000",
          primary: "#00E5FF",
          primaryContainer: "#004F58",
          onPrimary: "#00363D",
          onPrimaryContainer: "#C3F5FF",
          secondary: "#7DFFA2",
          secondaryContainer: "#0378B7",
          onSecondary: "#003918",
          tertiary: "#83D3E1",
          rose: "#FF5C8A",
          violet: "#8B6CFF",
          amber: "#FFB86C",
          surfaceLowest: "#07080A",
          surfaceLow: "#121518",
          surface: "#171B1F",
          surfaceHigh: "#1F2429",
          surfaceHighest: "#282F36",
          onSurface: "#E2E2E5",
          onSurfaceVariant: "#8DA0A6",
          outline: "#323D44",
          outlineVariant: "#1C252B",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "monospace"],
      },
    },
  },
  plugins: [],
};

export default config;

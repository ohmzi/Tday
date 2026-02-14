import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { SessionProvider } from "next-auth/react";
import { ThemeProvider } from "@/providers/ThemeProvider";
import "@/app/globals.css";


type Props = {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
};

export const metadata: Metadata = {
  title: "Tday",
  description: "Organize your tasks, schedule your day, and plan projects with Tday, a secure and easy-to-use task planner.",
  manifest: "/manifest.json",
  themeColor: "#E77D4E",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Tday",
  },
};

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

export default async function RootLayout({
  children,
  params,
}: Props) {
  const { locale } = await params;
  const messages = await getMessages();
  return (
    <html lang={locale} suppressHydrationWarning>
      <SessionProvider>
        <body className={`${inter.variable} font-sans antialiased`}>
          <ThemeProvider
            attribute="class"
            defaultTheme="system"
            enableSystem
            disableTransitionOnChange
          >
            <NextIntlClientProvider messages={messages}>
              <main>
                {children}
              </main>
            </NextIntlClientProvider>
          </ThemeProvider>
        </body>
      </SessionProvider>
    </html>
  );
}

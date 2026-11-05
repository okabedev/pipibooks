import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "pipibooks",
  description: "넘겨보는 도서 추천, pipibooks",
  openGraph: {
    siteName: "pipibooks",
    locale: "ko_KR",
    title: "pipibooks",
    description: "넘겨보는 도서 추천, pipibooks",
  },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}

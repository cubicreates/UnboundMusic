import type { Metadata } from "next";
import { ApiExplorer } from "@/components/ApiExplorer";

export const metadata: Metadata = {
  title: "API Reference — Unbound Music",
  description: "Simple, developer-friendly API reference for Unbound Music.",
};

export default function ApiDocsPage() {
  return <ApiExplorer />;
}

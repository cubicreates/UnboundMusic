import type { Metadata } from "next";
import { ApiExplorer } from "@/components/ApiExplorer";

export const metadata: Metadata = {
  title: "API Reference — Unbound Music Embedded Go Daemon",
  description:
    "Complete technical API documentation for Unbound Music's embedded Go 1.22+ daemon over Unix Domain Sockets and 127.0.0.1:45731.",
};

export default function ApiPage() {
  return <ApiExplorer />;
}

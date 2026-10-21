import type { NextConfig } from "next";

// The browser only ever talks to this Next server; /api/* is proxied to Spring
// so there is no CORS to configure and the backend URL is a server-side setting.
const API_URL = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_URL}/api/:path*` }];
  },
};

export default nextConfig;

/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    unoptimized: true,
  },
  async redirects() {
    return [
      {
        source: "/api",
        destination: "/api-docs",
        permanent: false,
      },
    ];
  },
};

export default nextConfig;

import posts from "@/content/blog/posts.json";
import { Link } from "@/i18n/navigation";

export default function BlogMainPage() {
  return (
    <div className="min-h-screen">
      <div className="relative mx-auto max-w-3xl px-6 py-20 md:py-32">
        <header className="mb-20">
          <h1
            className="mb-4 font-serif text-5xl font-bold tracking-tight md:text-6xl"
            style={{ fontFamily: "'Libre Baskerville', serif" }}
          >
            {"T'Day Blog"}
          </h1>
          <p className="max-w-2xl text-lg font-light tracking-wide opacity-70">
            Product notes about recurrence behavior, backend architecture, and
            the decisions shaping the Kotlin-first migration.
          </p>
        </header>

        <div className="space-y-0">
          {posts.map((post) => (
            <article
              key={post.slug}
              className="group border-b border-opacity-20 py-10 transition-all duration-300"
            >
              <Link href={`/blogs/page/${post.slug}`} className="block">
                <div className="mb-3 flex items-center gap-3 text-sm">
                  <span className="font-light opacity-60">{post.date}</span>
                  <span className="opacity-30">•</span>
                  <span className="font-light opacity-60">{post.readTime}</span>
                  <span className="opacity-30">•</span>
                  <span className="font-medium text-accent">{post.category}</span>
                </div>

                <h2
                  className="mb-3 font-serif text-2xl font-bold tracking-tight transition-colors duration-300 md:text-3xl"
                  style={{ fontFamily: "'Libre Baskerville', serif" }}
                >
                  {post.title}
                </h2>

                <p className="mb-4 leading-relaxed opacity-70 transition-opacity duration-300 group-hover:opacity-90">
                  {post.excerpt}
                </p>

                <div className="flex items-center gap-2 text-sm font-medium text-accent">
                  <span>Read more</span>
                  <svg
                    className="h-4 w-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M9 5l7 7-7 7"
                    />
                  </svg>
                </div>
              </Link>
            </article>
          ))}
        </div>

        <footer className="mt-24 border-opacity-20 pt-12 text-center">
          <p className="text-sm font-light opacity-60">
            © 2026 Tday. Built with Next.js and a Kotlin-first backend.
          </p>
          <div className="mt-6 flex justify-center gap-6">
            <a
              href="https://github.com/ohmzi/Tday"
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm opacity-60 transition-all duration-300 hover:text-[#2d5a3d] hover:opacity-100"
            >
              GitHub
            </a>
          </div>
        </footer>
      </div>
    </div>
  );
}

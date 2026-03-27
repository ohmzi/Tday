import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { DEFAULT_LOCALE } from "@/i18n";
import Prism from "prismjs";
import "prismjs/components/prism-kotlin";
import "prismjs/components/prism-typescript";
import "prismjs/components/prism-json";

interface BlogPost {
  slug: string;
  title: string;
  excerpt: string;
  date: string;
  readTime: string;
  category: string;
  heroImage: string;
  heroAlt: string;
  contentFile: string;
}

export default function BlogArticlePage() {
  const { slug, locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const navigate = useNavigate();
  const [post, setPost] = useState<BlogPost | null>(null);
  const [html, setHtml] = useState<string>("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const res = await fetch("/content/blog/posts.json");
        const posts: BlogPost[] = await res.json();
        const match = posts.find((p) => p.slug === slug);
        if (!match) {
          navigate(`/${loc}`, { replace: true });
          return;
        }
        if (cancelled) return;
        setPost(match);

        const contentPath = match.contentFile.startsWith("/")
          ? match.contentFile
          : `/${match.contentFile}`;
        const htmlRes = await fetch(contentPath);
        if (!htmlRes.ok) throw new Error("Failed to load article");
        const content = await htmlRes.text();
        if (cancelled) return;
        setHtml(content);
      } catch {
        if (!cancelled) navigate(`/${loc}`, { replace: true });
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [slug, loc, navigate]);

  useEffect(() => {
    if (html) Prism.highlightAll();
  }, [html]);

  if (loading || !post) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="py-10 overflow-y-auto">
      <div className="relative mx-auto max-w-3xl px-6">
        <div className="relative mb-8 h-[100px] w-full overflow-hidden rounded-lg md:h-[150px] lg:h-[200px]">
          <img
            src={post.heroImage}
            alt={post.heroAlt}
            className="h-full w-full object-cover object-bottom"
          />
        </div>

        <article className="space-y-6 text-sm leading-7 text-foreground/85 sm:text-base [&_code]:rounded [&_code]:bg-muted/40 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:font-mono [&_h1]:text-2xl [&_h1]:font-semibold [&_h2]:mt-8 [&_h2]:text-xl [&_h2]:font-semibold [&_ol]:space-y-2 [&_pre_code]:bg-transparent [&_pre_code]:px-0 [&_pre_code]:py-0 [&_ul]:space-y-2">
          <header className="space-y-2">
            <p className="text-xs uppercase tracking-[0.18em] text-accent/80">
              {post.category}
            </p>
            <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
              {post.title}
            </h1>
            <p className="text-sm text-muted-foreground">
              {post.date} &bull; {post.readTime}
            </p>
          </header>

          <div dangerouslySetInnerHTML={{ __html: html }} />
        </article>
      </div>
    </div>
  );
}

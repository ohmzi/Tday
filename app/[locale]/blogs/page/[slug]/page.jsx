import posts from "@/content/blog/posts.json";
import Image from "next/image";
import { notFound } from "next/navigation";
import path from "node:path";
import { readFile } from "node:fs/promises";

export function generateStaticParams() {
  return posts.map((post) => ({ slug: post.slug }));
}

export default async function BlogPostPage({ params }) {
  const { slug } = await params;
  const post = posts.find((entry) => entry.slug === slug);

  if (!post) {
    notFound();
  }

  const html = await readFile(path.join(process.cwd(), post.contentFile), "utf8");

  return (
    <div className="py-10 overflow-y-auto">
      <div className="relative mb-8 h-[100px] w-full overflow-hidden rounded-lg md:h-[150px] lg:h-[200px]">
        <Image
          src={post.heroImage}
          fill
          alt={post.heroAlt}
          priority
          className="object-cover object-bottom"
          sizes="100vw"
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
            {post.date} • {post.readTime}
          </p>
        </header>

        <div dangerouslySetInnerHTML={{ __html: html }} />
      </article>
    </div>
  );
}

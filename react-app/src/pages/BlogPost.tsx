import { useMemo } from "react";
import { Alert, Card, Spinner } from "react-bootstrap";
import { Link, useParams } from "react-router-dom";
import showdown from "showdown";
import DOMPurify from "dompurify";
import { useGetBlogPostQuery } from "../functions/api";

export default function BlogPost() {
  const { slug } = useParams<{ slug: string }>();
  const shouldFetch = (slug || "").trim().length > 0;
  const { data, isLoading, error } = useGetBlogPostQuery(slug || "", {
    skip: !shouldFetch,
  });

  const html = useMemo(() => {
    const md = data?.body_markdown || "";
    if (!md) return "";
    const converter = new showdown.Converter({
      tables: true,
      strikethrough: true,
      openLinksInNewWindow: true,
      simpleLineBreaks: true,
    });
    const rawHtml = converter.makeHtml(md);
    return DOMPurify.sanitize(rawHtml);
  }, [data?.body_markdown]);

  return (
    <div className="p-3">
      <div className="mb-3">
        <Link to="/blog" className="text-decoration-none">
          ← Back to Blog
        </Link>
      </div>

      {isLoading && (
        <div className="d-flex align-items-center gap-2">
          <Spinner animation="border" size="sm" />
          <span>Loading post...</span>
        </div>
      )}

      {!!error && (
        <Alert variant="danger">Failed to load post: {JSON.stringify(error)}</Alert>
      )}

      {!isLoading && !error && data && (
        <Card className="p-3">
          <h2 className="mb-1">{data.title}</h2>
          {data.published_at && (
            <div className="text-muted" style={{ fontSize: 14 }}>
              {new Date(data.published_at).toLocaleDateString()}
              {data.author_username ? ` · ${data.author_username}` : ""}
            </div>
          )}
          {!!data.excerpt && <p className="mt-2">{data.excerpt}</p>}

          <div
            className="mt-3"
            dangerouslySetInnerHTML={{ __html: html }}
          />
        </Card>
      )}
    </div>
  );
}


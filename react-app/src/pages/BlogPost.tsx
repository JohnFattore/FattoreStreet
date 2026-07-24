import { Alert, Card, Spinner } from "react-bootstrap";
import { Link, useParams } from "react-router-dom";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkBreaks from "remark-breaks";
import { useGetBlogPostQuery } from "../functions/api";

export default function BlogPost() {
  const { slug } = useParams<{ slug: string }>();
  const shouldFetch = (slug || "").trim().length > 0;
  const { data, isLoading, error } = useGetBlogPostQuery(slug || "", {
    skip: !shouldFetch,
  });

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
        <Alert variant="danger">
          Failed to load post: {JSON.stringify(error)}
        </Alert>
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

          <div className="mt-3">
            <Markdown
              remarkPlugins={[remarkGfm, remarkBreaks]}
              components={{
                a: ({ href, title, children }) => (
                  <a href={href} title={title} target="_blank" rel="noreferrer">
                    {children}
                  </a>
                ),
              }}
            >
              {data.body_markdown || ""}
            </Markdown>
          </div>
        </Card>
      )}
    </div>
  );
}

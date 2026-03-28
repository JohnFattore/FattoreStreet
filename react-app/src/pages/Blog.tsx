import { Alert, Card, Spinner } from "react-bootstrap";
import { Link } from "react-router-dom";
import { useGetBlogPostsQuery } from "../functions/api";

export default function Blog() {
  const { data, isLoading, error } = useGetBlogPostsQuery();

  return (
    <div className="p-3">
      <h2>Blog</h2>

      {!!error && (
        <Alert variant="danger">Failed to load blog posts: {JSON.stringify(error)}</Alert>
      )}

      {isLoading && (
        <div className="d-flex align-items-center gap-2">
          <Spinner animation="border" size="sm" />
          <span>Loading posts...</span>
        </div>
      )}

      {!isLoading && !error && (data?.results || []).length === 0 && (
        <Alert variant="secondary">No posts yet.</Alert>
      )}

      {(data?.results || []).map((post) => (
        <Card key={post.slug} className="p-3 mb-3">
          <h4 className="mb-1">
            <Link to={`/blog/${post.slug}`} className="text-decoration-none">
              {post.title}
            </Link>
          </h4>
          {post.published_at && (
            <div className="text-muted" style={{ fontSize: 14 }}>
              {new Date(post.published_at).toLocaleDateString()}
              {post.author_username ? ` · ${post.author_username}` : ""}
            </div>
          )}
          {!!post.excerpt && <p className="mt-2 mb-0">{post.excerpt}</p>}
        </Card>
      ))}
    </div>
  );
}


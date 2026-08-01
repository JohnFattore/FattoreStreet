import { Alert, Card, Spinner } from "react-bootstrap";
import { Link } from "react-router-dom";
import { useGetBlogPostsQuery } from "../functions/api";

/**
 * Slug of the category the sync command stamps on every learning topic. The
 * blog index excludes it; the LLM Notebook page asks for only it.
 */
export const LLM_NOTEBOOK_CATEGORY_SLUG = "llm-notebook";

type BlogPostListProps = {
  /** Show only posts in this category */
  category?: string;
  /** Hide posts in this category */
  excludeCategory?: string;
  /** Shown when the query comes back empty */
  emptyMessage?: string;
};

export default function BlogPostList({
  category,
  excludeCategory,
  emptyMessage = "No posts yet.",
}: BlogPostListProps) {
  const { data, isLoading, error } = useGetBlogPostsQuery({
    category,
    exclude_category: excludeCategory,
  });

  const posts = data?.results || [];

  return (
    <>
      {!!error && (
        <Alert variant="danger">
          Failed to load blog posts: {JSON.stringify(error)}
        </Alert>
      )}

      {isLoading && (
        <div className="d-flex align-items-center gap-2">
          <Spinner animation="border" size="sm" />
          <span>Loading posts...</span>
        </div>
      )}

      {!isLoading && !error && posts.length === 0 && (
        <Alert variant="secondary">{emptyMessage}</Alert>
      )}

      {posts.map((post) => (
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
    </>
  );
}

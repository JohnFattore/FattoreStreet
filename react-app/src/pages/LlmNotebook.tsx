import { Link } from "react-router-dom";
import BlogPostList, {
  LLM_NOTEBOOK_CATEGORY_SLUG,
} from "../components/BlogPostList";

export default function LlmNotebook() {
  return (
    <div className="p-3">
      <div className="mb-3">
        <Link to="/blog" className="text-decoration-none">
          ← Back to Blog
        </Link>
      </div>

      <h2>LLM Notebook</h2>

      <p className="text-muted">
        One topic from this codebase a day, researched and written up by Claude.
      </p>

      <BlogPostList
        category={LLM_NOTEBOOK_CATEGORY_SLUG}
        emptyMessage="No learning topics yet."
      />
    </div>
  );
}

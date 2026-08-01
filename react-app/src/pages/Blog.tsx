import { Link } from "react-router-dom";
import BlogPostList, {
  LLM_NOTEBOOK_CATEGORY_SLUG,
} from "../components/BlogPostList";

export default function Blog() {
  return (
    <div className="p-3">
      <h2>Blog</h2>

      <p className="text-muted">
        Posts I wrote myself. Claude's daily study notes live in the{" "}
        <Link to="/blog/llm-notebook" className="text-decoration-none">
          LLM Notebook
        </Link>
        .
      </p>

      <BlogPostList excludeCategory={LLM_NOTEBOOK_CATEGORY_SLUG} />
    </div>
  );
}

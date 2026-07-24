import { useSelector } from "react-redux";
import { RootState } from "../../main";
import { IReview } from "../../interfaces";
import { Alert, Button } from "react-bootstrap";
import { SortableTable } from "../SortableTable";
import {
  useGetReviewsQuery,
  useDeleteReviewMutation,
} from "../../functions/api";

const RATING_CHOICES = [
  { value: 1, label: "1 - Poor" },
  { value: 1.5, label: "1.5 - Subpar" },
  { value: 2, label: "2 - Fair" },
  { value: 2.5, label: "2.5 - Almost Good" },
  { value: 3, label: "3 - Good" },
  { value: 3.5, label: "3.5 - Decent" },
  { value: 4, label: "4 - Very Good" },
  { value: 4.5, label: "4.5 - Excellent-ish" },
  { value: 5, label: "5 - Excellent" },
];

export default function ReviewTable() {
  const { access, username } = useSelector((state: RootState) => state.user);
  const {
    data: reviews = [],
    isLoading,
    error,
  } = useGetReviewsQuery(undefined, { skip: !access });
  const [deleteReview, { error: deleteError }] = useDeleteReviewMutation();

  if (!access) return null;

  if (!isLoading && !error && reviews.length == 0)
    return <Alert>{username.concat(" has no reviews")}</Alert>;

  const columns = [
    { label: "Restaurant", sortKey: "name" },
    {
      label: "Rating",
      sortKey: "rating",
      render: (review: IReview) =>
        RATING_CHOICES.find((rating) => rating.value === review.rating)
          ?.label || "Unknown Rating",
    },
    { label: "Comment", sortKey: "comment" },
    {
      label: "Delete",
      sortKey: "delete",
      sortable: false,
      render: (review: IReview) => (
        <Button
          variant="danger"
          size="sm"
          onClick={() => deleteReview(review.id)}
        >
          Delete
        </Button>
      ),
    },
  ];

  return (
    <>
      <h3>{username.concat("'s restaurant reviews")}</h3>
      <SortableTable
        data={reviews}
        columns={columns}
        isLoading={isLoading}
        errors={[error, deleteError]}
      />
    </>
  );
}

import Table from 'react-bootstrap/Table';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { Alert } from 'react-bootstrap';
import { setReviewSort } from '../reducers/reviewReducer';
import { deleteReview, patchReview } from './axiosFunctions';
import LoginForm from './LoginForm';

const fields = [
    { name: "Restaurant", type: "text", field: "name" },
    { name: "Rating", type: "rating", field: "rating" },
    { name: "Comment", type: "string", field: "comment" },
    { name: "Delete", type: "delete", field: "delete" },
    //{ name: "Update", type: "update", field: "update" },
]

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

function ReviewRow({ review }) {
    const dispatch = useDispatch<AppDispatch>();
    let tableData: JSX.Element[] = [];
    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["type"] == "delete") {
            tableData.push(<td key={i} onClick={() => dispatch(deleteReview(review.id))}>{[fields[i]["field"]]}</td>)
        }
        else if (fields[i]["type"] == "rating") {
            const rating = RATING_CHOICES.find((rating) => rating.value === review.rating)?.label || "Unknown Rating";
            tableData.push(<td key={i}>{rating}</td>)
        }
        else if (fields[i]["type"] == "update") {
            tableData.push(<td key={i} onClick={() => {
                console.log("maxwell")
                const updatedReview = { ...review, rating: 4 }; // Create a copy with updated rating
                dispatch(patchReview(updatedReview))}}>{[fields[i]["field"]]}</td>)
        }
        else {
            tableData.push(<td key={i}>{formatString(review[fields[i]["field"]], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr key={review.id}>
            {tableData}
        </tr>)
}

export default function ReviewTable() {
    const { reviews, loading, sort } = useSelector((state: RootState) => state.reviews);
    const { access, username } = useSelector((state: RootState) => state.user);
    const dispatch = useDispatch<AppDispatch>();

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setReviewSort({ sortColumn, sortDirection }));
    };

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
    }

    if (loading) return <Alert>Loading Reviews</Alert>;

    if (!access) {
        return (
            <>
                <Alert>Login to submit and view reviews</Alert>
                <LoginForm/>
            </>
        )
    }

    if (reviews.length == 0) {
        return (<Alert>{username.concat(" has no reviews")}</Alert>)
    }

    return (
        <>
        <h3>{username.concat("'s restaurant reviews")}</h3>
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {reviews.map((review, index) => (
                        <ReviewRow key={index} review={review} />
                    ))}
                </tbody>
            </Table>
        </>
    );
}
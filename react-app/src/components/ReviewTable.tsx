import Table from 'react-bootstrap/Table';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { Alert } from 'react-bootstrap';
import { setReviewSort } from '../reducers/reviewReducer';
import { deleteReview } from './axiosFunctions';

const fields = [
    { name: "Restaurant", type: "text", field: "name" },
    { name: "Rating", type: "number", field: "rating" },
    { name: "Comment", type: "string", field: "comment" },
    { name: "Delete", type: "delete", field: "delete" },
]

// function is for simple calculations, function2 is for more complex operations
function ReviewRow({ review }) {
    const dispatch = useDispatch<AppDispatch>();
    let tableData: JSX.Element[] = [];
    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["type"] == "delete") {
            tableData.push(<td key={i} onClick={() => dispatch(deleteReview(review.id))}>{[fields[i]["field"]]}</td>)
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
    const { reviews, loading, error, sort } = useSelector((state: RootState) => state.reviews);
    const dispatch = useDispatch<AppDispatch>();

    if (loading) return <Alert>Loading Reviews</Alert>;
    if (error) return <Alert variant="danger">Error: {error}</Alert>;

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

    if (reviews.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }

    return (
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
    );
}
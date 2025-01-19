import ReviewTable from "../components/ReviewTable"
import { useEffect } from 'react';
import { getReviews } from '../components/axiosFunctions';
import { useDispatch } from "react-redux";
import { AppDispatch } from "../main";

export default function Review() {
    const dispatch = useDispatch<AppDispatch>();

    useEffect(() => {
        dispatch(getReviews());
    }, [dispatch]);
    
    return (
        <ReviewTable />
    )
}
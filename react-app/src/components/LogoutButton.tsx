import { Button } from 'react-bootstrap';
import { useDispatch } from "react-redux";
import { AppDispatch } from '../main'
import { logout } from '../reducers/userReducer';

export default function LogoutButton() {
    const dispatch = useDispatch<AppDispatch>();
    return (
        <Button
            onClick={() => {
                dispatch(logout())
            }}>Logout</Button>
    );
}
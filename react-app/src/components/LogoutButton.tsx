import { Button } from "react-bootstrap";
import { useDispatch } from "react-redux";
import { AppDispatch } from "../main";
import { logout } from "../reducers/userReducer";
import { api } from "../functions/api";

export default function LogoutButton() {
  const dispatch = useDispatch<AppDispatch>();
  return (
    <Button
      onClick={() => {
        dispatch(logout());
        dispatch(api.util.resetApiState());
      }}
    >
      Logout
    </Button>
  );
}

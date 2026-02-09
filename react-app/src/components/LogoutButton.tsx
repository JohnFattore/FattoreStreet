import { Button, Modal } from "react-bootstrap";
import { useDispatch } from "react-redux";
import { AppDispatch } from "../main";
import { logout } from "../reducers/userReducer";
import { api } from "../functions/api";
import { useState } from "react";

export default function LogoutButton() {
  const dispatch = useDispatch<AppDispatch>();
  const [showConfirm, setShowConfirm] = useState(false);

  const handleLogout = () => {
    dispatch(logout());
    dispatch(api.util.resetApiState());
    setShowConfirm(false);
  };

  return (
    <>
      <Button variant="outline-danger" onClick={() => setShowConfirm(true)}>
        Logout
      </Button>

      <Modal show={showConfirm} onHide={() => setShowConfirm(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Confirm Logout</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to log out?
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowConfirm(false)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={handleLogout}>
            Logout
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

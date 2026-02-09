import { Modal } from 'react-bootstrap';
import LoginForm from './LoginForm';

interface LoginModalProps {
    show: boolean;
    onHide: () => void;
}

export default function LoginModal({ show, onHide }: LoginModalProps) {
    return (
        <Modal show={show} onHide={onHide} centered backdrop="static">
            <Modal.Header closeButton>
                <Modal.Title>Sign In</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <LoginForm />
            </Modal.Body>
        </Modal>
    );
}
